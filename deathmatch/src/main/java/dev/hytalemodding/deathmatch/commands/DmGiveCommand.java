package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.Equipment;
import dev.hytalemodding.deathmatch.match.MatchService;
import dev.hytalemodding.deathmatch.model.Loadout;

import javax.annotation.Nonnull;

/**
 * /dmgive — выдать себе комплекты всех уровней по очереди и посмотреть,
 * какие идентификаторы предметов сервер знает, а какие нет.
 *
 * Это единственный надёжный способ проверить deathmatch.json: список
 * предметов на каждой сборке свой, и мод заранее его не знает.
 */
public class DmGiveCommand extends DmCommandBase {

    public DmGiveCommand(MatchService service) {
        super("dmgive", "Проверить комплекты уровней", service);
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

        for (Loadout level : this.service.config().getLevels()) {
            Equipment.Result result = Equipment.give(playerRef, level);
            say(context, level.describe() + " — "
                    + (result.isOk() ? "всё выдалось" : result.getMessage()));
        }
        say(context, "в инвентаре остался последний комплект. Поправьте deathmatch.json"
                + " и наберите /dmreload.");
    }
}
