package dev.hytalemodding.hubmenu.groupfinder.queue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;

/**
 * Игрок, который ждёт игру.
 *
 * Здесь же лежат ссылки, нужные для телепорта: они берутся в момент, когда
 * игрок встаёт в очередь. Перед переносом очередь ещё раз проверяет, что
 * игрок на сервере.
 */
public class QueueEntry {

    private final String key;
    private final String username;
    private final PlayerRef playerRef;
    private final Ref<EntityStore> ref;
    private final Store<EntityStore> store;
    private final World world;
    private final long joinedAtMillis;

    public QueueEntry(
            PlayerRef playerRef,
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            World world
    ) {
        this.playerRef = playerRef;
        this.ref = ref;
        this.store = store;
        this.world = world;
        this.key = ServerApi.playerKey(playerRef);
        this.username = ServerApi.username(playerRef);
        this.joinedAtMillis = System.currentTimeMillis();
    }

    public String getKey() {
        return this.key;
    }

    public String getUsername() {
        return this.username;
    }

    public PlayerRef getPlayerRef() {
        return this.playerRef;
    }

    public Ref<EntityStore> getRef() {
        return this.ref;
    }

    public Store<EntityStore> getStore() {
        return this.store;
    }

    public World getWorld() {
        return this.world;
    }

    public long getJoinedAtMillis() {
        return this.joinedAtMillis;
    }

    public int waitedSeconds() {
        return (int) ((System.currentTimeMillis() - this.joinedAtMillis) / 1000L);
    }

    public boolean isOnline() {
        return ServerApi.online(this.playerRef);
    }
}
