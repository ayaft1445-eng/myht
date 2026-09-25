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

import javax.annotation.Nonnull;

/** Команда /gfleave — выйти из очереди, не открывая окно. */
public class GroupFinderLeaveCommand extends AbstractPlayerCommand {

    private final GroupFinderService service;

    public GroupFinderLeaveCommand(GroupFinderService service) {
        super("gfleave", "Выйти из очереди поиска группы");
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
        boolean left = this.service.leave(ServerApi.playerKey(playerRef));
        context.sendMessage(Message.raw(left
                ? "[Поиск] вы вышли из очереди."
                : "[Поиск] вы не в очереди."));
    }
}
