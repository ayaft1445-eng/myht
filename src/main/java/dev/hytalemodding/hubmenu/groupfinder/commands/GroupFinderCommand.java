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
import dev.hytalemodding.hubmenu.groupfinder.ui.GroupFinderPage;
import dev.hytalemodding.hubmenu.lang.LanguageStore;
import dev.hytalemodding.hubmenu.lang.MenuLanguage;

import javax.annotation.Nonnull;

/** Команда /gf — открывает окно поиска группы. */
public class GroupFinderCommand extends AbstractPlayerCommand {

    private final GroupFinderService service;
    private final LanguageStore languages;

    public GroupFinderCommand(GroupFinderService service, LanguageStore languages) {
        super("gf", "Поиск группы для мини-игры");
        this.service = service;
        this.languages = languages;
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
            context.sendMessage(Message.raw("Не удалось открыть поиск: игрок не найден."));
            return;
        }

        player.getPageManager().openCustomPage(
                ref,
                store,
                new GroupFinderPage(
                        playerRef,
                        player.getPageManager(),
                        this.service,
                        world,
                        this.languages,
                        this.languages.get(playerRef.getUuid(),
                                MenuLanguage.fromCode(playerRef.getLanguage())),
                        false,
                        null
                )
        );
    }
}
