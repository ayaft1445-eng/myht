package dev.hytalemodding.deathmatch.model;

import dev.hytalemodding.deathmatch.storage.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Уровень дезматча: с какого числа убийств он включается и что выдаётся.
 *
 * Идентификаторы предметов — строки ассетов сервера, например
 * «hytale:iron_sword». Мод их не проверяет: какие предметы есть на сборке,
 * знает только сервер. Неизвестный идентификатор просто не выдастся, а в
 * консоль уйдёт строка — поэтому список правится в файле настроек, а
 * проверяется командой /dmgive.
 */
public class Loadout {

    /** Сколько убийств нужно набрать, чтобы получить этот комплект. */
    private int kills;
    private String name;
    /** Оружие — кладётся в первый слот хотбара и берётся в руки. */
    private String weapon = "";
    /** Броня по слотам: голова, грудь, руки, ноги. Пустая строка — слот пуст. */
    private String helmet = "";
    private String chest = "";
    private String hands = "";
    private String legs = "";
    /** Что ещё положить в хотбар: зелья, блоки, стрелы. */
    private final List<String> extras = new ArrayList<>();

    public Loadout() {
        this.name = "";
    }

    public Loadout(int kills, String name, String weapon,
                   String helmet, String chest, String hands, String legs) {
        this.kills = kills;
        this.name = name;
        this.weapon = weapon;
        this.helmet = helmet;
        this.chest = chest;
        this.hands = hands;
        this.legs = legs;
    }

    public static Loadout fromJson(Map<String, Object> json) {
        Loadout loadout = new Loadout();
        loadout.kills = Json.integer(json, "kills", 0);
        loadout.name = Json.string(json, "name", "");
        loadout.weapon = Json.string(json, "weapon", "");
        loadout.helmet = Json.string(json, "helmet", "");
        loadout.chest = Json.string(json, "chest", "");
        loadout.hands = Json.string(json, "hands", "");
        loadout.legs = Json.string(json, "legs", "");
        for (Object entry : Json.asArray(json.get("extras"))) {
            if (entry instanceof String && !((String) entry).isEmpty()) {
                loadout.extras.add((String) entry);
            }
        }
        return loadout;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("kills", this.kills);
        json.put("name", this.name);
        json.put("weapon", this.weapon);
        json.put("helmet", this.helmet);
        json.put("chest", this.chest);
        json.put("hands", this.hands);
        json.put("legs", this.legs);
        json.put("extras", new ArrayList<Object>(this.extras));
        return json;
    }

    public int getKills() {
        return this.kills;
    }

    public void setKills(int kills) {
        this.kills = Math.max(0, kills);
    }

    public String getName() {
        return this.name == null || this.name.isEmpty() ? ("УРОВЕНЬ " + this.kills) : this.name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getWeapon() {
        return this.weapon;
    }

    public String getHelmet() {
        return this.helmet;
    }

    public String getChest() {
        return this.chest;
    }

    public String getHands() {
        return this.hands;
    }

    public String getLegs() {
        return this.legs;
    }

    public List<String> getExtras() {
        return this.extras;
    }

    /** Броня по порядку слотов ассета: голова, грудь, руки, ноги. */
    public String[] armorBySlot() {
        return new String[] { this.helmet, this.chest, this.hands, this.legs };
    }

    /** Строка для чата и панели: «БОЕЦ · с 10 убийств». */
    public String describe() {
        return getName() + " · с " + this.kills + " убийств";
    }
}
