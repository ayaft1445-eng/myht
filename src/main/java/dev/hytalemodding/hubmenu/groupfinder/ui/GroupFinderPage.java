package dev.hytalemodding.hubmenu.groupfinder.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import dev.hytalemodding.hubmenu.lang.LanguageStore;
import dev.hytalemodding.hubmenu.lang.MenuLanguage;
import dev.hytalemodding.hubmenu.ui.HubMenuPage;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Окно поиска группы для игрока.
 *
 * Показывает строки режимов, у каждой — кнопка «встать в очередь» или
 * «выйти». Окно не обновляется само: после каждого нажатия оно собирается
 * заново, как и остальные окна этого мода.
 *
 * Весь текст окна ставит сервер, включая заголовок и подписи кнопок —
 * поэтому разметка одна на оба языка. Текст сидит на TextButton нарочно:
 * ID на Label клиент в рабочих окнах не ждёт, а ошибка разбора рвёт
 * соединение.
 *
 * Разметка: Common/UI/Custom/HubMenu/GroupFinder.ui
 */
public class GroupFinderPage extends InteractiveCustomUIPage<GroupFinderPage.FinderEventData> {

    private static final String LAYOUT = "HubMenu/GroupFinder.ui";
    /** Сколько строк режимов есть в разметке. */
    public static final int ROWS = 6;

    private static final String ACTION_JOIN = "join";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_REFRESH = "refresh";
    private static final String ACTION_LEAVE = "leave";

    private final PlayerRef playerRef;
    private final PageManager pageManager;
    private final GroupFinderService service;
    private final World world;
    private final LanguageStore languages;
    private final MenuLanguage language;
    /** Пришли из меню — тогда «НАЗАД» возвращает в раздел «Мини-игры», а не закрывает всё. */
    private final boolean fromMenu;
    /** Режим карточки, с которой пришли: он помечен в списке. null — ниоткуда. */
    private final String highlightModeId;

