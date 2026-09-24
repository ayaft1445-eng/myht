package dev.hytalemodding.hubmenu.groupfinder.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Мост к серверному API Hytale.
 *
 * ЗАЧЕМ ЭТОТ КЛАСС.
 * Команды, окна и сообщения плагин вызывает напрямую — эти вызовы уже
 * проверены рабочим кодом мода. А вот телепорт, чтение координат, имя мира
 * и проверка прав в разных сборках сервера называются по-разному, и ошибка
 * в сигнатуре ломает сборку целиком. Поэтому такие вызовы собраны здесь и
 * ищутся через рефлексию: мод собирается всегда, а если какой-то метод не
 * нашёлся — в консоль уходит понятная строка, и остальной мод работает.
 *
 * КАК ЗАМЕНИТЬ НА ПРЯМЫЕ ВЫЗОВЫ.
 * Когда под рукой будет SDK сервера, каждый метод ниже заменяется одной
 * строкой — нужный код приведён в комментарии перед методом. Команда
 * «/gfadmin → ДИАГНОСТИКА» печатает, что именно нашлось на этом сервере.
 */
public final class ServerApi {

    private ServerApi() {
    }

    /** Что удалось найти на этом сервере — для строки диагностики. */
    private static final Map<String, String> REPORT = new LinkedHashMap<>();

    private static final String[] VECTOR3D_CLASSES = {
            "com.hypixel.hytale.math.vector.Vector3d",
            "com.hypixel.hytale.math.Vector3d"
    };

    private static final String[] VECTOR3F_CLASSES = {
            "com.hypixel.hytale.math.vector.Vector3f",
            "com.hypixel.hytale.math.Vector3f"
    };

    private static final String[] TELEPORT_CLASSES = {
            "com.hypixel.hytale.server.core.modules.entity.teleport.Teleport",
            "com.hypixel.hytale.server.core.modules.entity.component.Teleport"
    };

    private static final String[] TRANSFORM_COMPONENT_CLASSES = {
            "com.hypixel.hytale.server.core.modules.entity.component.TransformComponent",
            "com.hypixel.hytale.server.core.modules.entity.TransformComponent",
            "com.hypixel.hytale.component.TransformComponent"
    };

    private static final String[] UNIVERSE_CLASSES = {
            "com.hypixel.hytale.server.core.universe.Universe"
    };

    // ------------------------------------------------------------------ игрок

    /**
     * Ник игрока. Прямой вызов: {@code playerRef.getUsername()}.
     */
    public static String username(Object playerRef) {
        Object value = call(playerRef, new String[] {"getUsername", "getName", "getDisplayName"});
        return value == null ? "?" : String.valueOf(value);
    }

    /**
     * Ключ, по которому игрок узнаётся между заходами.
     * Прямой вызов: {@code playerRef.getUuid()}.
     */
    public static String playerKey(Object playerRef) {
        Object value = call(playerRef, new String[] {"getUuid", "getUUID", "getId"});
        if (value != null) {
            return String.valueOf(value);
        }
        return username(playerRef);
    }

    /**
     * Игрок всё ещё на сервере?
     * Прямой вызов: {@code playerRef.isValid()}.
     * Если метода нет — считаем, что да: очередь всё равно чистится по таймауту.
     */
    public static boolean online(Object playerRef) {
        Object value = call(playerRef, new String[] {"isValid", "isOnline", "isConnected"});
        return !(value instanceof Boolean) || (Boolean) value;
    }

    /**
     * Проверка права. Прямой вызов: {@code player.hasPermission(node)}.
     *
     * @return null, если сервер не дал проверить права — тогда решает список
     *         админов из настроек.
     */
    public static Boolean hasPermission(Object player, String node) {
        if (player == null) {
            return null;
        }
        try {
            Method method = player.getClass().getMethod("hasPermission", String.class);
            Object value = invoke(player, method, node);
            note("hasPermission", "есть");
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable throwable) {
            note("hasPermission", "нет (" + short_(throwable) + ")");
            return null;
        }
    }

    // ------------------------------------------------------------------- мир

