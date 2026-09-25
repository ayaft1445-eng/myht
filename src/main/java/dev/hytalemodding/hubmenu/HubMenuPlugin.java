package dev.hytalemodding.hubmenu;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.commands.HubCommand;
import dev.hytalemodding.hubmenu.groupfinder.GroupFinderService;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderAdminCommand;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderCommand;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderLeaveCommand;
import dev.hytalemodding.hubmenu.groupfinder.commands.GroupFinderStatusCommand;
import dev.hytalemodding.hubmenu.groupfinder.storage.ConfigStore;
import dev.hytalemodding.hubmenu.lang.LanguageStore;
import dev.hytalemodding.hubmenu.ui.LanguagePickerPage;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа плагина.
 *
 * Команда /hub открывает тёмное меню HUB с кнопками-карточками:
 * «Мини-игры», «Новости», «Правила» и «Язык». Каждая кнопка открывает своё окно
 * с кнопкой «Назад».
 *
 * При первом заходе на сервер открывается отдельное окно выбора языка —
 * пока игрок ничего не выбрал, его нет в languages.properties. К меню HUB
 * это окно не относится: /hub всегда открывает главное окно.
 *
 * Второй частью мода идёт поиск группы: /gf у игроков, /gfadmin у админов.
 * Карточки во вкладке «Мини-игры» открывают тот же поиск и помечают в нём
 * режим, привязанный к карточке.
 */
public class HubMenuPlugin extends JavaPlugin {

    private LanguageStore languages;
    private GroupFinderService groupFinder;

    public HubMenuPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.languages = new LanguageStore(this.getDataDirectory());

        ConfigStore configStore = new ConfigStore(ServerApi.dataDirectory(this, "hubmenu"));
        this.groupFinder = new GroupFinderService(this.getLogger(), configStore);

        this.getCommandRegistry().registerCommand(new HubCommand(this.languages, this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderCommand(this.groupFinder, this.languages));
        this.getCommandRegistry().registerCommand(new GroupFinderLeaveCommand(this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderStatusCommand(this.groupFinder));
        this.getCommandRegistry().registerCommand(new GroupFinderAdminCommand(this.groupFinder));
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        this.groupFinder.start();

        this.getLogger().at(Level.INFO).log(
                "[HubMenu] Loaded. /hub — меню, /gf — поиск группы, /gfadmin — настройки.");
    }

    /** Первый заход: язык ещё не выбран — показываем отдельное окно выбора. */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Player player = event.getPlayer();
            Ref<EntityStore> ref = event.getPlayerRef();
            if (player == null || ref == null) {
                return;
            }

            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null || this.languages.hasChosen(playerRef.getUuid())) {
                return;
            }

            player.getPageManager().openCustomPage(
                    ref,
                    ref.getStore(),
                    new LanguagePickerPage(playerRef, this.languages)
            );
        } catch (RuntimeException exception) {
            // Меню — не повод ронять вход игрока на сервер.
            this.getLogger().at(Level.WARNING).log("[HubMenu] Не удалось показать выбор языка: " + exception);
        }
    }

    @Override
    protected void shutdown() {
        if (this.groupFinder != null) {
            this.groupFinder.stop();
            this.groupFinder = null;
        }
    }
}
