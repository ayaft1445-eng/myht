package dev.hytalemodding.hubmenu.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hubmenu.groupfinder.GroupFinderService;
import dev.hytalemodding.hubmenu.groupfinder.bridge.ServerApi;
import dev.hytalemodding.hubmenu.groupfinder.model.GameModeConfig;
import dev.hytalemodding.hubmenu.groupfinder.ui.GroupFinderPage;
import dev.hytalemodding.hubmenu.lang.LanguageStore;
import dev.hytalemodding.hubmenu.lang.MenuLanguage;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Меню HUB в тёмно-фиолетовом стиле.
 *
 * Одна страница показывает либо главное окно с четырьмя кнопками-карточками
 * (section = SECTION_MAIN), либо окно одного раздела. По нажатию кнопки игроку
 * открывается эта же страница с другим номером раздела, поэтому каждое окно
 * собирается заново и целиком.
 *
 * Язык. Разметка на каждый язык лежит отдельным файлом (Main_ru.ui, Main_en.ui),
 * страница берёт нужный по MenuLanguage. Окно выбора языка одно на оба языка:
 * его видят и те, кто ещё ничего не выбрал.
 *
 * Анимация нажатия. Плавных переходов в разметке Hytale нет, поэтому кнопку
 * «проигрывает» сервер: под каждой анимированной кнопкой лежат два скрытых
 * кадра (#Fx<кнопка>A — нажатие, #Fx<кнопка>B — вспышка). По нажатию сервер
 * включает их по очереди (A → B → A) и только потом открывает новое окно.
 * Тайминги — в PRESS_FRAMES и PRESS_FINISH_MS.
 *
 * Разметка: src/main/resources/Common/UI/Custom/HubMenu/
 */
public class HubMenuPage extends InteractiveCustomUIPage<HubMenuPage.HubEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Главное окно с карточками. */
    public static final int SECTION_MAIN = -1;

    /** Окно выбора языка — оно же четвёртая карточка главного окна. */
    public static final int SECTION_LANGUAGE = 3;

    private static final String MAIN_WINDOW = "Main";

    /** Окна разделов — порядок совпадает с порядком карточек в главном окне. */
    private static final String[] SECTION_WINDOWS = {
            "Section_Minigames",
            "Section_News",
            "Section_Rules"
    };

    /** Окно выбора языка общее: подписи в нём сразу на двух языках. */
    private static final String LANGUAGE_LAYOUT = "HubMenu/Section_Language.ui";

    /** Кнопки главного окна — порядок совпадает с номерами разделов. */
    private static final String[] SECTION_BUTTONS = {
            "#BtnMinigames",
            "#BtnNews",
            "#BtnRules",
            "#BtnLanguage"
    };

    /** Раздел «Мини-игры»: из него открывается поиск группы. */
    public static final int SECTION_MINIGAMES = 0;

    /** Кнопки режимов во вкладке мини-игр. */
    private static final String[] MODE_BUTTONS = { "#Mode0", "#Mode1", "#Mode2", "#Mode3" };

    /** Кнопка «Закрыть» в главном окне. */
    private static final String CLOSE_BUTTON = "#CloseButton";

    private static final String ACTION_OPEN_PREFIX = "open";
    private static final String ACTION_MODE_PREFIX = "mode";
    private static final String ACTION_LANG_PREFIX = "lang";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";

    /** Кнопка «Назад» в окнах разделов. */
    private static final String BACK_BUTTON = "#BackButton";

    /** Кадры анимации нажатия: какой слой включить и через сколько мс. */
    private static final String[] PRESS_FRAMES = { "A", "B", "A" };
    private static final long[] PRESS_FRAME_MS = { 0L, 70L, 140L };

    /** Через сколько мс после нажатия открывается следующее окно. */
    private static final long PRESS_FINISH_MS = 220L;

    /** Общий таймер кадров для всех игроков: задачи короткие, потока хватит одного. */
    private static final ScheduledExecutorService ANIMATION_TIMER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "HubMenu-Animation");
                thread.setDaemon(true);
                return thread;
            });

    private final PlayerRef playerRef;
    private final PageManager pageManager;
    private final LanguageStore languages;
    private final MenuLanguage language;
    private final int section;
    private final GroupFinderService groupFinder;
    private final World world;

    /** Идёт анимация нажатия — повторные нажатия в это время игнорируются. */
    private volatile boolean animating;

    public HubMenuPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull PageManager pageManager,
            @Nonnull LanguageStore languages,
            @Nonnull MenuLanguage language,
            int section,
            @Nonnull GroupFinderService groupFinder,
            @Nonnull World world
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, HubEventData.CODEC);
        this.playerRef = playerRef;
        this.pageManager = pageManager;
        this.languages = languages;
        this.language = language;
        this.section = isSection(section) ? section : SECTION_MAIN;
        this.groupFinder = groupFinder;
        this.world = world;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        if (this.section == SECTION_MAIN) {
            cmd.append(this.language.layout(MAIN_WINDOW));

            for (int i = 0; i < SECTION_BUTTONS.length; i++) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        SECTION_BUTTONS[i],
                        new EventData().append("Action", ACTION_OPEN_PREFIX + i),
                        false
                );
            }

            // Чипы языка в строке настроек: те же кнопки, что и в окне выбора
            // языка, поэтому язык переключается прямо из главного окна.
            for (MenuLanguage option : MenuLanguage.values()) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        option.getButtonId(),
                        new EventData().append("Action", ACTION_LANG_PREFIX + option.getCode()),
                        false
                );
            }

            // Большая кнопка «Закрыть» внизу главного окна. ESC тоже закрывает.
            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    CLOSE_BUTTON,
                    new EventData().append("Action", ACTION_CLOSE),
                    false
            );
            return;
        }

        if (this.section == SECTION_LANGUAGE) {
            cmd.append(LANGUAGE_LAYOUT);

            for (MenuLanguage option : MenuLanguage.values()) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        option.getButtonId(),
                        new EventData().append("Action", ACTION_LANG_PREFIX + option.getCode()),
                        false
                );
            }
        } else {
            cmd.append(this.language.layout(SECTION_WINDOWS[this.section]));

            if (this.section == SECTION_MINIGAMES) {
                for (int i = 0; i < MODE_BUTTONS.length; i++) {
                    evt.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            MODE_BUTTONS[i],
                            new EventData().append("Action", ACTION_MODE_PREFIX + i),
                            false
                    );
                }
            }
        }

        // Кнопка «Назад» вверху слева, кнопки «Закрыть» нет: закрывает ESC
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BackButton",
                new EventData().append("Action", ACTION_BACK),
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
        LOGGER.at(Level.INFO).log("[HubMenu] menu event: " + action);

        if (action == null || this.animating) {
            return;
        }

        String pressed = pressedButton(action);
        if (pressed != null) {
            playPress(pressed, () -> handleAction(ref, store, action));
            return;
        }

        handleAction(ref, store, action);
    }

    /** Выполняет действие кнопки: открыть раздел, сменить язык, закрыть. */
    private void handleAction(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull String action
    ) {
        if (ACTION_CLOSE.equals(action)) {
            this.close();
            return;
        }

        if (action.startsWith(ACTION_LANG_PREFIX)) {
            MenuLanguage chosen = MenuLanguage.fromCode(action.substring(ACTION_LANG_PREFIX.length()));
            this.languages.set(this.playerRef.getUuid(), chosen);
            this.playerRef.sendMessage(Message.raw(languageChanged(chosen)));
            this.open(ref, store, chosen, SECTION_MAIN);
            return;
        }

        if (action.startsWith(ACTION_MODE_PREFIX)) {
            openGroupFinder(ref, store, parseIndex(action, ACTION_MODE_PREFIX));
            return;
        }

        if (ACTION_BACK.equals(action)) {
            this.open(ref, store, this.language, SECTION_MAIN);
            return;
        }

        if (action.startsWith(ACTION_OPEN_PREFIX)) {
            int requested = parseSection(action);
            if (isSection(requested)) {
                this.open(ref, store, this.language, requested);
            }
        }
    }

    /**
     * Какую кнопку анимировать для этого действия. null — у кнопки нет кадров
     * анимации (чипы языка в главном окне, «Закрыть»), действие выполняется сразу.
     */
    private String pressedButton(@Nonnull String action) {
        if (ACTION_BACK.equals(action)) {
            return this.section == SECTION_MAIN ? null : BACK_BUTTON;
        }
        if (action.startsWith(ACTION_OPEN_PREFIX) && this.section == SECTION_MAIN) {
            int index = parseIndex(action, ACTION_OPEN_PREFIX);
            return index >= 0 && index < SECTION_BUTTONS.length ? SECTION_BUTTONS[index] : null;
        }
        if (action.startsWith(ACTION_MODE_PREFIX) && this.section == SECTION_MINIGAMES) {
            int index = parseIndex(action, ACTION_MODE_PREFIX);
            return index >= 0 && index < MODE_BUTTONS.length ? MODE_BUTTONS[index] : null;
        }
        if (action.startsWith(ACTION_LANG_PREFIX) && this.section == SECTION_LANGUAGE) {
            return MenuLanguage.fromCode(action.substring(ACTION_LANG_PREFIX.length())).getButtonId();
        }
        return null;
    }

    /**
     * Проигрывает анимацию нажатия кнопки и затем выполняет finish.
     * Кадры включаются через sendUpdate, всё — в потоке мира.
     */
    private void playPress(@Nonnull String button, @Nonnull Runnable finish) {
        this.animating = true;
        String fx = "#Fx" + button.substring(1);

        for (int i = 0; i < PRESS_FRAMES.length; i++) {
            String frame = PRESS_FRAMES[i];
            later(PRESS_FRAME_MS[i], () -> showFrame(fx, frame));
        }

        later(PRESS_FINISH_MS, () -> {
            try {
                finish.run();
            } finally {
                this.animating = false;
            }
        });
    }

    /** Включает один кадр (A или B) и гасит остальные. */
    private void showFrame(@Nonnull String fx, @Nonnull String frame) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(fx + "A.Visible", "A".equals(frame));
        cmd.set(fx + "B.Visible", "B".equals(frame));
        this.sendUpdate(cmd, false);
    }

    /** Запускает задачу в потоке мира через delayMs миллисекунд. */
    private void later(long delayMs, @Nonnull Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                this.animating = false;
                LOGGER.at(Level.WARNING).log("[HubMenu] press animation failed: " + throwable);
            }
        };
        Runnable onWorld = () -> {
            if (!ServerApi.runOnWorldThread(this.world, safe)) {
                safe.run();
            }
        };
        if (delayMs <= 0) {
            safe.run();
        } else {
            ANIMATION_TIMER.schedule(onWorld, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Нажали карточку режима во вкладке «Мини-игры»: открываем поиск группы
     * со списком всех режимов. В очередь сразу не ставим — игрок выбирает сам,
     * а режим этой карточки в списке помечен стрелками.
     */
    private void openGroupFinder(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            int card
    ) {
        GameModeConfig mode = card < 0 ? null : this.groupFinder.config().findByCard(card);
        this.pageManager.openCustomPage(ref, store, new GroupFinderPage(
                this.playerRef,
                this.pageManager,
                this.groupFinder,
                this.world,
                this.languages,
                this.language,
                true,
                mode == null ? null : mode.getId()
        ));
    }

    /** Открывает игроку это же меню с другим разделом или языком. */
    private void open(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MenuLanguage newLanguage,
            int newSection
    ) {
        this.pageManager.openCustomPage(
                ref,
                store,
                new HubMenuPage(this.playerRef, this.pageManager, this.languages, newLanguage,
                        newSection, this.groupFinder, this.world)
        );
    }

    @Nonnull
    private static String languageChanged(@Nonnull MenuLanguage language) {
        return language == MenuLanguage.EN
                ? "Menu language: English."
                : "Язык меню: русский.";
    }

    private static boolean isSection(int value) {
        return value >= 0 && value <= SECTION_LANGUAGE;
    }

    private static int parseSection(@Nonnull String action) {
        return parseIndex(action, ACTION_OPEN_PREFIX);
    }

    private static int parseIndex(@Nonnull String action, @Nonnull String prefix) {
        try {
            return Integer.parseInt(action.substring(prefix.length()));
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
