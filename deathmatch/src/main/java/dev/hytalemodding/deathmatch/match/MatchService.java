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

    private final HytaleLogger logger;
    private final ConfigStore store;
    private DeathMatchConfig config;

    /** Все, кто сейчас в бою. Ключ — устойчивый идентификатор игрока. */
    private final Map<String, Fighter> fighters = new ConcurrentHashMap<>();
    /** Кого опрашивать по таймеру: все, кто зашёл на сервер. */
    private final Map<String, PlayerRef> online = new ConcurrentHashMap<>();
    /** Последний известный мир — для объявлений в общий чат. */
    private volatile Object lastWorld;
    /**
     * Ступень матча, о которой уже объявили.
     *
     * Ступень общая на всех и держится на лучшем счёте: как только кто-то
     * добрался до порога, новое оружие получают сразу все, кто на арене.
     */
    private int announcedTier;

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
                    // Вернулся на арену: счёт за ним сохранился, выдаём
                    // оружие текущей ступени матча.
                    fighter.setOnArena(true);
                    equip(fighter);
                    tell(fighter, "вы снова в бою. " + statusLine(fighter.getPlayerKey()));
                    announce(fighter.getUsername() + " вернулся в бой.");
                }
            } else if (fighter != null && fighter.isOnArena()) {
                // Вышел за круг — режим выключается сразу, оружие забираем.
                // Счёт остаётся за игроком: вернётся — продолжит с того же
                // места, а вот драться снаружи выданным мечом не выйдет.
                fighter.setOnArena(false);
                Equipment.clear(fighter.getPlayerRef());
                tell(fighter, "вы вышли за границу арены: режим выключен, оружие забрано."
                        + " Счёт (" + fighter.getKills() + ") сохранён — вернитесь, и бой продолжится.");
                announce(fighter.getUsername() + " вышел с арены ("
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

        equip(fighter);
        tell(fighter, "вы в бою. " + statusLine(key));
        announce(fighter.getUsername() + " вступил в бой. Бойцов на арене: " + fighterCount());
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
            victim.addDeath();
            if (this.config.isResetLevelOnDeath()) {
                victim.resetScore();
                tell(victim, "смерть сбросила ваш счёт — начинайте заново.");
            }
        }

        int kills = killer.addKill();

        // Ступень одна на всех: её поднимает тот, кто первым дошёл до порога,
        // а оружие после этого меняется у каждого, кто стоит на арене.
        int tier = tierIndex();
        if (tier > this.announcedTier) {
            this.announcedTier = tier;
            Loadout level = tierLevel();
            announce(killer.getUsername() + " дошёл до " + kills + " убийств — всем выдан "
                    + (level == null ? "новый комплект" : level.getName()) + ".");
            equipEveryoneOnArena();
        } else {
            tell(killer, "убийство засчитано: " + kills + ". " + nextStepText());
        }

        int goal = this.config.getGoalKills();
        if (goal > 0 && kills >= goal) {
            finish(killer);
        }
    }

    /** Номер ступени матча: считается по лучшему счёту среди бойцов. */
    private int tierIndex() {
        return this.config.levelIndexFor(bestKills());
    }

    /** Комплект текущей ступени — он же у всех на арене. */
    private Loadout tierLevel() {
        return this.config.levelFor(bestKills());
    }

    /** Лучший счёт матча. Бойцы, отошедшие с арены, тоже учитываются. */
    private int bestKills() {
        int best = 0;
        for (Fighter fighter : this.fighters.values()) {
            best = Math.max(best, fighter.getKills());
        }
        return best;
    }

    private void equipEveryoneOnArena() {
        for (Fighter fighter : this.fighters.values()) {
            if (fighter.isOnArena()) {
                equip(fighter);
            }
        }
    }

    /** Матч дошёл до цели: объявляем победителя и начинаем заново. */
    private void finish(Fighter winner) {
        announce("матч окончен. Победитель — " + winner.getUsername()
                + " (" + winner.getKills() + " убийств). Счёт сброшен, оружие снова стартовое.");
        restart();
    }

    /** Выдаёт оружие текущей ступени всем, кто на арене. */
    public void reequipEveryone() {
        equipEveryoneOnArena();
    }

    /** Полный сброс матча — команда админа. */
    public void resetMatch() {
        restart();
        announce("счёт сброшен, бой начинается заново.");
    }

    private void restart() {
        for (Fighter fighter : this.fighters.values()) {
            fighter.resetScore();
        }
        this.announcedTier = 0;
        equipEveryoneOnArena();
    }

    /**
     * Выдаёт бойцу оружие текущей ступени матча.
     *
     * Ступень общая, поэтому смотрим не на личный счёт бойца, а на лучший
     * счёт в матче: догоняющие дерутся тем же оружием, что и лидер.
     */
    public void equip(Fighter fighter) {
        Loadout level = tierLevel();
        if (level == null) {
            return;
        }

        Equipment.Result result = Equipment.give(fighter.getPlayerRef(), level);
        fighter.setLevelIndex(tierIndex());
        if (!result.isOk()) {
            tell(fighter, "комплект выдался не полностью — " + result.getMessage());
            log(Level.WARNING, "ступень «" + level.getName() + "»: " + result.getMessage());
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

        Loadout level = tierLevel();
        String state = fighter.isOnArena() ? "" : " (вы вне арены, оружие забрано)";
        return "убийств: " + fighter.getKills() + ", смертей: " + fighter.getDeaths()
                + ", оружие матча: " + (level == null ? "—" : level.getName())
                + ", лучший счёт: " + bestKills() + ". " + nextStepText() + state;
    }

    /** Таблица лидеров матча, сверху — лучшие. */
    public List<Fighter> leaderboard() {
        List<Fighter> list = new ArrayList<>(this.fighters.values());
        list.sort(Comparator.comparingInt(Fighter::getKills).reversed());
        return list;
    }

    /**
     * Что будет дальше в матче. Считается по лучшему счёту: ступень общая,
     * и до неё остаётся столько, сколько не хватает лидеру.
     */
    private String nextStepText() {
        int best = bestKills();
        Loadout next = this.config.nextLevel(best);
        int goal = this.config.getGoalKills();

        if (next != null) {
            return "до оружия «" + next.getName() + "» осталось "
                    + Math.max(0, next.getKills() - best) + " убийств.";
        }
        if (goal > 0) {
            return "до конца матча осталось " + Math.max(0, goal - best) + " убийств.";
        }
        return "оружие последней ступени уже выдано.";
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
        this.announcedTier = tierIndex();
        equipEveryoneOnArena();
        return this.store.getLastError().isEmpty()
                ? "настройки перечитаны, ступеней: " + this.config.getLevels().size()
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
