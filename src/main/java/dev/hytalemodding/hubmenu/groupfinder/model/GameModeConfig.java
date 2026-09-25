package dev.hytalemodding.hubmenu.groupfinder.model;

import dev.hytalemodding.hubmenu.groupfinder.storage.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** Настройки одной мини-игры: сколько игроков собирать и куда их уносить. */
public class GameModeConfig {

    public static final int MIN_PLAYERS_LIMIT = 1;
    public static final int MAX_PLAYERS_LIMIT = 64;
    public static final int MAX_COUNTDOWN = 120;
    public static final int MAX_SPREAD = 32;

    /** Короткий ключ режима, по нему режим ищут команды и меню. */
    private String id = "mode";
    /** Название в окне поиска. */
    private String name = "Режим";
    private boolean enabled;
    private int minPlayers = 2;
    private int maxPlayers = 2;
    /** Секунды между «группа собрана» и телепортом. 0 — уносит сразу. */
    private int countdownSeconds = 5;
    /** Радиус в блоках, на который игроков раскидывает вокруг точки. */
    private int spreadRadius;
    /** Номер карточки в окне «Мини-игры» (0..3), -1 — не привязан. */
    private int cardIndex = -1;
    private ArenaPoint arena = new ArenaPoint();

    public GameModeConfig() {
    }

    public GameModeConfig(String id, String name, int cardIndex) {
        this.id = id;
        this.name = name;
        this.cardIndex = cardIndex;
    }

    public static GameModeConfig fromJson(Map<String, Object> json) {
        GameModeConfig mode = new GameModeConfig();
        mode.id = Json.string(json, "id", "mode");
        mode.name = Json.string(json, "name", mode.id);
        mode.enabled = Json.bool(json, "enabled", false);
        mode.minPlayers = Json.integer(json, "minPlayers", 2);
        mode.maxPlayers = Json.integer(json, "maxPlayers", mode.minPlayers);
        mode.countdownSeconds = Json.integer(json, "countdownSeconds", 5);
        mode.spreadRadius = Json.integer(json, "spreadRadius", 0);
        mode.cardIndex = Json.integer(json, "cardIndex", -1);
        mode.arena = ArenaPoint.fromJson(Json.asObject(json.get("arena")));
        mode.normalise();
        return mode;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", this.id);
        json.put("name", this.name);
        json.put("enabled", this.enabled);
        json.put("minPlayers", this.minPlayers);
        json.put("maxPlayers", this.maxPlayers);
        json.put("countdownSeconds", this.countdownSeconds);
        json.put("spreadRadius", this.spreadRadius);
        json.put("cardIndex", this.cardIndex);
        json.put("arena", this.arena.toJson());
        return json;
    }

    /** Чинит значения, если файл правили руками и получилось что-то странное. */
    public void normalise() {
        if (this.id == null || this.id.trim().isEmpty()) {
            this.id = "mode";
        }
        this.id = this.id.trim().toLowerCase();
        if (this.name == null || this.name.trim().isEmpty()) {
            this.name = this.id;
        }
        this.minPlayers = clamp(this.minPlayers, MIN_PLAYERS_LIMIT, MAX_PLAYERS_LIMIT);
        this.maxPlayers = clamp(this.maxPlayers, this.minPlayers, MAX_PLAYERS_LIMIT);
        this.countdownSeconds = clamp(this.countdownSeconds, 0, MAX_COUNTDOWN);
        this.spreadRadius = clamp(this.spreadRadius, 0, MAX_SPREAD);
        if (this.cardIndex < -1) {
            this.cardIndex = -1;
        }
        if (this.arena == null) {
            this.arena = new ArenaPoint();
        }
    }

    /** Режим готов принимать игроков? */
    public boolean isReady() {
        return this.enabled && this.arena.isSet();
    }

    /** Почему режим не работает — текст для панели админа. */
    public String readyProblem() {
        if (!this.enabled) {
            return "выключен";
        }
        if (!this.arena.isSet()) {
            return "нет точки телепорта";
        }
        return "";
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
        normalise();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
        normalise();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinPlayers() {
        return this.minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
        normalise();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        if (this.maxPlayers < this.minPlayers) {
            this.minPlayers = this.maxPlayers;
        }
        normalise();
    }

    public int getCountdownSeconds() {
        return this.countdownSeconds;
    }

    public void setCountdownSeconds(int countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
        normalise();
    }

    public int getSpreadRadius() {
        return this.spreadRadius;
    }

    public void setSpreadRadius(int spreadRadius) {
        this.spreadRadius = spreadRadius;
        normalise();
    }

    public int getCardIndex() {
        return this.cardIndex;
    }

    public void setCardIndex(int cardIndex) {
        this.cardIndex = cardIndex;
        normalise();
    }

    public ArenaPoint getArena() {
        return this.arena;
    }
}
