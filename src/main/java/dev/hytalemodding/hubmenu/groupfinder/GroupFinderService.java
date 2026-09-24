package dev.hytalemodding.hubmenu.groupfinder;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;
import dev.hytalemodding.hubmenu.groupfinder.model.ArenaPoint;
import dev.hytalemodding.hubmenu.groupfinder.model.GameModeConfig;
import dev.hytalemodding.hubmenu.groupfinder.model.GroupFinderConfig;
import dev.hytalemodding.hubmenu.groupfinder.queue.QueueEntry;
import dev.hytalemodding.hubmenu.groupfinder.storage.ConfigStore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Поиск группы: очереди по режимам, отсчёт и перенос собранной группы.
 *
 * Как это работает. Игрок встаёт в очередь — командой или кнопкой в меню.
 * Раз в секунду служба смотрит на каждую очередь: набралось ли минимум
 * игроков. Набралось — идёт отсчёт, по нулю первые N игроков уходят на
 * арену режима, а очередь освобождается для следующей группы.
 *
 * Состояние меняется только под замком этого объекта, потому что дёргают
 * его из разных потоков: команды, окна и собственный таймер.
 */
public class GroupFinderService {

    /** Право, которое пускает в панель настроек. */
    public static final String ADMIN_PERMISSION = "hubmenu.groupfinder.admin";

    private static final String PREFIX = "[Поиск] ";
    private static final long TICK_SECONDS = 1L;

    private final HytaleLogger logger;
    private final ConfigStore store;

    private GroupFinderConfig config;
    /** Очереди по ключу режима. */
    private final Map<String, ModeQueue> queues = new LinkedHashMap<>();
    private ScheduledExecutorService ticker;

    public GroupFinderService(HytaleLogger logger, ConfigStore store) {
        this.logger = logger;
        this.store = store;
        this.config = store.load();
    }

    // ------------------------------------------------------------------ запуск

