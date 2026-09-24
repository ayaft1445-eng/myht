package dev.hytalemodding.hubmenu;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.commands.HubCommand;
import dev.hytalemodding.hubmenu.lang.LanguageStore;
import dev.hytalemodding.hubmenu.lang.MenuLanguage;
import dev.hytalemodding.hubmenu.ui.HubMenuPage;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа плагина.
 *
 * Команда /hub открывает тёмное меню HUB с кнопками-карточками:
 * «Мини-игры», «Новости», «Правила» и «Язык». Каждая кнопка открывает своё окно
 * с кнопкой «Назад».
 *
 * При первом заходе на сервер игроку сразу показывается окно выбора языка —
 * пока он ничего не выбрал, его нет в languages.properties.
 */
public class HubMenuPlugin extends JavaPlugin {

    private LanguageStore languages;

    public HubMenuPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.languages = new LanguageStore(this.getDataDirectory());

        this.getCommandRegistry().registerCommand(new HubCommand(this.languages));
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        this.getLogger().at(Level.INFO).log("[HubMenu] Loaded. Type /hub in chat to open the menu.");
    }

    /** Первый заход: язык ещё не выбран — показываем окно выбора. */
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
                    new HubMenuPage(
                            playerRef,
                            player.getPageManager(),
                            this.languages,
                            MenuLanguage.fromCode(playerRef.getLanguage()),
                            HubMenuPage.SECTION_LANGUAGE
                    )
            );
        } catch (RuntimeException exception) {
            // Меню — не повод ронять вход игрока на сервер.
            this.getLogger().at(Level.WARNING).log("[HubMenu] Не удалось показать выбор языка: " + exception);
        }
    }

}
