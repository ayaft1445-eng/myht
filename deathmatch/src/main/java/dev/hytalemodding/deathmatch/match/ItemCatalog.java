package dev.hytalemodding.deathmatch.match;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Откуда брать настоящие идентификаторы предметов.
 *
 * Список предметов лежит в ассетах игры, а не в серверном коде, и угадать
 * имена не выходит: на одной сборке это «iron_sword», на другой что-то своё.
 * Поэтому спрашиваем сам сервер двумя способами.
 *
 * 1. {@link #fromInventory} — идентификаторы того, что уже лежит у игрока.
 *    Способ всегда работает: возьмите предмет в инвентарь и посмотрите имя.
 *
 * 2. {@link #fromAssets} — полный список из хранилища ассетов. Прямого
 *    доступа к нему у мода нет, поэтому идём через рефлексию: у класса Item
 *    есть статический CODEC, а внутри него где-то лежит карта ассетов с
 *    методом getAssetMap(). Способ может не сработать на другой сборке —
 *    тогда остаётся первый.
 */
public final class ItemCatalog {

    /** Насколько глубоко ищем карту ассетов внутри статических полей. */
    private static final int MAX_DEPTH = 4;

    private ItemCatalog() {
    }

    /** Идентификаторы всех предметов в инвентаре игрока — реальные, без догадок. */
    public static List<String> fromInventory(PlayerRef playerRef) {
        Set<String> found = new LinkedHashSet<>();
        try {
            Player player = playerRef == null ? null : playerRef.getComponent(Player.getComponentType());
            Inventory inventory = player == null ? null : player.getInventory();
            if (inventory == null) {
                return new ArrayList<>(found);
            }
            collect(inventory.getHotbar(), found);
            collect(inventory.getArmor(), found);
            collect(inventory.getStorage(), found);
            collect(inventory.getUtility(), found);
            collect(inventory.getTools(), found);
            collect(inventory.getBackpack(), found);
        } catch (RuntimeException | LinkageError ignored) {
            // пустой ответ честнее, чем падение команды
        }
        return new ArrayList<>(found);
    }

    private static void collect(ItemContainer container, Set<String> found) {
        if (container == null) {
            return;
        }
        try {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null || ItemStack.isEmpty(stack)) {
                    continue;
                }
                String id = stack.getItemId();
                if (id != null && !id.isEmpty()) {
                    found.add(id);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // одна непослушная секция не повод бросать остальные
        }
    }

    /** Полный список предметов сервера. Пустой — значит, добраться не вышло. */
    public static List<String> fromAssets() {
        Set<String> found = new LinkedHashSet<>();
        try {
            for (Field field : Item.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                dig(field.get(null), found, 0, new IdentityHashMap<>());
                if (!found.isEmpty()) {
                    break;
                }
            }
        } catch (RuntimeException | LinkageError | IllegalAccessException ignored) {
            // рефлексия — способ запасной, молча уходим
        }
        return new ArrayList<>(found);
    }

    /**
     * Ищет внутри объекта карту ассетов: сначала метод getAssetMap(), потом
     * обычную Map со строковыми ключами и предметами внутри.
     */
    private static void dig(Object target, Set<String> found, int depth, Map<Object, Boolean> seen) {
        if (target == null || depth > MAX_DEPTH || seen.put(target, Boolean.TRUE) != null) {
            return;
        }

        if (harvest(target, found)) {
            return;
        }

        try {
            Method assetMap = findMethod(target, "getAssetMap");
            if (assetMap != null) {
                Object value = assetMap.invoke(target);
                if (harvest(value, found)) {
                    return;
                }
                dig(value, found, depth + 1, seen);
            }
        } catch (RuntimeException | LinkageError | ReflectiveOperationException ignored) {
            // метода нет или он бросил — идём дальше по полям
        }

        for (Field field : target.getClass().getDeclaredFields()) {
            if (field.getType().isPrimitive() || field.getType() == String.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                dig(field.get(target), found, depth + 1, seen);
                if (!found.isEmpty()) {
                    return;
                }
            } catch (RuntimeException | LinkageError | ReflectiveOperationException ignored) {
                // закрытое поле — не беда, полей много
            }
        }
    }

    /** Если это карта или набор предметов — забираем идентификаторы. */
    private static boolean harvest(Object value, Set<String> found) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Item && entry.getKey() instanceof String) {
                    found.add((String) entry.getKey());
                }
            }
            return !found.isEmpty();
        }
        if (value instanceof Collection) {
            for (Object entry : (Collection<?>) value) {
                addItemId(entry, found);
            }
            return !found.isEmpty();
        }
        if (value != null && value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addItemId(Array.get(value, i), found);
            }
            return !found.isEmpty();
        }
        return false;
    }

    private static void addItemId(Object entry, Set<String> found) {
        if (entry instanceof Item) {
            String id = ((Item) entry).getId();
            if (id != null && !id.isEmpty()) {
                found.add(id);
            }
        }
    }

    private static Method findMethod(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }
}
