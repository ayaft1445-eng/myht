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
import dev.hytalemodding.deathmatch.model.Loadout;

import javax.annotation.Nonnull;

/**
 * /dmsimple — вернуть стандартные настройки боя.
 *
 * Лестница из четырёх мечей (0, 15, 40 и 70 убийств) и матч до ста.
 * Нужна, когда файл настроек уже создан: обновление мода чужой
 * deathmatch.json не переписывает, поэтому старые ступени остаются на
 * месте. Команда меняет только ступени и цель матча — арена, радиус и
 * список админов остаются как были.
 */
public class DmSimpleCommand extends DmCommandBase {

    public DmSimpleCommand(MatchService service) {
        super("dmsimple", "Стандартные настройки: лестница мечей до ста убийств", service);
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

        say(context, "стандартные настройки включены, матч до "
                + DeathMatchConfig.DEFAULT_GOAL + " убийств. Ступени:");

        StringBuilder missing = new StringBuilder();
        for (Loadout level : this.service.config().getLevels()) {
            boolean ok = Equipment.exists(level.getWeapon());
            say(context, "  с " + level.getKills() + " убийств — " + level.getName()
                    + " (" + level.getWeapon() + ")" + (ok ? "" : " — сервер не знает!"));
            if (!ok) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(level.getWeapon());
            }
        }
        if (missing.length() > 0) {
            say(context, "проверьте имена через /dmscan: " + missing);
        }
        if (!saved) {
            say(context, "файл настроек не сохранился: " + this.service.store().getLastError());
        }
    }
}
