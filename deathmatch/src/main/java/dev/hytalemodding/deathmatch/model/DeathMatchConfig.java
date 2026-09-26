package dev.hytalemodding.deathmatch.model;

import dev.hytalemodding.deathmatch.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Настройки дезматча — всё, что лежит в deathmatch.json.
 *
 * Арена задаётся одной точкой и радиусом: кто внутри круга — тот в бою.
 * Так мод цепляется к меню HubMenu, ничего о нём не зная: поиск группы
 * приносит игроков на свою точку, а дезматч видит их по координатам.
 * Поэтому точку дезматча ставят там же, где точку режима в /gfadmin.
 */
public class DeathMatchConfig {

    private boolean enabled = true;
    /** Центр арены и место возрождения. */
    private ArenaPoint arena = new ArenaPoint();
    /** Радиус зоны боя в блоках вокруг точки. */
    private int radius = 60;
    /** Сколько убийств заканчивают матч. 0 — матч не кончается. */
    private int goalKills = DEFAULT_GOAL;
    /** Писать ли о входах, уровнях и победах в общий чат. */
    private boolean announce = true;
    /** true — после смерти игрок падает на первый уровень. */
    private boolean resetLevelOnDeath;
    /** Выдавать ли комплект заново после каждой смерти. */
    private boolean regiveOnRespawn = true;
    /** Ники админов: им доступны /dmsetarena, /dmreset и прочее. */
    private final Set<String> admins = new LinkedHashSet<>();
    /** Уровни по возрастанию числа убийств. Первый — стартовый комплект. */
    private final List<Loadout> levels = new ArrayList<>();

    /** Сколько убийств заканчивают матч по умолчанию. */
    public static final int DEFAULT_GOAL = 100;

    /**
     * Лестница мечей по умолчанию — настоящие предметы Hytale.
     *
     * Ступень в матче одна на всех: как только лучший игрок добирается до
     * порога, новый меч получают все, кто стоит на арене.
     */
    private static List<Loadout> defaultLevels() {
        List<Loadout> levels = new ArrayList<>();
        levels.add(new Loadout(0, "ПРИМИТИВНЫЙ МЕЧ", "Weapon_Sword_Crude", "", "", "", ""));
        levels.add(new Loadout(15, "МЕЧ ИЗ ХЛАМА", "Weapon_Sword_Scrap", "", "", "", ""));
        levels.add(new Loadout(40, "КАМЕННЫЙ МЕЧ ТРОРКОВ", "Weapon_Sword_Stone_Trork", "", "", "", ""));
        levels.add(new Loadout(70, "ТОРИЕВЫЙ МЕЧ", "Weapon_Sword_Thorium", "", "", "", ""));
        return levels;
    }

    /** Настройки по умолчанию: лестница мечей и матч до ста убийств. */
    public static DeathMatchConfig defaults() {
        DeathMatchConfig config = new DeathMatchConfig();
        config.goalKills = DEFAULT_GOAL;
        config.levels.addAll(defaultLevels());
        return config;
    }

    /**
     * Возвращает настройки к стандартным: четыре меча по порогам 0, 15, 40
     * и 70 убийств и матч до ста. Арену, радиус и список админов не трогаем —
     * их настраивали руками.
     */
    public void applySimplePreset() {
        this.levels.clear();
        this.levels.addAll(defaultLevels());
        this.goalKills = DEFAULT_GOAL;
        this.enabled = true;
    }

