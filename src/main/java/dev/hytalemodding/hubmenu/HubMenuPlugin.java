package dev.hytalemodding.hubmenu;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.hubmenu.commands.HubCommand;
import dev.hytalemodding.hubmenu.groupfinder.GroupFinderService;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderAdminCommand;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderCommand;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderLeaveCommand;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderStatusCommand;
import dev.hytalemodding.hubmenu.groupfinder.storage.ConfigStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа плагина.
 *
 * Команда /hub открывает чёрно-белое меню HUB с четырьмя кнопками-карточками:
 * «Мини-игры», «Новости», «Правила» и «ДС сервер». Каждая кнопка открывает
 * своё окно с кнопкой «Назад».
 *
 * Второй частью мода идёт поиск группы: /gf у игроков, /gfadmin у админов.
 * Карточки во вкладке «Мини-игры» ставят игрока в очередь того режима,
 * который к карточке привязан.
 */
public class HubMenuPlugin extends JavaPlugin {

    private GroupFinderService groupFinder;

    public HubMenuPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ConfigStore configStore = new ConfigStore(ServerApi.dataDirectory(this, "hubmenu"));
        this.groupFinder = new GroupFinderService(this.getLogger(), configStore);

        this.getCommandRegistry().registerCommand(new HubCommand(this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderCommand(this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderLeaveCommand(this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderStatusCommand(this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderAdminCommand(this.groupFinder));

        this.groupFinder.start();

        this.getLogger().at(Level.INFO).log(
                "[HubMenu] Loaded. /hub — меню, /gf — поиск группы, /gfadmin — настройки.");
    }

    @Override
    protected void shutdown() {
        if (this.groupFinder != null) {
            this.groupFinder.stop();
            this.groupFinder = null;
        }
    }
}