    /**
     * Имя мира. Прямой вызов: {@code world.getName()}.
     */
    public static String worldName(Object world) {
        if (world == null) {
            return "";
        }
        Object value = call(world, new String[] {"getName", "getWorldName", "getDisplayName"});
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Мир по имени. Прямой вызов: {@code Universe.get().getWorld(name)}.
     *
     * @return null, если мир не найден или Universe недоступен.
     */
    public static Object findWorld(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Class<?> universeClass = findClass(UNIVERSE_CLASSES);
        if (universeClass == null) {
            note("Universe", "нет");
            return null;
        }
        try {
            Object universe = universeClass.getMethod("get").invoke(null);
            if (universe == null) {
                return null;
            }
            for (String methodName : new String[] {"getWorld", "world", "getWorldByName"}) {
                try {
                    Method method = universe.getClass().getMethod(methodName, String.class);
                    Object world = invoke(universe, method, name);
                    if (world != null) {
                        note("Universe", "есть (" + methodName + ")");
                        return world;
                    }
                } catch (NoSuchMethodException ignored) {
                    // пробуем следующее имя
                }
            }
            // последний шанс: перебрать список миров и сравнить имена
            Object worlds = call(universe, new String[] {"getWorlds", "worlds"});
            if (worlds instanceof Iterable) {
                for (Object world : (Iterable<?>) worlds) {
                    if (name.equalsIgnoreCase(worldName(world))) {
                        note("Universe", "есть (перебор миров)");
                        return world;
                    }
                }
            }
        } catch (Throwable throwable) {
            note("Universe", "ошибка: " + short_(throwable));
        }
        return null;
    }

    /**
     * Пишет сообщение всем в мире.
     * Прямой вызов: {@code world.sendMessage(message)}.
     *
     * @return true, если сообщение ушло.
     */
    public static boolean broadcast(Object world, Object message) {
        if (world == null || message == null) {
            return false;
        }
        for (Method method : world.getClass().getMethods()) {
            if (method.getName().equals("sendMessage")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(message)) {
                try {
                    invoke(world, method, message);
                    note("world.sendMessage", "есть");
                    return true;
                } catch (Throwable throwable) {
                    note("world.sendMessage", "ошибка: " + short_(throwable));
                    return false;
                }
            }
        }
        note("world.sendMessage", "нет");
        return false;
    }

    /**
     * Выполняет задачу в потоке мира — так безопаснее менять состояние мира.
     * Прямой вызов: {@code world.execute(task)}.
     *
     * @return true, если задача отдана миру; false — вызывающий код должен
     *         выполнить её сам.
     */
    public static boolean runOnWorldThread(Object world, Runnable task) {
        if (world == null) {
            return false;
        }
        try {
            Method method = world.getClass().getMethod("execute", Runnable.class);
            invoke(world, method, task);
            note("world.execute", "есть");
            return true;
        } catch (Throwable throwable) {
            note("world.execute", "нет (" + short_(throwable) + ")");
            return false;
        }
    }

    // --------------------------------------------------------------- позиция

    /**
     * Координаты и поворот сущности: {x, y, z, yaw, pitch}.
     * Прямой вызов: {@code store.getComponent(ref, TransformComponent.getComponentType()).getPosition()}.
     *
     * @return null, если позицию прочитать не удалось.
     */
    public static double[] position(Object store, Object ref) {
        Object transform = transformComponent(store, ref);
        if (transform == null) {
            return null;
        }
        Object position = call(transform, new String[] {"getPosition", "position"});
        Object rotation = call(transform, new String[] {"getRotation", "rotation"});
        if (position == null) {
            return null;
        }
        double x = vectorPart(position, "getX", "x", 0);
        double y = vectorPart(position, "getY", "y", 0);
        double z = vectorPart(position, "getZ", "z", 0);
        double yaw = rotation == null ? 0 : vectorPart(rotation, "getYaw", "y", 0);
        double pitch = rotation == null ? 0 : vectorPart(rotation, "getPitch", "x", 0);
        return new double[] {x, y, z, yaw, pitch};
    }

    // -------------------------------------------------------------- телепорт

    /**
     * Переносит игрока в точку. Возвращает null при успехе или текст ошибки.
     *
     * Прямой вызов (когда под рукой SDK):
     * <pre>
     * store.addComponent(ref, Teleport.getComponentType(),
     *         new Teleport(targetWorld, new Vector3d(x, y, z), new Vector3f(pitch, yaw, 0)));
     * </pre>
     *
     * Здесь по очереди пробуются три пути: компонент Teleport, метод
     * {@code player.moveTo(...)} и прямая запись позиции в TransformComponent.
     */
    public static String teleport(
            Object store,
            Object ref,
            Object player,
            Object currentWorld,
            Object targetWorld,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        Object world = targetWorld == null || targetWorld == currentWorld ? null : targetWorld;

        String viaComponent = teleportViaComponent(store, ref, world, x, y, z, yaw, pitch);
        if (viaComponent == null) {
            note("телепорт", "компонент Teleport");
            return null;
        }

        // Запасные пути двигают игрока внутри текущего мира. Если нужен другой
        // мир — ими пользоваться нельзя: игрок окажется не там, где ждут.
        if (world != null) {
            note("телепорт", "между мирами не вышло");
            return "перенос в другой мир недоступен (" + viaComponent + ")";
        }

        String viaMoveTo = teleportViaMoveTo(player, store, ref, x, y, z);
        if (viaMoveTo == null) {
            note("телепорт", "player.moveTo");
            return null;
        }

        String viaTransform = teleportViaTransform(store, ref, x, y, z, yaw, pitch);
        if (viaTransform == null) {
            note("телепорт", "TransformComponent");
            return null;
        }

        note("телепорт", "не найден способ");
        return "Teleport: " + viaComponent + "; moveTo: " + viaMoveTo + "; Transform: " + viaTransform;
    }

    private static String teleportViaComponent(
            Object store, Object ref, Object world, double x, double y, double z, float yaw, float pitch) {
        Class<?> teleportClass = findClass(TELEPORT_CLASSES);
        if (teleportClass == null) {
            return "класс Teleport не найден";
        }
        try {
            Object position = newVector3d(x, y, z);
            Object rotation = newVector3f(pitch, yaw, 0.0f);
            if (position == null || rotation == null) {
                return "классы векторов не найдены";
            }

            Object teleport = null;
            for (Constructor<?> constructor : teleportClass.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (world != null
                        && types.length == 3
                        && types[0].isInstance(world)
                        && types[1].isInstance(position)
                        && types[2].isInstance(rotation)) {
                    teleport = constructor.newInstance(world, position, rotation);
                    break;
                }
                if (world == null
                        && types.length == 2
                        && types[0].isInstance(position)
                        && types[1].isInstance(rotation)) {
                    teleport = constructor.newInstance(position, rotation);
                    break;
                }
            }
            if (teleport == null) {
                return "подходящий конструктор не найден";
            }

            Object componentType = teleportClass.getMethod("getComponentType").invoke(null);
            if (componentType == null) {
                return "getComponentType вернул null";
            }
            if (!addComponent(store, ref, componentType, teleport)) {
                return "store.addComponent недоступен";
            }
            return null;
        } catch (Throwable throwable) {
            return short_(throwable);
        }
    }

    private static String teleportViaMoveTo(Object player, Object store, Object ref, double x, double y, double z) {
        if (player == null) {
            return "игрок не передан";
        }
        try {
            for (Method method : player.getClass().getMethods()) {
                if (!method.getName().equals("moveTo")) {
                    continue;
                }
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 5
                        && types[0].isInstance(ref)
                        && types[1] == double.class
                        && types[4].isInstance(store)) {
                    invoke(player, method, ref, x, y, z, store);
                    return null;
                }
                if (types.length == 4 && types[0].isInstance(ref) && types[1] == double.class) {
                    invoke(player, method, ref, x, y, z);
                    return null;
                }
            }
            return "метод moveTo не найден";
        } catch (Throwable throwable) {
            return short_(throwable);
        }
    }

