package dev.hytalemodding.deathmatch.match;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemodding.deathmatch.bridge.ServerApi;
import dev.hytalemodding.deathmatch.model.DeathMatchConfig;
import dev.hytalemodding.deathmatch.model.Loadout;
import dev.hytalemodding.deathmatch.storage.ConfigStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Бой: кто на арене, кто сколько убил и какой комплект носит.
 *
 * Как мод цепляется к меню HubMenu. Никак напрямую — и это нарочно: два
 * мода не знают о классах друг друга и обновляются порознь. Связь через
 * место: поиск группы в меню приносит игроков на свою точку, а дезматч
 * раз в пару секунд смотрит, кто стоит внутри круга своей арены. Совпали
 * точки — карточка мини-игры в меню запускает бой. Не совпали — дезматч
 * работает сам по себе, игроки приходят ногами.
 *
 * Секундный таймер крутится в своём потоке, поэтому всё, что трогает мир
 * (выдача предметов, телепорт), уходит через {@link ServerApi}.
 */
public class MatchService {

    private static final String PREFIX = "[Дезматч] ";
    /** Узлы права, любой из которых пускает к настройкам. */
    private static final String[] ADMIN_PERMISSIONS = { "deathmatch.admin", "*" };
    private static final int TICK_SECONDS = 2;
    /** Через столько без единой удачной проверки боец считается ушедшим. */
    private static final long LOST_MILLIS = 15_000L;

    private final HytaleLogger logger;
    private final ConfigStore store;
    private DeathMatchConfig config;

    /** Все, кто сейчас в бою. Ключ — устойчивый идентификатор игрока. */
    private final Map<String, Fighter> fighters = new ConcurrentHashMap<>();
    /** Кого опрашивать по таймеру: все, кто зашёл на сервер. */
    private final Map<String, PlayerRef> online = new ConcurrentHashMap<>();
    /** Последний известный мир — для объявлений в общий чат. */
    private volatile Object lastWorld;

    private ScheduledExecutorService ticker;

    public MatchService(HytaleLogger logger, ConfigStore store) {
        this.logger = logger;
        this.store = store;
        this.config = store.load();
    }

    // ------------------------------------------------------------------ запуск

