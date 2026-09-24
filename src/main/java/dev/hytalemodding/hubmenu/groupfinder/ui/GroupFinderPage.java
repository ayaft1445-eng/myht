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

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;

/**
 * Окно поиска группы для игрока.
 *
 * Показывает строки режимов, у каждой — кнопка «встать в очередь» или
 * «выйти». Окно не обновляется само: после каждого нажатия оно собирается
 * заново, как и остальные окна этого мода.
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

    public GroupFinderPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull PageManager pageManager,
            @Nonnull GroupFinderService service,
            @Nonnull World world
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, FinderEventData.CODEC);
        this.playerRef = playerRef;
        this.pageManager = pageManager;
        this.service = service;
        this.world = world;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        String playerKey = ServerApi.playerKey(this.playerRef);
        String queuedMode = this.service.queuedModeId(playerKey);
        List<GameModeConfig> modes = this.service.config().getModes();

        cmd.set("#Status.Text", statusText(playerKey, queuedMode));

        for (int row = 0; row < ROWS; row++) {
            if (row >= modes.size()) {
                cmd.set("#Name" + row + ".Text", "");
                cmd.set("#Info" + row + ".Text", "");
                cmd.set("#Join" + row + ".Text", "");
                continue;
            }

            GameModeConfig mode = modes.get(row);
            boolean here = mode.getId().equals(queuedMode);

            cmd.set("#Name" + row + ".Text", mode.getName());
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

        if (ACTION_BACK.equals(action)) {
            this.close();
            return;
        }

        if (ACTION_REFRESH.equals(action)) {
            reopen(ref, store);
            return;
        }

        if (ACTION_LEAVE.equals(action)) {
            String playerKey = ServerApi.playerKey(this.playerRef);
            boolean left = this.service.leave(playerKey);
            say(left ? "вы вышли из очереди." : "вы и так не в очереди.");
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
                say("вы вышли из очереди «" + mode.getName() + "».");
            } else {
                GroupFinderService.JoinResult result =
                        this.service.join(mode.getId(), this.playerRef, ref, store, this.world);
                say(result.getMessage());
            }
            reopen(ref, store);
        }
    }

    // ------------------------------------------------------------- тексты

    private String statusText(String playerKey, String queuedMode) {
        if (!this.service.config().isEnabled()) {
            return "ПОИСК ГРУППЫ ВЫКЛЮЧЕН";
        }
        if (queuedMode == null) {
            return "ВЫ НЕ В ОЧЕРЕДИ — ВЫБЕРИТЕ РЕЖИМ";
        }
        return "ВЫ В ОЧЕРЕДИ · " + this.service.statusLine(playerKey).toUpperCase(Locale.ROOT);
    }

    private String infoText(GameModeConfig mode) {
        if (!mode.isEnabled()) {
            return "режим выключен";
        }
        if (!mode.getArena().isSet()) {
            return "не настроен — админ ещё не поставил точку";
        }
        StringBuilder info = new StringBuilder();
        info.append("в очереди ").append(this.service.queueSize(mode.getId()))
                .append(" из ").append(mode.getMinPlayers());
        if (mode.getMaxPlayers() > mode.getMinPlayers()) {
            info.append(" (до ").append(mode.getMaxPlayers()).append(")");
        }
        int countdown = this.service.countdownLeft(mode.getId());
        if (countdown >= 0) {
            info.append(" · старт через ").append(countdown).append(" с");
        }
        return info.toString();
    }

    private static String buttonText(GameModeConfig mode, boolean queuedHere) {
        if (queuedHere) {
            return "ВЫЙТИ";
        }
        return mode.isReady() ? "ВСТАТЬ В ОЧЕРЕДЬ" : "НЕДОСТУПЕН";
    }

    // ------------------------------------------------------------ мелочи

    private void reopen(Ref<EntityStore> ref, Store<EntityStore> store) {
        this.pageManager.openCustomPage(ref, store,
                new GroupFinderPage(this.playerRef, this.pageManager, this.service, this.world));
    }

    private void say(String text) {
        this.playerRef.sendMessage(Message.raw("[Поиск] " + text));
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