    /** Поднимает секундный таймер. Вызывается один раз при старте плагина. */
    public synchronized void start() {
        if (this.ticker != null) {
            return;
        }
        this.ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "HubMenu-GroupFinder");
            thread.setDaemon(true);
            return thread;
        });
        this.ticker.scheduleAtFixedRate(this::safeTick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);

        if (this.config.isSetupMode()) {
            log(Level.WARNING, "список админов пуст — панель /gfadmin пока открыта всем. "
                    + "Откройте её и нажмите «СДЕЛАТЬ СЕБЯ АДМИНОМ».");
        }
        if (!this.store.getLastError().isEmpty()) {
            log(Level.WARNING, this.store.getLastError());
        }
        log(Level.INFO, "поиск группы готов, режимов в настройках: " + this.config.getModes().size());
    }

    /** Гасит таймер и распускает очереди. Вызывается при выключении плагина. */
    public synchronized void stop() {
        if (this.ticker != null) {
            this.ticker.shutdownNow();
            this.ticker = null;
        }
        for (ModeQueue queue : this.queues.values()) {
            for (QueueEntry entry : queue.players) {
                tell(entry, "сервер выключает поиск группы.");
            }
            queue.players.clear();
            queue.countdown = -1;
        }
    }

    // ---------------------------------------------------------------- настройки

    public synchronized GroupFinderConfig config() {
        return this.config;
    }

    public synchronized boolean saveConfig() {
        boolean saved = this.store.save(this.config);
        if (!saved) {
            log(Level.WARNING, this.store.getLastError());
        }
        return saved;
    }

    /** Перечитывает файл с диска — на случай правок руками. */
    public synchronized void reloadConfig() {
        this.config = this.store.load();
        dropQueuesOfMissingModes();
        if (!this.store.getLastError().isEmpty()) {
            log(Level.WARNING, this.store.getLastError());
        }
    }

    public ConfigStore store() {
        return this.store;
    }

    /**
     * Пускать ли игрока в панель настроек.
     *
     * Сначала смотрим список админов, потом право сервера, и только если
     * список пуст — пускаем всех: иначе владелец сервера не смог бы добавить
     * сам себя. Об этом режиме мод громко пишет в консоль и в саму панель.
     */
    public synchronized boolean isAdmin(Object player, String username) {
        if (this.config.isListedAdmin(username)) {
            return true;
        }
        if (Boolean.TRUE.equals(ServerApi.hasPermission(player, ADMIN_PERMISSION))) {
            return true;
        }
        return this.config.isSetupMode();
    }

    // ------------------------------------------------------------------ очередь

    /** Чем закончилась попытка встать в очередь. */
    public enum JoinStatus {
        OK,
        ALREADY_HERE,
        SWITCHED,
        SYSTEM_OFF,
        MODE_OFF,
        NO_ARENA,
        UNKNOWN_MODE
    }

    /** Результат попытки встать в очередь: статус и готовый текст для игрока. */
    public static class JoinResult {

        private final JoinStatus status;
        private final String message;

        JoinResult(JoinStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public JoinStatus getStatus() {
            return this.status;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean isQueued() {
            return this.status == JoinStatus.OK
                    || this.status == JoinStatus.SWITCHED
                    || this.status == JoinStatus.ALREADY_HERE;
        }
    }

    public synchronized JoinResult join(
            String modeId,
            PlayerRef playerRef,
            Ref<EntityStore> ref,
            Store<EntityStore> entityStore,
            World world
    ) {
        if (!this.config.isEnabled()) {
            return new JoinResult(JoinStatus.SYSTEM_OFF, "поиск группы сейчас выключен.");
        }
        GameModeConfig mode = this.config.findMode(modeId);
        if (mode == null) {
            return new JoinResult(JoinStatus.UNKNOWN_MODE, "такого режима нет.");
        }
        if (!mode.isEnabled()) {
            return new JoinResult(JoinStatus.MODE_OFF, "режим «" + mode.getName() + "» выключен.");
        }
        if (!mode.getArena().isSet()) {
            return new JoinResult(JoinStatus.NO_ARENA,
                    "у режима «" + mode.getName() + "» не задана точка телепорта — скажите админу.");
        }

        String key = ServerApi.playerKey(playerRef);
        String current = queuedModeId(key);
        if (mode.getId().equals(current)) {
            return new JoinResult(JoinStatus.ALREADY_HERE,
                    "вы уже ищете игру: " + mode.getName() + ".");
        }

        boolean switched = current != null;
        if (switched) {
            removeFromQueues(key);
        }

        ModeQueue queue = queue(mode.getId());
        queue.players.add(new QueueEntry(playerRef, ref, entityStore, world));

        String text = "ищем игру: " + mode.getName()
                + " · " + queue.players.size() + "/" + mode.getMinPlayers();
        if (switched) {
            return new JoinResult(JoinStatus.SWITCHED, text + " (прошлая очередь отменена)");
        }
        // Зовём остальных только на первом игроке в очереди: иначе каждый
        // зашедший сыпал бы в общий чат одну и ту же строку.
        if (this.config.isAnnounce() && queue.players.size() == 1) {
            announce(world, "идёт набор в «" + mode.getName() + "» — нужно "
                    + mode.getMinPlayers() + ". Встать в очередь: /gf");
        }
        return new JoinResult(JoinStatus.OK, text);
    }

    /** Убирает игрока из очереди. true — он там был. */
    public synchronized boolean leave(String playerKey) {
        return removeFromQueues(playerKey);
    }

    /** Ключ режима, в очереди которого стоит игрок, или null. */
    public synchronized String queuedModeId(String playerKey) {
        for (Map.Entry<String, ModeQueue> entry : this.queues.entrySet()) {
            for (QueueEntry player : entry.getValue().players) {
                if (player.getKey().equals(playerKey)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public synchronized int queueSize(String modeId) {
        ModeQueue queue = this.queues.get(modeId);
        return queue == null ? 0 : queue.players.size();
    }

    /** Сколько секунд осталось до переноса, или -1 если отсчёт не идёт. */
    public synchronized int countdownLeft(String modeId) {
        ModeQueue queue = this.queues.get(modeId);
        return queue == null ? -1 : queue.countdown;
    }

    public synchronized void clearQueue(String modeId) {
        ModeQueue queue = this.queues.get(modeId);
        if (queue == null) {
            return;
        }
        for (QueueEntry entry : queue.players) {
            tell(entry, "очередь очищена админом.");
        }
        queue.players.clear();
        queue.countdown = -1;
    }

    /** Строка состояния для игрока: что он ищет и сколько уже ждёт. */
    public synchronized String statusLine(String playerKey) {
        String modeId = queuedModeId(playerKey);
        if (modeId == null) {
            return "вы не в очереди.";
        }
        GameModeConfig mode = this.config.findMode(modeId);
        ModeQueue queue = queue(modeId);
        String name = mode == null ? modeId : mode.getName();
        int need = mode == null ? 0 : mode.getMinPlayers();
        StringBuilder line = new StringBuilder();
        line.append(name).append(" · ").append(queue.players.size()).append("/").append(need);
        if (queue.countdown >= 0) {
            line.append(" · старт через ").append(queue.countdown).append(" с");
        }
        for (QueueEntry entry : queue.players) {
            if (entry.getKey().equals(playerKey)) {
                line.append(" · ждёте ").append(entry.waitedSeconds()).append(" с");
                break;
            }
        }
        return line.toString();
    }

    // --------------------------------------------------------------------- тик

    private void safeTick() {
        try {
            tick();
        } catch (Throwable throwable) {
            log(Level.SEVERE, "сбой в такте поиска группы: " + throwable);
        }
    }

    private void tick() {
        List<Runnable> jobs = new ArrayList<>();
        synchronized (this) {
            dropQueuesOfMissingModes();
            for (GameModeConfig mode : this.config.getModes()) {
                ModeQueue queue = this.queues.get(mode.getId());
                if (queue == null || queue.players.isEmpty()) {
                    continue;
                }
                dropLostPlayers(queue);
                if (queue.players.isEmpty()) {
                    queue.countdown = -1;
                    continue;
                }
                if (!this.config.isEnabled() || !mode.isReady()) {
                    for (QueueEntry entry : queue.players) {
                        tell(entry, "поиск в «" + mode.getName() + "» остановлен: "
                                + (this.config.isEnabled() ? mode.readyProblem() : "система выключена") + ".");
                    }
                    queue.players.clear();
                    queue.countdown = -1;
                    continue;
                }
                stepCountdown(mode, queue, jobs);
            }
        }
        for (Runnable job : jobs) {
            job.run();
        }
    }

    /** Двигает отсчёт режима на секунду и, если пора, собирает группу. */
    private void stepCountdown(GameModeConfig mode, ModeQueue queue, List<Runnable> jobs) {
        if (queue.players.size() < mode.getMinPlayers()) {
            if (queue.countdown >= 0) {
                queue.countdown = -1;
                broadcastQueue(queue, "кто-то вышел, отсчёт отменён. Ждём игроков.");
            }
            return;
        }

        if (queue.countdown < 0) {
            queue.countdown = mode.getCountdownSeconds();
            broadcastQueue(queue, queue.countdown > 0
                    ? "группа собрана! Старт через " + queue.countdown + " с."
                    : "группа собрана!");
        } else {
            queue.countdown--;
            if (queue.countdown > 0 && (queue.countdown <= 5 || queue.countdown % 10 == 0)) {
                broadcastQueue(queue, "старт через " + queue.countdown + " с.");
            }
        }

        if (queue.countdown <= 0) {
            startMatch(mode, queue, jobs);
        }
    }

    /** Забирает из очереди готовую группу и ставит задачи на перенос. */
    private void startMatch(GameModeConfig mode, ModeQueue queue, List<Runnable> jobs) {
        int take = Math.min(queue.players.size(), mode.getMaxPlayers());
        List<QueueEntry> group = new ArrayList<>(queue.players.subList(0, take));
        queue.players.subList(0, take).clear();
        queue.countdown = -1;

        ArenaPoint arena = mode.getArena();
        Object targetWorld = resolveWorld(arena, group);

        for (int i = 0; i < group.size(); i++) {
            QueueEntry entry = group.get(i);
            double[] spot = spread(arena, i, group.size(), mode.getSpreadRadius());
            jobs.add(() -> teleport(mode, entry, targetWorld, spot));
        }

        StringBuilder names = new StringBuilder();
        for (QueueEntry entry : group) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(entry.getUsername());
        }
        log(Level.INFO, "группа собрана · " + mode.getName() + " · " + names);

        if (this.config.isAnnounce() && !group.isEmpty()) {
            announce(group.get(0).getWorld(),
                    "группа для «" + mode.getName() + "» собрана: " + names + ".");
        }
    }

    /**
     * Ищет мир арены. null значит «тот же мир, где стоит игрок» — так
     * настроено, когда имя мира в точке пустое.
     */
    private Object resolveWorld(ArenaPoint arena, List<QueueEntry> group) {
        String wanted = arena.getWorld();
        if (wanted == null || wanted.isEmpty() || group.isEmpty()) {
            return null;
        }
        String here = ServerApi.worldName(group.get(0).getWorld());
        if (wanted.equalsIgnoreCase(here)) {
            return null;
        }
        Object world = ServerApi.findWorld(wanted);
        if (world == null) {
            log(Level.WARNING, "мир «" + wanted + "» не найден, переносим в текущий мир");
        }
        return world;
    }

    /** Раскидывает игроков по кругу вокруг точки, если задан разброс. */
    private static double[] spread(ArenaPoint arena, int index, int count, int radius) {
        if (radius <= 0 || count <= 1) {
            return new double[] {arena.getX(), arena.getY(), arena.getZ()};
        }
        double angle = 2.0 * Math.PI * index / count;
        return new double[] {
                arena.getX() + Math.cos(angle) * radius,
                arena.getY(),
                arena.getZ() + Math.sin(angle) * radius
        };
    }

    /** Переносит одного игрока. Выполняется в потоке мира, если сервер даёт. */
    private void teleport(GameModeConfig mode, QueueEntry entry, Object targetWorld, double[] spot) {
        Runnable task = () -> {
            if (!entry.isOnline() || entry.getStore() == null || entry.getRef() == null) {
                log(Level.WARNING, "не переносим " + entry.getUsername() + ": игрока больше нет на сервере");
                return;
            }
            ArenaPoint arena = mode.getArena();
            Player player = entry.getStore().getComponent(entry.getRef(), Player.getComponentType());
            String error = ServerApi.teleport(
                    entry.getStore(),
                    entry.getRef(),
                    player,
                    entry.getWorld(),
                    targetWorld,
                    spot[0],
                    spot[1],
                    spot[2],
                    arena.getYaw(),
                    arena.getPitch()
            );
            if (error == null) {
                tell(entry, "группа собрана — переносим на «" + mode.getName() + "». Удачи!");
            } else {
                tell(entry, "не получилось перенести вас на арену, скажите админу.");
                log(Level.SEVERE, "телепорт не сработал (" + mode.getId() + "): " + error);
            }
        };
        if (!ServerApi.runOnWorldThread(entry.getWorld(), task)) {
            task.run();
        }
    }

    // ------------------------------------------------------------- мелкая работа

    private ModeQueue queue(String modeId) {
        return this.queues.computeIfAbsent(modeId, id -> new ModeQueue());
    }

    private boolean removeFromQueues(String playerKey) {
        boolean removed = false;
        for (ModeQueue queue : this.queues.values()) {
            Iterator<QueueEntry> iterator = queue.players.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getKey().equals(playerKey)) {
                    iterator.remove();
                    removed = true;
                }
            }
            if (removed && queue.players.isEmpty()) {
                queue.countdown = -1;
            }
        }
        return removed;
    }

    /** Убирает тех, кто вышел с сервера или ждёт дольше таймаута. */
    private void dropLostPlayers(ModeQueue queue) {
        int timeout = this.config.getQueueTimeoutSeconds();
        Iterator<QueueEntry> iterator = queue.players.iterator();
        while (iterator.hasNext()) {
            QueueEntry entry = iterator.next();
            if (!entry.isOnline()) {
                iterator.remove();
                continue;
            }
            if (timeout > 0 && entry.waitedSeconds() >= timeout) {
                tell(entry, "никого не нашли за " + timeout + " с, выводим из очереди.");
                iterator.remove();
            }
        }
    }

    /** Выбрасывает очереди режимов, которых больше нет в настройках. */
    private void dropQueuesOfMissingModes() {
        Iterator<Map.Entry<String, ModeQueue>> iterator = this.queues.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ModeQueue> entry = iterator.next();
            if (this.config.findMode(entry.getKey()) == null) {
                for (QueueEntry player : entry.getValue().players) {
                    tell(player, "режим удалён из настроек, очередь распущена.");
                }
                iterator.remove();
            }
        }
    }

    private void broadcastQueue(ModeQueue queue, String text) {
        for (QueueEntry entry : queue.players) {
            tell(entry, text);
        }
    }

    private void tell(QueueEntry entry, String text) {
        try {
            entry.getPlayerRef().sendMessage(Message.raw(PREFIX + text));
        } catch (Throwable throwable) {
            // игрок мог выйти между проверкой и отправкой — это не беда
        }
    }

    private void announce(World world, String text) {
        ServerApi.broadcast(world, Message.raw(PREFIX + text));
    }

    private void log(Level level, String text) {
        this.logger.at(level).log("[Поиск группы] " + text);
    }

    /** Очередь одного режима плюс её отсчёт. */
    private static final class ModeQueue {
        private final List<QueueEntry> players = new ArrayList<>();
        /** -1 — отсчёт не идёт. */
        private int countdown = -1;
    }
}
