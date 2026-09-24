package dev.hytalemodding.hubmenu.groupfinder.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.groupfinder.GroupFinderService;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;
import dev.hytalemodding.hubmenu.groupfinder.ui.AdminPage;

import javax.annotation.Nonnull;

/** Команда /gfadmin — панель настроек поиска группы. */
public class GroupFinderAdminCommand extends AbstractPlayerCommand {

    private final GroupFinderService service;

    public GroupFinderAdminCommand(GroupFinderService service) {
        super("gfadmin", "Настройки поиска группы");
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
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Не удалось открыть панель: игрок не найден."));
            return;
        }

        if (!this.service.isAdmin(player, ServerApi.username(playerRef))) {
            context.sendMessage(Message.raw(
                    "Панель настроек только для админов (право " + GroupFinderService.ADMIN_PERMISSION + ")."));
            return;
        }

        player.getPageManager().openCustomPage(
                ref,
                store,
                new AdminPage(playerRef, player.getPageManager(), this.service, world, null)
        );
    }
}
