package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.bridge.ServerApi;
import dev.hytalemodding.deathmatch.match.Equipment;
import dev.hytalemodding.deathmatch.match.MatchService;
import dev.hytalemodding.deathmatch.model.DeathMatchConfig;
import dev.hytalemodding.deathmatch.model.Loadout;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * /dmadmin — состояние настроек, список админских команд и первая настройка.
 *
 * Пока в файле нет ни одного админа, команда открыта всем и первый, кто её
 * наберёт, становится админом — так же, как «сделать себя админом» в панели
 * поиска группы. Дальше она только показывает состояние.
 */
public class DmAdminCommand extends DmCommandBase {

    public DmAdminCommand(MatchService service) {
        super("dmadmin", "Настройки дезматча", service);
    }

    @Override
    protected void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        DeathMatchConfig config = this.service.config();

        if (config.isSetupMode()) {
            config.addAdmin(ServerApi.username(playerRef));
            this.service.saveConfig();
            say(context, "вы стали админом дезматча. Список админов — в deathmatch.json.");
        } else if (!requireAdmin(context, playerRef)) {
            return;
        }

        say(context, "состояние: " + (config.isEnabled() ? "включён" : "выключен")
                + ", арена: " + config.getArena().describe()
                + ", радиус " + config.getRadius()
                + ", до победы " + (config.getGoalKills() == 0 ? "без предела" : config.getGoalKills())
                + ", бойцов " + this.service.fighterCount());

        say(context, "уровни:");
        boolean missing = false;
        for (Loadout level : config.getLevels()) {
            // Сразу показываем, знает ли сервер оружие уровня: без этого
            // непонятно, почему в руке пусто или висел знак вопроса.
            boolean ok = Equipment.exists(level.getWeapon());
            missing = missing || !ok;
            say(context, "  " + level.describe() + " — оружие " + shortId(level.getWeapon())
                    + (ok ? " (есть)" : " (сервер такого не знает)"));
        }
        if (missing) {
            say(context, "часть предметов сервер не знает — наберите /dmscan,"
                    + " он найдёт настоящие имена и сложит их в файл.");
        }

        say(context, "команды: /dmsetarena — точка арены здесь, /dmgive — проверить предметы,"
                + " /dmreload — перечитать файл, /dmreset — сбросить счёт, /dmtoggle — вкл/выкл,"
                + " /dmdiag — что видит мод.");
        say(context, "файл настроек: " + this.service.store().getFile());

        if (!this.service.store().getLastError().isEmpty()) {
            say(context, "внимание: " + this.service.store().getLastError());
        }

        Map<String, String> snapshot = this.service.debugSnapshot();
        if (!snapshot.isEmpty()) {
            say(context, "сейчас в бою:");
            for (Map.Entry<String, String> entry : snapshot.entrySet()) {
                say(context, "  " + entry.getKey() + " — " + entry.getValue());
            }
        }
    }

    private static String shortId(String itemId) {
        return itemId == null || itemId.isEmpty() ? "нет" : itemId;
    }
}
