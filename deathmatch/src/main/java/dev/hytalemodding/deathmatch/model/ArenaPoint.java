package dev.hytalemodding.deathmatch.model;

import dev.hytalemodding.deathmatch.storage.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Точка арены дезматча: центр зоны боя и место возрождения.
 *
 * Пустое имя мира значит «тот же мир, в котором стоял игрок» — это обычный
 * случай для сервера с одной картой и разными аренами на ней.
 */
public class ArenaPoint {

    private String world = "";
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    /** false — точку ещё не ставили, режим запускать нельзя. */
    private boolean set;

    public ArenaPoint() {
    }

    public static ArenaPoint fromJson(Map<String, Object> json) {
        ArenaPoint point = new ArenaPoint();
        point.world = Json.string(json, "world", "");
        point.x = Json.number(json, "x", 0);
        point.y = Json.number(json, "y", 0);
        point.z = Json.number(json, "z", 0);
        point.yaw = (float) Json.number(json, "yaw", 0);
        point.pitch = (float) Json.number(json, "pitch", 0);
        point.set = Json.bool(json, "set", false);
        return point;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("set", this.set);
        json.put("world", this.world);
        json.put("x", round(this.x));
        json.put("y", round(this.y));
        json.put("z", round(this.z));
        json.put("yaw", round(this.yaw));
        json.put("pitch", round(this.pitch));
        return json;
    }

    /** Записывает координаты игрока, который нажал «поставить точку здесь». */
    public void setTo(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world == null ? "" : world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.set = true;
    }

    public void clear() {
        this.world = "";
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.yaw = 0;
        this.pitch = 0;
        this.set = false;
    }

    public boolean isSet() {
        return this.set;
    }

    public String getWorld() {
        return this.world;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    /** Короткая строка для панели: «hub · 120.5 / 64.0 / -35.2». */
    public String describe() {
        if (!this.set) {
            return "не задана";
        }
        String where = this.world.isEmpty() ? "тот же мир" : this.world;
        return where + " · " + round(this.x) + " / " + round(this.y) + " / " + round(this.z);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