    private static String teleportViaTransform(
            Object store, Object ref, double x, double y, double z, float yaw, float pitch) {
        Object transform = transformComponent(store, ref);
        if (transform == null) {
            return "TransformComponent не найден";
        }
        try {
            Object position = newVector3d(x, y, z);
            Object rotation = newVector3f(pitch, yaw, 0.0f);
            if (position == null) {
                return "класс Vector3d не найден";
            }
            boolean moved = invokeWith(transform, new String[] {"teleportPosition", "setPosition"}, position);
            if (!moved) {
                return "метод установки позиции не найден";
            }
            if (rotation != null) {
                invokeWith(transform, new String[] {"teleportRotation", "setRotation"}, rotation);
            }
            return null;
        } catch (Throwable throwable) {
            return short_(throwable);
        }
    }

    // ------------------------------------------------------------- настройки

    /**
     * Папка плагина для файла настроек.
     * Прямой вызов: {@code plugin.getDataDirectory()}.
     */
    public static Path dataDirectory(Object plugin, String fallbackFolder) {
        Object value = call(plugin, new String[] {"getDataDirectory", "getDataFolder", "getDataPath"});
        if (value instanceof Path) {
            return (Path) value;
        }
        if (value instanceof java.io.File) {
            return ((java.io.File) value).toPath();
        }
        note("папка плагина", "не найдена, берём ./" + fallbackFolder);
        return Paths.get(fallbackFolder);
    }

    // ---------------------------------------------------------- диагностика

    /** Строки вида «телепорт: компонент Teleport» — что нашлось на сервере. */
    public static List<String> diagnostics() {
        List<String> lines = new ArrayList<>();
        synchronized (REPORT) {
            if (REPORT.isEmpty()) {
                lines.add("пока ничего не вызывалось");
            }
            for (Map.Entry<String, String> entry : REPORT.entrySet()) {
                lines.add(entry.getKey() + ": " + entry.getValue());
            }
        }
        return lines;
    }

    private static void note(String key, String value) {
        synchronized (REPORT) {
            REPORT.put(key, value);
        }
    }

