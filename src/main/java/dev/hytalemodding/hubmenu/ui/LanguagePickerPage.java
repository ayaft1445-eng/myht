package dev.hytalemodding.hubmenu.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.lang.LanguageStore;
import dev.hytalemodding.hubmenu.lang.MenuLanguage;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Отдельное окно выбора языка при первом заходе на сервер.
 *
 * Это не часть меню HUB: окно меньше, кнопки «Назад» нет, после выбора оно
 * просто закрывается. Меню HUB открывается командой /hub и всегда начинается
 * с главного окна.
 *
 * Разметка: Common/UI/Custom/HubMenu/Language_Start.ui
 */
public class LanguagePickerPage extends InteractiveCustomUIPage<HubMenuPage.HubEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String LAYOUT = "HubMenu/Language_Start.ui";
    private static final String ACTION_LANG_PREFIX = "lang";

    private final PlayerRef playerRef;
    private final LanguageStore languages;

    public LanguagePickerPage(@Nonnull PlayerRef playerRef, @Nonnull LanguageStore languages) {
        super(playerRef, CustomPageLifetime.CanDismiss, HubMenuPage.HubEventData.CODEC);
        this.playerRef = playerRef;
        this.languages = languages;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        for (MenuLanguage option : MenuLanguage.values()) {
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    option.getButtonId(),
                    new EventData().append("Action", ACTION_LANG_PREFIX + option.getCode()),
                    false
            );
        }
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull HubMenuPage.HubEventData data
    ) {
        String action = data.getAction();
        LOGGER.at(Level.INFO).log("[HubMenu] language event: " + action);

        if (action == null || !action.startsWith(ACTION_LANG_PREFIX)) {
            return;
        }

        MenuLanguage chosen = MenuLanguage.fromCode(action.substring(ACTION_LANG_PREFIX.length()));
        this.languages.set(this.playerRef.getUuid(), chosen);
        this.playerRef.sendMessage(Message.raw(chosen == MenuLanguage.EN
                ? "Menu language: English. Type /hub to open the menu."
                : "Язык меню: русский. Меню открывается командой /hub."));
        this.close();
    }
}
