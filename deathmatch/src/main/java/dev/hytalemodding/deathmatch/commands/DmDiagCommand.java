package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.bridge.ServerApi;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * /dmdiag — что мод нашёл в серверном API на этой сборке.
 *
 * Нужна, когда «не выдаётся оружие» или «не считаются убийства»: строка
 * диагностики сразу показывает, какой вызов не нашёлся.
 */
public class DmDiagCommand extends DmCommandBase {

    public DmDiagCommand(MatchService service) {
        super("dmdiag", "Диагностика дезматча", service);
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

        double[] direct = ServerApi.position(store, ref);
        double[] viaRef = ServerApi.positionOf(playerRef);
        say(context, "координаты через store: " + describe(direct));
        say(context, "координаты через PlayerRef: " + describe(viaRef));
        say(context, "мир: " + ServerApi.worldName(world));

        List<String> lines = ServerApi.diagnostics();
        for (String line : lines) {
            say(context, line);
        }
    }

    private static String describe(double[] position) {
        if (position == null) {
            return "не читаются";
        }
        return Math.round(position[0]) + " / " + Math.round(position[1]) + " / " + Math.round(position[2]);
    }
}