    public synchronized void start() {
        if (this.ticker != null) {
            return;
        }
        this.ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DeathMatch-Arena");
            thread.setDaemon(true);
            return thread;
        });
        this.ticker.scheduleAtFixedRate(this::safeTick, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);

        if (!this.store.getLastError().isEmpty()) {
            log(Level.WARNING, this.store.getLastError());
        }
        if (this.config.isSetupMode()) {
            log(Level.WARNING, "список админов пуст — команды настройки пока открыты всем. "
                    + "Зайдите в игру и наберите /dmadmin.");
        }
        if (!this.config.getArena().isSet()) {
            log(Level.WARNING, "точка арены не задана: встаньте на арену и наберите /dmsetarena.");
        }
        log(Level.INFO, "дезматч готов, уровней в настройках: " + this.config.getLevels().size());
    }

    public synchronized void stop() {
        if (this.ticker != null) {
            this.ticker.shutdownNow();
            this.ticker = null;
        }
        for (Fighter fighter : this.fighters.values()) {
            tell(fighter, "сервер выключает дезматч.");
        }
        this.fighters.clear();
        this.online.clear();
    }

    // ---------------------------------------------------------------- игроки

    /** Игрок зашёл на сервер — с этого момента его позицию опрашивает таймер. */
    public void trackPlayer(PlayerRef playerRef) {
        if (playerRef != null) {
            this.online.put(ServerApi.playerKey(playerRef), playerRef);
        }
    }

    public void untrackPlayer(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        String key = ServerApi.playerKey(playerRef);
        this.online.remove(key);
        Fighter left = this.fighters.remove(key);
        if (left != null) {
            announce(left.getUsername() + " вышел из боя.");
        }
    }

    public void rememberWorld(Object world) {
        if (world != null) {
            this.lastWorld = world;
        }
    }

    // ------------------------------------------------------------------- тик

    private void safeTick() {
        try {
            tick();
        } catch (RuntimeException | LinkageError problem) {
            log(Level.WARNING, "сбой в проверке арены: " + problem);
        }
    }

    private void tick() {
        if (!this.config.isReady()) {
            return;
        }

        for (Map.Entry<String, PlayerRef> entry : this.online.entrySet()) {
            PlayerRef playerRef = entry.getValue();
            if (!ServerApi.online(playerRef)) {
                this.online.remove(entry.getKey());
                Fighter gone = this.fighters.remove(entry.getKey());
                if (gone != null) {
                    announce(gone.getUsername() + " вышел из боя.");
                }
                continue;
            }

            double[] position = ServerApi.positionOf(playerRef);
            if (position == null) {
                continue;
            }

            boolean inside = insideArena(position);
            Fighter fighter = this.fighters.get(entry.getKey());

            if (inside && fighter == null) {
                join(playerRef);
            } else if (inside) {
                fighter.seen();
                if (!fighter.isOnArena()) {
                    // Вернулся после смерти: счёт за ним сохранился, выдаём
                    // комплект его уровня заново.
                    fighter.setOnArena(true);
                    if (this.config.isRegiveOnRespawn()) {
                        applyLevel(fighter, true);
                    }
                    tell(fighter, "вы снова в бою. " + statusLine(fighter.getPlayerKey()));
                }
            } else if (fighter != null && fighter.isOnArena()
                    && System.currentTimeMillis() - fighter.getLastSeenMillis() > LOST_MILLIS) {
                // Ушёл с арены — счёт не трогаем: игрок мог умереть и
                // возродиться на спавне. Вернётся — продолжит с тем же счётом.
                fighter.setOnArena(false);
                tell(fighter, "вы вне арены. Счёт сохранён, вернитесь — и бой продолжится."
                        + " Совсем выйти — /dmleave.");
                announce(fighter.getUsername() + " вне арены ("
                        + fighter.getKills() + " убийств).");
            }
        }
    }

    private boolean insideArena(double[] position) {
        double dx = position[0] - this.config.getArena().getX();
        double dz = position[2] - this.config.getArena().getZ();
        double dy = position[1] - this.config.getArena().getY();
        int radius = this.config.getRadius();
        // По высоте допуск больше: арены бывают многоэтажными.
        return dx * dx + dz * dz <= (double) radius * radius && Math.abs(dy) <= radius * 2.0;
    }

    // ------------------------------------------------------------------- бой

    /** Заводит бойца и выдаёт стартовый комплект. */
    public Fighter join(PlayerRef playerRef) {
        String key = ServerApi.playerKey(playerRef);
        Fighter existing = this.fighters.get(key);
        if (existing != null) {
            return existing;
        }

        Fighter fighter = new Fighter(key, ServerApi.username(playerRef), playerRef);
        this.fighters.put(key, fighter);
        this.online.put(key, playerRef);

        applyLevel(fighter, true);
        tell(fighter, "вы в бою. Убивайте — и на " + nextStepText(fighter) + ".");
        announce(fighter.getUsername() + " вступил в бой. Бойцов на арене: " + this.fighters.size());
        return fighter;
    }

    /** Убирает бойца из матча. */
    public boolean leave(String playerKey, boolean clearInventory) {
        Fighter fighter = this.fighters.remove(playerKey);
        if (fighter == null) {
            return false;
        }
        if (clearInventory) {
            Equipment.clear(fighter.getPlayerRef());
        }
        tell(fighter, "вы вышли из боя. Убийств: " + fighter.getKills() + ".");
        return true;
    }

    /**
     * Засчитывает убийство. Вызывается из ECS-системы, то есть уже на потоке
     * мира — поэтому предметы выдаются прямо здесь.
     */
    public void onKill(PlayerRef killerRef, PlayerRef victimRef) {
        if (killerRef == null || !this.config.isEnabled()) {
            return;
        }

        String killerKey = ServerApi.playerKey(killerRef);
        Fighter killer = this.fighters.get(killerKey);
        if (killer == null || !killer.isOnArena()) {
            return;
        }

        Fighter victim = victimRef == null ? null : this.fighters.get(ServerApi.playerKey(victimRef));
        if (victim != null) {
            // Игрок умер — он уедет на спавн, комплект вернём, когда придёт назад.
            victim.setOnArena(false);
            victim.addDeath();
            if (this.config.isResetLevelOnDeath()) {
                victim.resetScore();
                applyLevel(victim, true);
                tell(victim, "смерть сбросила ваш уровень — начинайте заново.");
            } else if (this.config.isRegiveOnRespawn()) {
                applyLevel(victim, true);
            }
        }

        int kills = killer.addKill();
        int newLevel = this.config.levelIndexFor(kills);
        if (newLevel != killer.getLevelIndex()) {
            applyLevel(killer, true);
            Loadout level = this.config.levelFor(kills);
            announce(killer.getUsername() + " — " + (level == null ? "новый уровень" : level.getName())
                    + " (" + kills + " убийств).");
        } else {
            tell(killer, "убийство засчитано: " + kills + ". До " + nextStepText(killer) + ".");
        }

        int goal = this.config.getGoalKills();
        if (goal > 0 && kills >= goal) {
            finish(killer);
        }
    }

    /** Матч дошёл до цели: объявляем победителя и начинаем заново. */
    private void finish(Fighter winner) {
        announce("матч окончен. Победитель — " + winner.getUsername()
                + " (" + winner.getKills() + " убийств).");
        for (Fighter fighter : this.fighters.values()) {
            fighter.resetScore();
            applyLevel(fighter, true);
        }
    }

    /** Полный сброс матча — команда админа. */
    public void resetMatch() {
        for (Fighter fighter : this.fighters.values()) {
            fighter.resetScore();
            applyLevel(fighter, true);
        }
        announce("счёт сброшен, бой начинается заново.");
    }

    /** Выдаёт бойцу комплект его уровня. */
    public void applyLevel(Fighter fighter, boolean force) {
        Loadout level = this.config.levelFor(fighter.getKills());
        if (level == null) {
            return;
        }
        int index = this.config.levelIndexFor(fighter.getKills());
        if (!force && index == fighter.getLevelIndex()) {
            return;
        }

        Equipment.Result result = Equipment.give(fighter.getPlayerRef(), level);
        fighter.setLevelIndex(index);
        if (!result.isOk()) {
            tell(fighter, "комплект выдался не полностью — " + result.getMessage());
            log(Level.WARNING, "уровень «" + level.getName() + "»: " + result.getMessage());
        }
    }

    /** Система убийств споткнулась — пишем один раз в консоль, не спамим. */
    public void noteKillProblem(Throwable problem) {
        log(Level.WARNING, "убийство не засчиталось: " + problem);
    }

    // --------------------------------------------------------------- справки

    public Fighter fighter(String playerKey) {
        return this.fighters.get(playerKey);
    }

    public int fighterCount() {
        int count = 0;
        for (Fighter fighter : this.fighters.values()) {
            if (fighter.isOnArena()) {
                count++;
            }
        }
        return count;
    }

    /** Строка состояния для /dm. */
    public String statusLine(String playerKey) {
        Fighter fighter = this.fighters.get(playerKey);
        if (fighter == null) {
            if (!this.config.getArena().isSet()) {
                return "арена ещё не настроена — скажите админу набрать /dmsetarena.";
            }
            return "вы не в бою. Арена: " + this.config.getArena().describe()
                    + ", радиус " + this.config.getRadius() + ". Придите туда — бой начнётся сам.";
        }

        Loadout level = this.config.levelFor(fighter.getKills());
        return "убийств: " + fighter.getKills() + ", смертей: " + fighter.getDeaths()
                + ", уровень: " + (level == null ? "—" : level.getName())
                + ". До " + nextStepText(fighter) + ".";
    }

    /** Таблица лидеров матча, сверху — лучшие. */
    public List<Fighter> leaderboard() {
        List<Fighter> list = new ArrayList<>(this.fighters.values());
        list.sort(Comparator.comparingInt(Fighter::getKills).reversed());
        return list;
    }

    private String nextStepText(Fighter fighter) {
        Loadout next = this.config.nextLevel(fighter.getKills());
        if (next == null) {
            int goal = this.config.getGoalKills();
            if (goal > 0) {
                return "победы осталось " + Math.max(0, goal - fighter.getKills());
            }
            return "последнего уровня вы уже дошли";
        }
        return "уровня «" + next.getName() + "» осталось "
                + Math.max(0, next.getKills() - fighter.getKills());
    }

    /** Что лежит в настройках — для команд админа. */
    public DeathMatchConfig config() {
        return this.config;
    }

    public ConfigStore store() {
        return this.store;
    }

    public boolean saveConfig() {
        return this.store.save(this.config);
    }

    /** Перечитывает файл настроек, матч при этом не рвётся. */
    public String reload() {
        this.config = this.store.load();
        for (Fighter fighter : this.fighters.values()) {
            applyLevel(fighter, true);
        }
        return this.store.getLastError().isEmpty()
                ? "настройки перечитаны, уровней: " + this.config.getLevels().size()
                : this.store.getLastError();
    }

    /**
     * Может ли игрок пользоваться командами настройки.
     *
     * Право спрашиваем у PlayerRef: PermissionHolder у сервера именно он.
     * Узлов проверяем несколько — на серверах право называется по-разному,
     * а у оператора обычно стоит звёздочка.
     */
    public boolean isAdmin(PlayerRef playerRef) {
        if (this.config.isSetupMode()) {
            return true;
        }
        if (this.config.isAdmin(ServerApi.username(playerRef))) {
            return true;
        }
        for (String node : ADMIN_PERMISSIONS) {
            if (Boolean.TRUE.equals(ServerApi.hasPermission(playerRef, node))) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- мелочи

    private void tell(Fighter fighter, String text) {
        try {
            fighter.getPlayerRef().sendMessage(Message.raw(PREFIX + text));
        } catch (RuntimeException ignored) {
            // игрок мог выйти между проверкой и отправкой
        }
    }

    /** Сообщение всем бойцам, а если известен мир — то и в общий чат. */
    private void announce(String text) {
        if (!this.config.isAnnounce()) {
            return;
        }
        Object world = this.lastWorld;
        if (world != null && ServerApi.broadcast(world, Message.raw(PREFIX + text))) {
            return;
        }
        for (Fighter fighter : this.fighters.values()) {
            tell(fighter, text);
        }
    }

    private void log(Level level, String text) {
        this.logger.at(level).log(PREFIX + text);
    }

    /** Для диагностики: что видит мод про каждого бойца. */
    public Map<String, String> debugSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Fighter fighter : this.fighters.values()) {
            double[] position = ServerApi.positionOf(fighter.getPlayerRef());
            snapshot.put(fighter.getUsername(),
                    "убийств " + fighter.getKills()
                            + ", уровень " + fighter.getLevelIndex()
                            + ", позиция " + (position == null ? "неизвестна"
                            : Math.round(position[0]) + "/" + Math.round(position[1])
                            + "/" + Math.round(position[2])));
        }
        return snapshot;
    }
}
