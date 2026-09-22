package dev.hytalemodding.hubmenu.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.ui.HubMenuPage;

import javax.annotation.Nonnull;

/** Команда /hub — открывает меню HUB. */
public class HubCommand extends AbstractPlayerCommand {

    public HubCommand() {
        super("hub", "Открыть меню сервера");
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
            context.sendMessage(Message.raw("Не удалось открыть меню: игрок не найден."));
            return;
        }

        player.getPageManager().openCustomPage(
                ref,
                store,
                new HubMenuPage(playerRef, player.getPageManager(), HubMenuPage.SECTION_MAIN)
        );
    }
}
