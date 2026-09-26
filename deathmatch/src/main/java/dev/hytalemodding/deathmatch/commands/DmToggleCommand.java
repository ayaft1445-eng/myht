package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;

/** /dmtoggle — включить или выключить дезматч целиком. */
public class DmToggleCommand extends DmCommandBase {

    public DmToggleCommand(MatchService service) {
        super("dmtoggle", "Включить или выключить дезматч", service);
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

        boolean enabled = !this.service.config().isEnabled();
        this.service.config().setEnabled(enabled);
        this.service.saveConfig();
        say(context, enabled ? "дезматч включён." : "дезматч выключен, арена больше не ловит игроков.");
    }
}
