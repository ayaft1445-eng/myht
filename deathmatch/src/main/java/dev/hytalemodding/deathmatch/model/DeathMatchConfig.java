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
    private int goalKills = 30;
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

    /**
     * Настройки по умолчанию: четыре уровня по 0 / 10 / 20 / 30 убийств.
     *
     * Идентификаторы предметов взяты обычные для Hytale, но на разных
     * сборках набор ассетов разный — проверьте их командой /dmgive и
     * поправьте deathmatch.json под свой сервер.
     */
    public static DeathMatchConfig defaults() {
        DeathMatchConfig config = new DeathMatchConfig();
        config.levels.add(new Loadout(0, "НОВИЧОК", "hytale:wooden_sword",
                "", "hytale:leather_chestplate", "", ""));
        config.levels.add(new Loadout(10, "БОЕЦ", "hytale:stone_sword",
                "hytale:iron_helmet", "hytale:iron_chestplate", "", "hytale:iron_leggings"));
        config.levels.add(new Loadout(20, "ВЕТЕРАН", "hytale:iron_sword",
                "hytale:iron_helmet", "hytale:iron_chestplate", "hytale:iron_gauntlets",
                "hytale:iron_leggings"));
        config.levels.add(new Loadout(30, "МАСТЕР", "hytale:thorium_sword",
                "hytale:thorium_helmet", "hytale:thorium_chestplate", "hytale:thorium_gauntlets",
                "hytale:thorium_leggings"));
        return config;
    }

    public static DeathMatchConfig fromJson(Map<String, Object> json) {
        DeathMatchConfig config = new DeathMatchConfig();
        config.enabled = Json.bool(json, "enabled", true);
        config.arena = ArenaPoint.fromJson(Json.asObject(json.get("arena")));
        config.radius = Json.integer(json, "radius", 60);
        config.goalKills = Json.integer(json, "goalKills", 30);
        config.announce = Json.bool(json, "announce", true);
        config.resetLevelOnDeath = Json.bool(json, "resetLevelOnDeath", false);
        config.regiveOnRespawn = Json.bool(json, "regiveOnRespawn", true);

        for (Object entry : Json.asArray(json.get("admins"))) {
            if (entry instanceof String && !((String) entry).isEmpty()) {
                config.admins.add(((String) entry).toLowerCase());
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

    public void addAdmin(String username) {
        if (username != null && !username.isEmpty()) {
            this.admins.add(username.toLowerCase());
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
