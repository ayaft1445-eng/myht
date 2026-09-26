package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;

/** /dm — где я, сколько убил и сколько осталось до следующего комплекта. */
public class DmCommand extends DmCommandBase {

    public DmCommand(MatchService service) {
        super("dm", "Состояние дезматча", service);
    }

    @Override
    protected void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        say(context, this.service.statusLine(keyOf(playerRef)));
        say(context, "бойцов на арене: " + this.service.fighterCount()
                + ". Выйти — /dmleave, таблица — /dmtop.");
    }
}
