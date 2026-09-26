package dev.hytalemodding.deathmatch.match;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Игрок в текущем матче: сколько убил, сколько раз умер, какой уровень носит. */
public class Fighter {

    private final String playerKey;
    private final String username;
    private final PlayerRef playerRef;

    private int kills;
    private int deaths;
    /** Номер уже выданного уровня — чтобы не выдавать один и тот же дважды. */
    private int levelIndex = -1;
    /** Время последней проверки: по нему мы понимаем, что игрок ушёл с арены. */
    private long lastSeenMillis = System.currentTimeMillis();
    /** false — боец сейчас вне круга арены, но счёт за ним сохраняется. */
    private boolean onArena = true;

    public Fighter(String playerKey, String username, PlayerRef playerRef) {
        this.playerKey = playerKey;
        this.username = username;
        this.playerRef = playerRef;
    }

    public String getPlayerKey() {
        return this.playerKey;
    }

    public String getUsername() {
        return this.username;
    }

    public PlayerRef getPlayerRef() {
        return this.playerRef;
    }

    public int getKills() {
        return this.kills;
    }

    public int addKill() {
        return ++this.kills;
    }

    public int getDeaths() {
        return this.deaths;
    }

    public void addDeath() {
        this.deaths++;
    }

    public void resetScore() {
        this.kills = 0;
        this.deaths = 0;
        this.levelIndex = -1;
    }

    public int getLevelIndex() {
        return this.levelIndex;
    }

    public void setLevelIndex(int levelIndex) {
        this.levelIndex = levelIndex;
    }

    public void seen() {
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public long getLastSeenMillis() {
        return this.lastSeenMillis;
    }

    public boolean isOnArena() {
        return this.onArena;
    }

    public void setOnArena(boolean onArena) {
        this.onArena = onArena;
    }
}
