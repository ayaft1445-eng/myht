package dev.hytalemodding.serverhub.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Меню сервера.
 *
 * Одна и та же страница показывает либо главное окно с тремя кнопками
 * (section = SECTION_MAIN), либо окно одного раздела (section = 0, 1, 2).
 * По нажатию кнопки игроку открывается эта же страница с другим номером
 * раздела — поэтому каждое окно всегда собирается заново и целиком.
 *
 * Разметка: src/main/resources/Common/UI/Custom/ServerHub/
 */
public class HubPage extends InteractiveCustomUIPage<HubPage.HubEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Главное окно с тремя кнопками. */
    public static final int SECTION_MAIN = -1;

    private static final String MAIN_LAYOUT = "ServerHub/Main.ui";

    /** Окна разделов — порядок совпадает с порядком кнопок в главном окне. */
    private static final String[] SECTION_LAYOUTS = {
            "ServerHub/Section_Rules.ui",
            "ServerHub/Section_Minigames.ui",
            "ServerHub/Section_Discord.ui"
    };

    /** Кнопки главного окна — порядок совпадает с SECTION_LAYOUTS. */
    private static final String[] SECTION_BUTTONS = {
            "#BtnRules",
            "#BtnMinigames",
            "#BtnDiscord"
    };

    private static final String ACTION_OPEN_PREFIX = "open";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    private final PlayerRef playerRef;
    private final PageManager pageManager;
    private final int section;

    public HubPage(@Nonnull PlayerRef playerRef, @Nonnull PageManager pageManager, int section) {
        super(playerRef, CustomPageLifetime.CanDismiss, HubEventData.CODEC);
        this.playerRef = playerRef;
        this.pageManager = pageManager;
        this.section = isSection(section) ? section : SECTION_MAIN;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        if (this.section == SECTION_MAIN) {
            cmd.append(MAIN_LAYOUT);

            for (int i = 0; i < SECTION_BUTTONS.length; i++) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        SECTION_BUTTONS[i],
                        new EventData().append("Action", ACTION_OPEN_PREFIX + i),
                        false
                );
            }
        } else {
            cmd.append(SECTION_LAYOUTS[this.section]);

            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#BackButton",
                    new EventData().append("Action", ACTION_BACK),
                    false
            );
        }

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", ACTION_CLOSE),
                false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull HubEventData data
    ) {
        String action = data.getAction();
        LOGGER.at(Level.INFO).log("[ServerHub] menu event: " + action);

        if (action == null) {
            return;
        }

        if (ACTION_CLOSE.equals(action)) {
            this.close();
            return;
        }

        if (ACTION_BACK.equals(action)) {
            this.openSection(ref, store, SECTION_MAIN);
            return;
        }

        if (action.startsWith(ACTION_OPEN_PREFIX)) {
            int requested = parseSection(action);
            if (isSection(requested)) {
                this.openSection(ref, store, requested);
            }
        }
    }

    /** Открывает игроку эту же страницу с другим разделом. */
    private void openSection(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            int newSection
    ) {
        this.pageManager.openCustomPage(ref, store, new HubPage(this.playerRef, this.pageManager, newSection));
    }

    private static boolean isSection(int value) {
        return value >= 0 && value < SECTION_LAYOUTS.length;
    }

    private static int parseSection(@Nonnull String action) {
        try {
            return Integer.parseInt(action.substring(ACTION_OPEN_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return SECTION_MAIN;
        }
    }

    /** Данные, которые клиент присылает при нажатии на кнопку. */
    public static class HubEventData {

        public static final BuilderCodec<HubEventData> CODEC = BuilderCodec
                .builder(HubEventData.class, HubEventData::new)
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (eventData, value, extraInfo) -> eventData.action = value,
                        (eventData, extraInfo) -> eventData.action
                )
                .add()
                .build();

        private String action;

        public HubEventData() {
        }

        public String getAction() {
            return this.action;
        }
    }
}
