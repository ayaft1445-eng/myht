package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;

/** /dmreset — обнулить счёт матча и вернуть всем стартовый комплект. */
public class DmResetCommand extends DmCommandBase {

    public DmResetCommand(MatchService service) {
        super("dmreset", "Сбросить счёт матча", service);
    }

    @Override
    protected void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!requireAdmin(context, playerRef)) {
            return;
        }
        this.service.resetMatch();
        say(context, "счёт сброшен, бойцов на арене: " + this.service.fighterCount() + ".");
    }
}
