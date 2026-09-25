package dev.hytalemodding.hubmenu.groupfinder.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.groupfinder.GroupFinderService;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;
import dev.hytalemodding.hubmenu.groupfinder.model.GameModeConfig;

import javax.annotation.Nonnull;

/** Команда /gfstatus — что сейчас с очередями, без открытия окна. */
public class GroupFinderStatusCommand extends AbstractPlayerCommand {

    private final GroupFinderService service;

    public GroupFinderStatusCommand(GroupFinderService service) {
        super("gfstatus", "Состояние очередей поиска группы");
        this.service = service;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        StringBuilder text = new StringBuilder("[Поиск] ");
        text.append(this.service.statusLine(ServerApi.playerKey(playerRef)));

        for (GameModeConfig mode : this.service.config().getModes()) {
            if (!mode.isReady()) {
                continue;
            }
            text.append("\n  ").append(mode.getName())
                    .append(" — ").append(this.service.queueSize(mode.getId()))
                    .append("/").append(mode.getMinPlayers());
            int countdown = this.service.countdownLeft(mode.getId());
            if (countdown >= 0) {
                text.append(" (старт через ").append(countdown).append(" с)");
            }
        }

        context.sendMessage(Message.raw(text.toString()));
    }
}