    // --------------------------------------------------------- мелкие помощники

    private static Object transformComponent(Object store, Object ref) {
        Class<?> transformClass = findClass(TRANSFORM_COMPONENT_CLASSES);
        if (transformClass == null) {
            note("TransformComponent", "класс не найден");
            return null;
        }
        try {
            Object componentType = transformClass.getMethod("getComponentType").invoke(null);
            Object component = getComponent(store, ref, componentType);
            note("TransformComponent", component == null ? "компонента нет у игрока" : "есть");
            return component;
        } catch (Throwable throwable) {
            note("TransformComponent", "ошибка: " + short_(throwable));
            return null;
        }
    }

    private static Object getComponent(Object store, Object ref, Object componentType) {
        if (store == null || componentType == null) {
            return null;
        }
        for (Method method : store.getClass().getMethods()) {
            if (!method.getName().equals("getComponent")) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 2 && types[0].isInstance(ref) && types[1].isInstance(componentType)) {
                try {
                    return invoke(store, method, ref, componentType);
                } catch (Throwable throwable) {
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean addComponent(Object store, Object ref, Object componentType, Object component) {
        if (store == null) {
            return false;
        }
        for (Method method : store.getClass().getMethods()) {
            if (!method.getName().equals("addComponent")) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[0].isInstance(ref) && types[1].isInstance(componentType)) {
                try {
                    invoke(store, method, ref, componentType, component);
                    return true;
                } catch (Throwable throwable) {
                    return false;
                }
            }
        }
        return false;
    }

    private static Object newVector3d(double x, double y, double z) {
        Class<?> vectorClass = findClass(VECTOR3D_CLASSES);
        if (vectorClass == null) {
            return null;
        }
        try {
            return vectorClass.getConstructor(double.class, double.class, double.class).newInstance(x, y, z);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Object newVector3f(float x, float y, float z) {
        Class<?> vectorClass = findClass(VECTOR3F_CLASSES);
        if (vectorClass == null) {
            return null;
        }
        try {
            return vectorClass.getConstructor(float.class, float.class, float.class).newInstance(x, y, z);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static double vectorPart(Object vector, String getter, String field, double fallback) {
        Object value = call(vector, new String[] {getter});
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            Field declared = vector.getClass().getField(field);
            Object raw = declared.get(vector);
            if (raw instanceof Number) {
                return ((Number) raw).doubleValue();
            }
        } catch (Throwable ignored) {
            // вернём значение по умолчанию
        }
        return fallback;
    }

    private static Object call(Object target, String[] methodNames) {
        if (target == null) {
            return null;
        }
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                return invoke(target, method);
            } catch (Throwable ignored) {
                // пробуем следующее имя
            }
        }
        return null;
    }

    private static boolean invokeWith(Object target, String[] methodNames, Object argument) {
        for (String name : methodNames) {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(argument)) {
                    try {
                        invoke(target, method, argument);
                        return true;
                    } catch (Throwable ignored) {
                        // пробуем следующий метод
                    }
                }
            }
        }
        return false;
    }

    /**
     * Вызывает метод, обходя закрытые классы.
     *
     * Метод может быть объявлен публичным, но в непубличном классе — тогда
     * обычный invoke бросает IllegalAccessException. В этом случае ищем то же
     * объявление в открытом предке: виртуальный вызов всё равно попадёт в
     * нужную реализацию.
     */
    private static Object invoke(Object target, Method method, Object... arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException blocked) {
            Method open = openDeclaration(target.getClass(), method.getName(), method.getParameterTypes());
            if (open != null) {
                return open.invoke(target, arguments);
            }
            method.setAccessible(true);
            return method.invoke(target, arguments);
        }
    }

    /** Ищет объявление метода в публичном классе или интерфейсе выше по иерархии. */
    private static Method openDeclaration(Class<?> type, String name, Class<?>[] parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (Modifier.isPublic(current.getModifiers())) {
                try {
                    return current.getDeclaredMethod(name, parameterTypes);
                } catch (NoSuchMethodException ignored) {
                    // смотрим выше
                }
            }
            for (Class<?> anInterface : current.getInterfaces()) {
                if (Modifier.isPublic(anInterface.getModifiers())) {
                    try {
                        return anInterface.getDeclaredMethod(name, parameterTypes);
                    } catch (NoSuchMethodException ignored) {
                        // смотрим дальше
                    }
                }
            }
        }
        return null;
    }

    private static Class<?> findClass(String[] candidates) {
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                // пробуем следующее имя
            }
        }
        return null;
    }

    private static String short_(Throwable throwable) {
        String message = throwable.getMessage();
        String name = throwable.getClass().getSimpleName();
        return message == null || message.isEmpty() ? name : name + ": " + message;
    }
}
