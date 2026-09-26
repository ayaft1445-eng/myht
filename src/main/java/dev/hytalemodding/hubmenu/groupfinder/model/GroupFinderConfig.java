package dev.hytalemodding.hubmenu.groupfinder.model;

import dev.hytalemodding.hubmenu.groupfinder.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Всё, что настраивается у поиска группы.
 *
 * Файл лежит рядом с плагином и правится либо панелью админа, либо руками —
 * формат нарочно простой.
 */
public class GroupFinderConfig {

    public static final int FILE_VERSION = 1;
    /** Сколько карточек в окне «Мини-игры» — к ним можно привязать режимы. */
    public static final int MENU_CARDS = 4;

    private boolean enabled = true;
    /**
     * Ники админов. Пустой список — режим первой настройки, см. isSetupMode().
     *
     * Список читает такт службы, а меняет панель из другого потока, поэтому
     * здесь и у режимов — CopyOnWriteArrayList: перебор никогда не наткнётся
     * на правку.
     */
    private final List<String> admins = new CopyOnWriteArrayList<>();
    /** Писать ли в общий чат «идёт набор в …». */
    private boolean announce = true;
    /** Через сколько секунд простоя игрока убирает из очереди. 0 — никогда. */
    private int queueTimeoutSeconds;
    private final List<GameModeConfig> modes = new CopyOnWriteArrayList<>();

    public static GroupFinderConfig defaults() {
        GroupFinderConfig config = new GroupFinderConfig();
        config.modes.add(new GameModeConfig("deathmatch", "ДЕЗМАТЧ", 0));
        config.modes.add(new GameModeConfig("survival", "ВЫЖИВАНИЕ", 1));
        config.modes.add(new GameModeConfig("parkour", "ПАРКУР", 2));
        config.modes.add(new GameModeConfig("arena", "АРЕНА", 3));
        for (GameModeConfig mode : config.modes) {
            mode.setMinPlayers(2);
            mode.setMaxPlayers(2);
        }
        return config;
    }

    public static GroupFinderConfig fromJson(Map<String, Object> json) {
        GroupFinderConfig config = new GroupFinderConfig();
        config.enabled = Json.bool(json, "enabled", true);
        config.announce = Json.bool(json, "announce", true);
        config.queueTimeoutSeconds = Math.max(0, Json.integer(json, "queueTimeoutSeconds", 0));
        for (Object entry : Json.asArray(json.get("admins"))) {
            if (entry instanceof String) {
                config.addAdmin((String) entry);
            }
        }
        for (Object entry : Json.asArray(json.get("modes"))) {
            config.modes.add(GameModeConfig.fromJson(Json.asObject(entry)));
        }
        if (config.modes.isEmpty()) {
            config.modes.addAll(defaults().modes);
        }
        config.dropDuplicateIds();
        return config;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version", FILE_VERSION);
        json.put("enabled", this.enabled);
        json.put("announce", this.announce);
        json.put("queueTimeoutSeconds", this.queueTimeoutSeconds);
        json.put("admins", new ArrayList<Object>(this.admins));
        List<Object> modeList = new ArrayList<>();
        for (GameModeConfig mode : this.modes) {
            modeList.add(mode.toJson());
        }
        json.put("modes", modeList);
        return json;
    }

    // ------------------------------------------------------------------ режимы

    public List<GameModeConfig> getModes() {
        return this.modes;
    }

    public GameModeConfig findMode(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (GameModeConfig mode : this.modes) {
            if (mode.getId().equals(key)) {
                return mode;
            }
        }
        return null;
    }

    /** Режим, привязанный к карточке окна «Мини-игры». */
    public GameModeConfig findByCard(int cardIndex) {
        for (GameModeConfig mode : this.modes) {
            if (mode.getCardIndex() == cardIndex) {
                return mode;
            }
        }
        return null;
    }

    /** Добавляет режим со свободным ключом вида mode5. */
    public GameModeConfig addMode() {
        int number = this.modes.size() + 1;
        String id = "mode" + number;
        while (findMode(id) != null) {
            id = "mode" + (++number);
        }
        GameModeConfig mode = new GameModeConfig(id, "РЕЖИМ " + number, -1);
        this.modes.add(mode);
        return mode;
    }

    public boolean removeMode(String id) {
        GameModeConfig mode = findMode(id);
        return mode != null && this.modes.remove(mode);
    }

    /**
     * Одна карточка меню — один режим. Снимает привязку у остальных, чтобы
     * нажатие в меню всегда вело в один и тот же режим.
     */
    public void bindCard(GameModeConfig mode, int cardIndex) {
        if (cardIndex >= 0) {
            for (GameModeConfig other : this.modes) {
                if (other != mode && other.getCardIndex() == cardIndex) {
                    other.setCardIndex(-1);
                }
            }
        }
        mode.setCardIndex(cardIndex);
    }

    private void dropDuplicateIds() {
        List<String> seen = new ArrayList<>();
        for (GameModeConfig mode : this.modes) {
            String id = mode.getId();
            if (seen.contains(id)) {
                int suffix = 2;
                while (seen.contains(id + suffix)) {
                    suffix++;
                }
                mode.setId(id + suffix);
            }
            seen.add(mode.getId());
        }
    }

    // ------------------------------------------------------------------ админы

    public List<String> getAdmins() {
        return this.admins;
    }

    public void addAdmin(String username) {
        if (username == null) {
            return;
        }
        String name = username.trim();
        if (!name.isEmpty() && !isListedAdmin(name)) {
            this.admins.add(name);
        }
    }

    public void removeAdmin(String username) {
        this.admins.removeIf(name -> name.equalsIgnoreCase(username));
    }

    public boolean isListedAdmin(String username) {
        if (username == null) {
            return false;
        }
        for (String name : this.admins) {
            if (name.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Пока список админов пуст, панель открыта всем — иначе первый владелец
     * сервера не сможет себя добавить. Панель в этом режиме показывает красную
     * полосу с подсказкой, а в консоль уходит предупреждение.
     */
    public boolean isSetupMode() {
        return this.admins.isEmpty();
    }

    // -------------------------------------------------------------- остальное

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAnnounce() {
        return this.announce;
    }

    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    public int getQueueTimeoutSeconds() {
        return this.queueTimeoutSeconds;
    }

    public void setQueueTimeoutSeconds(int queueTimeoutSeconds) {
        this.queueTimeoutSeconds = Math.max(0, Math.min(3600, queueTimeoutSeconds));
    }
}
