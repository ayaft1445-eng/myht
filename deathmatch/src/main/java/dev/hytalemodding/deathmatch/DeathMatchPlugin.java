package dev.hytalemodding.deathmatch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.bridge.ServerApi;
import dev.hytalemodding.deathmatch.commands.DmAdminCommand;
import dev.hytalemodding.deathmatch.commands.DmCommand;
import dev.hytalemodding.deathmatch.commands.DmDiagCommand;
import dev.hytalemodding.deathmatch.commands.DmGiveCommand;
import dev.hytalemodding.deathmatch.commands.DmLeaveCommand;
import dev.hytalemodding.deathmatch.commands.DmReloadCommand;
import dev.hytalemodding.deathmatch.commands.DmResetCommand;
import dev.hytalemodding.deathmatch.commands.DmSetArenaCommand;
import dev.hytalemodding.deathmatch.commands.DmToggleCommand;
import dev.hytalemodding.deathmatch.commands.DmTopCommand;
import dev.hytalemodding.deathmatch.match.MatchService;
import dev.hytalemodding.deathmatch.storage.ConfigStore;
import dev.hytalemodding.deathmatch.systems.KillSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа мода «Дезматч».
 *
 * Что он делает. У арены есть точка и радиус; кто зашёл внутрь — тот в бою.
 * За убийства растёт счёт, и на заданных числах убийств (по умолчанию 0, 10,
 * 20 и 30) боец получает новый комплект: оружие в руки и броню в слоты.
 * Набрал цель матча — победа, счёт всем обнуляется, бой идёт дальше.
 *
 * Как он связан с меню HubMenu. Через место, а не через код: поиск группы в
 * меню приносит игроков на точку своего режима, а дезматч ловит всех, кто
 * оказался внутри круга своей арены. Поставьте обе точки в одно место —
 * карточка мини-игры в меню станет входом в дезматч. Классами моды друг о
 * друге не знают и обновляются независимо.
 *
 * Настройки: <папка плагина>/deathmatch.json, команда /dmadmin показывает
 * состояние и подсказывает остальные команды.
 */
public class DeathMatchPlugin extends JavaPlugin {

    private MatchService match;

    public DeathMatchPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ConfigStore configStore = new ConfigStore(ServerApi.dataDirectory(this, "deathmatch"));
        this.match = new MatchService(this.getLogger(), configStore);

        this.getCommandRegistry().registerCommand(new DmCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmLeaveCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmTopCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmAdminCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmSetArenaCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmGiveCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmReloadCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmResetCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmToggleCommand(this.match));
        this.getCommandRegistry().registerCommand(new DmDiagCommand(this.match));

        // Убийства приходят ECS-событием, обычным слушателем его не поймать.
        this.getEntityStoreRegistry().registerSystem(new KillSystem(this.match));

        // Кто зашёл на сервер — того опрашивает таймер арены.
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        this.match.start();

        this.getLogger().at(Level.INFO).log(
                "[Дезматч] Загружен. /dm — состояние, /dmadmin — настройки.");
    }

    @Override
    protected void shutdown() {
        if (this.match != null) {
            this.match.stop();
            this.match = null;
        }
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            Ref<EntityStore> ref = event.getPlayerRef();
            if (player == null || ref == null) {
                return;
            }
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef != null) {
                this.match.trackPlayer(playerRef);
            }
        } catch (RuntimeException | LinkageError problem) {
            // Дезматч — не повод ронять вход игрока на сервер.
            this.getLogger().at(Level.WARNING).log("[Дезматч] вход игрока: " + problem);
        }
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        try {
            this.match.untrackPlayer(event.getPlayerRef());
        } catch (RuntimeException | LinkageError problem) {
            this.getLogger().at(Level.WARNING).log("[Дезматч] выход игрока: " + problem);
        }
    }
}
