package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.Equipment;
import dev.hytalemodding.deathmatch.match.MatchService;
import dev.hytalemodding.deathmatch.model.DeathMatchConfig;

import javax.annotation.Nonnull;

/**
 * /dmsimple — вернуть простой режим: один меч и матч до ста убийств.
 *
 * Нужна, когда файл настроек уже создан: обновление мода чужой
 * deathmatch.json не переписывает, поэтому старые уровни с выдуманными
 * предметами остаются на месте. Команда меняет только уровни и цель матча —
 * арена, радиус и список админов остаются как были.
 */
public class DmSimpleCommand extends DmCommandBase {

    public DmSimpleCommand(MatchService service) {
        super("dmsimple", "Простой режим: один меч, сто убийств", service);
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

        this.service.config().applySimplePreset();
        boolean saved = this.service.saveConfig();
        this.service.reequipEveryone();

        say(context, "простой режим включён: у всех " + DeathMatchConfig.DEFAULT_WEAPON
                + ", матч до " + DeathMatchConfig.DEFAULT_GOAL + " убийств.");
        if (!Equipment.exists(DeathMatchConfig.DEFAULT_WEAPON)) {
            say(context, "внимание: сервер не знает такой предмет — проверьте имя через /dmscan.");
        }
        if (!saved) {
            say(context, "файл настроек не сохранился: " + this.service.store().getLastError());
        }
    }
}