    public GroupFinderPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull PageManager pageManager,
            @Nonnull GroupFinderService service,
            @Nonnull World world,
            @Nonnull LanguageStore languages,
            @Nonnull MenuLanguage language,
            boolean fromMenu,
            String highlightModeId
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, FinderEventData.CODEC);
        this.playerRef = playerRef;
        this.pageManager = pageManager;
        this.service = service;
        this.world = world;
        this.languages = languages;
        this.language = language;
        this.fromMenu = fromMenu;
        this.highlightModeId = highlightModeId;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        // Разметку грузим первой: без неё все set ниже целятся в пустоту.
        cmd.append(LAYOUT);

        boolean en = this.language == MenuLanguage.EN;
        String playerKey = ServerApi.playerKey(this.playerRef);
        String queuedMode = this.service.queuedModeId(playerKey);
        List<GameModeConfig> modes = this.service.config().getModes();

        cmd.set("#Title.Text", en ? "GROUP FINDER" : "ПОИСК ГРУППЫ");
        cmd.set("#BackButton.Text", en ? "BACK" : "НАЗАД");
        cmd.set("#RefreshButton.Text", en ? "REFRESH" : "ОБНОВИТЬ");
        cmd.set("#LeaveButton.Text", en ? "LEAVE QUEUE" : "ВЫЙТИ ИЗ ОЧЕРЕДИ");
        cmd.set("#Hint.Text", en ? "ESC — CLOSE" : "ESC — ЗАКРЫТЬ");
        cmd.set("#Status.Text", statusText(queuedMode));

        for (int row = 0; row < ROWS; row++) {
            if (row >= modes.size()) {
                cmd.set("#Name" + row + ".Text", "");
                cmd.set("#Info" + row + ".Text", "");
                cmd.set("#Join" + row + ".Text", "");
                continue;
            }

            GameModeConfig mode = modes.get(row);
            boolean here = mode.getId().equals(queuedMode);
            boolean chosen = mode.getId().equals(this.highlightModeId);

            cmd.set("#Name" + row + ".Text", chosen ? "> " + mode.getName() + " <" : mode.getName());
            cmd.set("#Info" + row + ".Text", infoText(mode));
            cmd.set("#Join" + row + ".Text", buttonText(mode, here));

            if (mode.isReady() || here) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        "#Join" + row,
                        new EventData().append("Action", ACTION_JOIN + row),
                        false
                );
            }
        }

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
                new EventData().append("Action", ACTION_BACK), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
                new EventData().append("Action", ACTION_REFRESH), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#LeaveButton",
                new EventData().append("Action", ACTION_LEAVE), false);
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull FinderEventData data
    ) {
        String action = data.getAction();
        if (action == null) {
            return;
        }

        boolean en = this.language == MenuLanguage.EN;

        if (ACTION_BACK.equals(action)) {
            if (this.fromMenu) {
                this.pageManager.openCustomPage(ref, store, new HubMenuPage(
                        this.playerRef,
                        this.pageManager,
                        this.languages,
                        this.language,
                        HubMenuPage.SECTION_MINIGAMES,
                        this.service,
                        this.world
                ));
            } else {
                this.close();
            }
            return;
        }

        if (ACTION_REFRESH.equals(action)) {
            reopen(ref, store);
            return;
        }

        if (ACTION_LEAVE.equals(action)) {
            boolean left = this.service.leave(ServerApi.playerKey(this.playerRef));
            if (left) {
                say(en ? "you left the queue." : "вы вышли из очереди.");
            } else {
                say(en ? "you are not in a queue." : "вы и так не в очереди.");
            }
            reopen(ref, store);
            return;
        }

        if (action.startsWith(ACTION_JOIN)) {
            int row = number(action.substring(ACTION_JOIN.length()));
            List<GameModeConfig> modes = this.service.config().getModes();
            if (row < 0 || row >= modes.size()) {
                return;
            }
            GameModeConfig mode = modes.get(row);
            String playerKey = ServerApi.playerKey(this.playerRef);

            if (mode.getId().equals(this.service.queuedModeId(playerKey))) {
                this.service.leave(playerKey);
                say(en
                        ? "you left the queue for " + mode.getName() + "."
                        : "вы вышли из очереди «" + mode.getName() + "».");
            } else {
                GroupFinderService.JoinResult result =
                        this.service.join(mode.getId(), this.playerRef, ref, store, this.world);
                // Удачную постановку в очередь пишем сами — она разная на двух
                // языках. Отказы берём у сервиса: там разбор причины.
                if (result.isQueued() && en) {
                    say("you joined the queue for " + mode.getName() + ".");
                } else {
                    say(result.getMessage());
                }
            }
            reopen(ref, store);
        }
    }

    // ------------------------------------------------------------- тексты

    private String statusText(String queuedMode) {
        boolean en = this.language == MenuLanguage.EN;

        if (!this.service.config().isEnabled()) {
            return en ? "GROUP FINDER IS OFF" : "ПОИСК ГРУППЫ ВЫКЛЮЧЕН";
        }

        if (queuedMode == null) {
            GameModeConfig chosen = this.highlightModeId == null
                    ? null
                    : this.service.config().findMode(this.highlightModeId);
            if (chosen != null) {
                return en
                        ? "MODE SELECTED: " + chosen.getName() + " — PRESS JOIN QUEUE"
                        : "ВЫБРАН РЕЖИМ: " + chosen.getName() + " — НАЖМИТЕ «ВСТАТЬ В ОЧЕРЕДЬ»";
            }
            return en ? "YOU ARE NOT QUEUED — PICK A MODE" : "ВЫ НЕ В ОЧЕРЕДИ — ВЫБЕРИТЕ РЕЖИМ";
        }

        // Строку очереди собираем сами из чисел: так она есть на обоих языках.
        GameModeConfig mode = this.service.config().findMode(queuedMode);
        String name = mode == null ? queuedMode : mode.getName();
        int size = this.service.queueSize(queuedMode);
        int need = mode == null ? 0 : mode.getMinPlayers();
        int countdown = this.service.countdownLeft(queuedMode);

        StringBuilder text = new StringBuilder();
        if (en) {
            text.append("IN QUEUE · ").append(name).append(" · ").append(size).append(" OF ").append(need);
            if (countdown >= 0) {
                text.append(" · START IN ").append(countdown).append(" S");
            }
        } else {
            text.append("ВЫ В ОЧЕРЕДИ · ").append(name).append(" · ").append(size).append(" ИЗ ").append(need);
            if (countdown >= 0) {
                text.append(" · СТАРТ ЧЕРЕЗ ").append(countdown).append(" С");
            }
        }
        return text.toString();
    }

    private String infoText(GameModeConfig mode) {
        boolean en = this.language == MenuLanguage.EN;

        if (!mode.isEnabled()) {
            return en ? "mode is off" : "режим выключен";
        }
        if (!mode.getArena().isSet()) {
            return en
                    ? "not set up — the admin has not placed the point yet"
                    : "не настроен — админ ещё не поставил точку";
        }

        StringBuilder info = new StringBuilder();
        if (en) {
            info.append("queued ").append(this.service.queueSize(mode.getId()))
                    .append(" of ").append(mode.getMinPlayers());
            if (mode.getMaxPlayers() > mode.getMinPlayers()) {
                info.append(" (up to ").append(mode.getMaxPlayers()).append(")");
            }
        } else {
            info.append("в очереди ").append(this.service.queueSize(mode.getId()))
                    .append(" из ").append(mode.getMinPlayers());
            if (mode.getMaxPlayers() > mode.getMinPlayers()) {
                info.append(" (до ").append(mode.getMaxPlayers()).append(")");
            }
        }

        int countdown = this.service.countdownLeft(mode.getId());
        if (countdown >= 0) {
            info.append(en ? " · start in " : " · старт через ").append(countdown).append(en ? " s" : " с");
        }
        return info.toString();
    }

    private String buttonText(GameModeConfig mode, boolean queuedHere) {
        boolean en = this.language == MenuLanguage.EN;
        if (queuedHere) {
            return en ? "LEAVE" : "ВЫЙТИ";
        }
        if (mode.isReady()) {
            return en ? "JOIN QUEUE" : "ВСТАТЬ В ОЧЕРЕДЬ";
        }
        return en ? "UNAVAILABLE" : "НЕДОСТУПЕН";
    }

    // ------------------------------------------------------------ мелочи

    private void reopen(Ref<EntityStore> ref, Store<EntityStore> store) {
        this.pageManager.openCustomPage(ref, store, new GroupFinderPage(
                this.playerRef, this.pageManager, this.service, this.world,
                this.languages, this.language, this.fromMenu, this.highlightModeId));
    }

    private void say(String text) {
        this.playerRef.sendMessage(Message.raw(
                (this.language == MenuLanguage.EN ? "[Finder] " : "[Поиск] ") + text));
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** Что прислал клиент при нажатии кнопки. */
    public static class FinderEventData {

        public static final BuilderCodec<FinderEventData> CODEC = BuilderCodec
                .builder(FinderEventData.class, FinderEventData::new)
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (eventData, value, extraInfo) -> eventData.action = value,
                        (eventData, extraInfo) -> eventData.action
                )
                .add()
                .build();

        private String action;

        public FinderEventData() {
        }

        public String getAction() {
            return this.action;
        }
    }
}
