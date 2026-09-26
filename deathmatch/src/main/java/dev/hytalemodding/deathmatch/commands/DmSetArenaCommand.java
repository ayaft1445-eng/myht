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

/**
 * /dmsetarena — поставить точку арены там, где стоит админ.
 *
 * Координаты руками не набираются: это тот же приём, что и в панели
 * поиска группы у HubMenu, и он избавляет от ошибок со знаками.
 */
public class DmSetArenaCommand extends DmCommandBase {

    public DmSetArenaCommand(MatchService service) {
        super("dmsetarena", "Поставить точку арены здесь", service);
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

        double[] position = ServerApi.position(store, ref);
        if (position == null) {
            position = ServerApi.positionOf(playerRef);
        }
        if (position == null) {
            say(context, "не получилось прочитать ваши координаты — посмотрите /dmdiag.");
            return;
        }

        String worldName = ServerApi.worldName(world);
        this.service.config().getArena().setTo(
                worldName == null ? "" : worldName,
                position[0], position[1], position[2],
                position.length > 3 ? (float) position[3] : 0f,
                position.length > 4 ? (float) position[4] : 0f);

        if (this.service.saveConfig()) {
            say(context, "точка арены здесь: " + this.service.config().getArena().describe()
                    + ", радиус " + this.service.config().getRadius() + " блоков.");
            say(context, "поставьте ту же точку режиму в /gfadmin — и карточка мини-игры"
                    + " в меню HubMenu будет приводить игроков прямо сюда.");
        } else {
            say(context, "точка принята, но файл не сохранился: "
                    + this.service.store().getLastError());
        }
    }
}
