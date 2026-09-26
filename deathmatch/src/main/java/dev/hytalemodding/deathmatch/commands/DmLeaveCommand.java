package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;

/** /dmleave — выйти из боя, не уходя с арены ногами. */
public class DmLeaveCommand extends DmCommandBase {

    public DmLeaveCommand(MatchService service) {
        super("dmleave", "Выйти из боя", service);
    }

    @Override
    protected void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        boolean left = this.service.leave(keyOf(playerRef), true);
        if (!left) {
            say(context, "вы и так не в бою.");
            return;
        }
        say(context, "вы вышли из боя. Пока стоите на арене, бой начнётся снова —"
                + " отойдите за её край.");
    }
}