    public static DeathMatchConfig fromJson(Map<String, Object> json) {
        DeathMatchConfig config = new DeathMatchConfig();
        config.enabled = Json.bool(json, "enabled", true);
        config.arena = ArenaPoint.fromJson(Json.asObject(json.get("arena")));
        config.radius = Json.integer(json, "radius", 60);
        config.goalKills = Json.integer(json, "goalKills", DEFAULT_GOAL);
        config.announce = Json.bool(json, "announce", true);
        config.resetLevelOnDeath = Json.bool(json, "resetLevelOnDeath", false);
        config.regiveOnRespawn = Json.bool(json, "regiveOnRespawn", true);

        for (Object entry : Json.asArray(json.get("admins"))) {
            if (entry instanceof String) {
                config.addAdmin((String) entry);
            }
        }
        for (Object entry : Json.asArray(json.get("levels"))) {
            config.levels.add(Loadout.fromJson(Json.asObject(entry)));
        }
        if (config.levels.isEmpty()) {
            config.levels.addAll(defaults().levels);
        }
        config.sortLevels();
        return config;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("enabled", this.enabled);
        json.put("arena", this.arena.toJson());
        json.put("radius", this.radius);
        json.put("goalKills", this.goalKills);
        json.put("announce", this.announce);
        json.put("resetLevelOnDeath", this.resetLevelOnDeath);
        json.put("regiveOnRespawn", this.regiveOnRespawn);
        json.put("admins", new ArrayList<Object>(this.admins));

        List<Object> levels = new ArrayList<>();
        for (Loadout level : this.levels) {
            levels.add(level.toJson());
        }
        json.put("levels", levels);
        return json;
    }

    /** Уровни всегда идут по возрастанию убийств — на этом держится поиск уровня. */
    public void sortLevels() {
        this.levels.sort((left, right) -> Integer.compare(left.getKills(), right.getKills()));
    }

    /** Какой комплект положен за столько убийств. */
    public Loadout levelFor(int kills) {
        Loadout found = null;
        for (Loadout level : this.levels) {
            if (kills >= level.getKills()) {
                found = level;
            }
        }
        return found == null ? (this.levels.isEmpty() ? null : this.levels.get(0)) : found;
    }

    /** Номер уровня по числу убийств — 0 для стартового. */
    public int levelIndexFor(int kills) {
        int index = 0;
        for (int i = 0; i < this.levels.size(); i++) {
            if (kills >= this.levels.get(i).getKills()) {
                index = i;
            }
        }
        return index;
    }

    /** Следующий уровень или null, если этот последний. */
    public Loadout nextLevel(int kills) {
        int index = levelIndexFor(kills);
        return index + 1 < this.levels.size() ? this.levels.get(index + 1) : null;
    }

    /** Готова ли арена к бою: включено и точка поставлена. */
    public boolean isReady() {
        return this.enabled && this.arena.isSet() && !this.levels.isEmpty();
    }

    /** Пока список админов пуст, админские команды открыты всем. */
    public boolean isSetupMode() {
        return this.admins.isEmpty();
    }

    public boolean isAdmin(String username) {
        return username != null && this.admins.contains(username.toLowerCase());
    }

    /**
     * «?» сюда не попадает специально: так ServerApi отвечает, когда имя
     * игрока прочитать не вышло. Попади такой ответ в файл — список перестал
     * бы быть пустым, режим первой настройки выключился бы, и админом не смог
     * бы стать уже никто.
     */
    public void addAdmin(String username) {
        if (username == null) {
            return;
        }
        String name = username.trim();
        if (!name.isEmpty() && !"?".equals(name)) {
            this.admins.add(name.toLowerCase());
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ArenaPoint getArena() {
        return this.arena;
    }

    public int getRadius() {
        return this.radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(5, Math.min(500, radius));
    }

    public int getGoalKills() {
        return this.goalKills;
    }

    public void setGoalKills(int goalKills) {
        this.goalKills = Math.max(0, goalKills);
    }

    public boolean isAnnounce() {
        return this.announce;
    }

    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    public boolean isResetLevelOnDeath() {
        return this.resetLevelOnDeath;
    }

    public boolean isRegiveOnRespawn() {
        return this.regiveOnRespawn;
    }

    public List<Loadout> getLevels() {
        return this.levels;
    }

    public Set<String> getAdmins() {
        return this.admins;
    }
}
