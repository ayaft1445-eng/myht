package dev.hytalemodding.hubmenu.groupfinder.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import dev.hytalemodding.hubmenu.groupfinder.model.ArenaPoint;
import dev.hytalemodding.hubmenu.groupfinder.model.GameModeConfig;
import dev.hytalemodding.hubmenu.groupfinder.model.GroupFinderConfig;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;

/**
 * Панель настроек поиска группы.
 *
 * Одна страница показывает либо список режимов, либо настройку одного
 * режима — это решает поле editingModeId. Всё меняется кнопками: текстовых
 * полей в разметке нет нарочно, потому что ошибка в разметке рвёт клиенту
 * соединение, а кнопки собраны из тех же элементов, что и остальное меню.
 *
 * Координаты арены не набираются руками: админ встаёт в нужное место и
 * жмёт «ПОСТАВИТЬ ТОЧКУ ЗДЕСЬ».
 *
 * Разметка: Common/UI/Custom/HubMenu/GroupFinderAdmin.ui и GroupFinderMode.ui
 */
public class AdminPage extends InteractiveCustomUIPage<AdminPage.AdminEventData> {

    private static final String LIST_LAYOUT = "HubMenu/GroupFinderAdmin.ui";
    private static final String MODE_LAYOUT = "HubMenu/GroupFinderMode.ui";

    /** Сколько строк режимов помещается в разметке списка. */
    public static final int ROWS = 6;
    private static final int TIMEOUT_STEP = 30;

    private final PlayerRef playerRef;
    private final PageManager pageManager;
    private final GroupFinderService service;
    private final World world;
    /** null — показываем список, иначе настройку этого режима. */
    private final String editingModeId;

    public AdminPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull PageManager pageManager,
            @Nonnull GroupFinderService service,
            @Nonnull World world,
            String editingModeId
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminEventData.CODEC);
        this.playerRef = playerRef;
        this.pageManager = pageManager;
        this.service = service;
        this.world = world;
        this.editingModeId = editingModeId;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        GameModeConfig editing = this.editingModeId == null
                ? null
                : this.service.config().findMode(this.editingModeId);

        if (editing == null) {
            buildList(cmd, evt);
        } else {
            buildMode(cmd, evt, editing);
        }
    }

    // -------------------------------------------------------- список режимов

    private void buildList(UICommandBuilder cmd, UIEventBuilder evt) {
        GroupFinderConfig config = this.service.config();
        List<GameModeConfig> modes = config.getModes();

        cmd.set("#Banner.Text", bannerText(config, modes.size()));
        cmd.set("#SystemValue.Text", config.isEnabled() ? "ВКЛ" : "ВЫКЛ");
        cmd.set("#AnnounceValue.Text", config.isAnnounce() ? "ВКЛ" : "ВЫКЛ");
        cmd.set("#TimeoutValue.Text", timeoutText(config.getQueueTimeoutSeconds()));
        cmd.set("#SelfAdminButton.Text", selfAdminText(config));

        bind(evt, "#BackButton", "back");
        bind(evt, "#SystemValue", "sys");
        bind(evt, "#AnnounceValue", "ann");
        bind(evt, "#TimeoutMinus", "tminus");
        bind(evt, "#TimeoutPlus", "tplus");
        bind(evt, "#AddButton", "add");
        bind(evt, "#ReloadButton", "reload");
        bind(evt, "#DiagButton", "diag");
        if (config.isSetupMode()) {
            bind(evt, "#SelfAdminButton", "selfadmin");
        }

        for (int row = 0; row < ROWS; row++) {
            if (row >= modes.size()) {
                cmd.set("#MName" + row + ".Text", "");
                cmd.set("#MInfo" + row + ".Text", "");
                cmd.set("#MToggle" + row + ".Text", "");
                cmd.set("#MEdit" + row + ".Text", "");
                continue;
            }
            GameModeConfig mode = modes.get(row);
            cmd.set("#MName" + row + ".Text", mode.getName());
            cmd.set("#MInfo" + row + ".Text", modeSummary(mode));
            cmd.set("#MToggle" + row + ".Text", mode.isEnabled() ? "ВКЛ" : "ВЫКЛ");
            cmd.set("#MEdit" + row + ".Text", "НАСТРОИТЬ");
            bind(evt, "#MToggle" + row, "tog" + row);
            bind(evt, "#MEdit" + row, "edit" + row);
        }
    }

    private String bannerText(GroupFinderConfig config, int modeCount) {
        if (config.isSetupMode()) {
            return "ПЕРВАЯ НАСТРОЙКА: ПАНЕЛЬ ОТКРЫТА ВСЕМ — НАЖМИТЕ «СДЕЛАТЬ СЕБЯ АДМИНОМ»";
        }
        String error = this.service.store().getLastError();
        if (!error.isEmpty()) {
            return error.toUpperCase(Locale.ROOT);
        }
        if (modeCount > ROWS) {
            return "РЕЖИМОВ БОЛЬШЕ " + ROWS + " — ОСТАЛЬНЫЕ ВИДНЫ ТОЛЬКО В ФАЙЛЕ НАСТРОЕК";
        }
        return "ФАЙЛ: " + this.service.store().getFile();
    }

    private String modeSummary(GameModeConfig mode) {
        StringBuilder text = new StringBuilder();
        text.append("ключ ").append(mode.getId());
        text.append(" · игроков ").append(mode.getMinPlayers()).append("–").append(mode.getMaxPlayers());
        text.append(" · отсчёт ").append(mode.getCountdownSeconds()).append(" с");
        text.append(" · точка: ").append(mode.getArena().describe());
        text.append(" · в очереди ").append(this.service.queueSize(mode.getId()));
        if (mode.getCardIndex() >= 0) {
            text.append(" · кнопка ").append(mode.getCardIndex() + 1);
        }
        return text.toString();
    }

    private static String timeoutText(int seconds) {
        if (seconds <= 0) {
            return "выкл";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + " мин";
        }
        return seconds + " с";
    }

    private static String selfAdminText(GroupFinderConfig config) {
        if (config.isSetupMode()) {
            return "СДЕЛАТЬ СЕБЯ АДМИНОМ";
        }
        return "АДМИНОВ: " + config.getAdmins().size();
    }

    // ------------------------------------------------------- настройка режима

    private void buildMode(UICommandBuilder cmd, UIEventBuilder evt, GameModeConfig mode) {
        ArenaPoint arena = mode.getArena();

        cmd.set("#Title.Text", "НАСТРОЙКА · " + mode.getName());
        cmd.set("#EnabledValue.Text", mode.isEnabled() ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН");
        cmd.set("#MinValue.Text", String.valueOf(mode.getMinPlayers()));
        cmd.set("#MaxValue.Text", String.valueOf(mode.getMaxPlayers()));
        cmd.set("#CdValue.Text", String.valueOf(mode.getCountdownSeconds()));
        cmd.set("#SpreadValue.Text", String.valueOf(mode.getSpreadRadius()));
        cmd.set("#CardValue.Text", cardText(mode.getCardIndex()));
        cmd.set("#ArenaValue.Text", arena.describe());
        cmd.set("#QueueValue.Text", "в очереди: " + this.service.queueSize(mode.getId()));
        cmd.set("#Hint.Text", "ключ режима: " + mode.getId()
                + " · название меняется в файле groupfinder.json");

        bind(evt, "#BackButton", "back");
        bind(evt, "#EnabledValue", "en");
        bind(evt, "#MinMinus", "min-");
        bind(evt, "#MinPlus", "min+");
        bind(evt, "#MaxMinus", "max-");
        bind(evt, "#MaxPlus", "max+");
        bind(evt, "#CdMinus", "cd-");
        bind(evt, "#CdPlus", "cd+");
        bind(evt, "#SpreadMinus", "sp-");
        bind(evt, "#SpreadPlus", "sp+");
        bind(evt, "#CardPrev", "cardprev");
        bind(evt, "#CardNext", "cardnext");
        bind(evt, "#SetHereButton", "sethere");
        bind(evt, "#ClearArenaButton", "cleararena");
        bind(evt, "#TestButton", "test");
        bind(evt, "#ClearQueueButton", "clearqueue");
        bind(evt, "#DeleteButton", "del");
    }

    private static String cardText(int cardIndex) {
        return cardIndex < 0 ? "нет" : "кнопка " + (cardIndex + 1);
    }

    // ------------------------------------------------------------- обработка

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull AdminEventData data
    ) {
        String action = data.getAction();
        if (action == null || !ensureAdmin(ref, store)) {
            return;
        }

        if ("back".equals(action)) {
            if (this.editingModeId == null) {
                this.close();
            } else {
                open(ref, store, null);
            }
            return;
        }

        if (this.editingModeId == null) {
            handleListAction(ref, store, action);
        } else {
            handleModeAction(ref, store, action);
        }
    }

    private void handleListAction(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        GroupFinderConfig config = this.service.config();
        List<GameModeConfig> modes = config.getModes();

        switch (action) {
            case "sys":
                config.setEnabled(!config.isEnabled());
                saveAnd(ref, store, "поиск группы " + (config.isEnabled() ? "включён" : "выключен") + ".");
                return;
            case "ann":
                config.setAnnounce(!config.isAnnounce());
                saveAnd(ref, store, "оповещения в чат " + (config.isAnnounce() ? "включены" : "выключены") + ".");
                return;
            case "tminus":
                config.setQueueTimeoutSeconds(config.getQueueTimeoutSeconds() - TIMEOUT_STEP);
                saveAnd(ref, store, null);
                return;
            case "tplus":
                config.setQueueTimeoutSeconds(config.getQueueTimeoutSeconds() + TIMEOUT_STEP);
                saveAnd(ref, store, null);
                return;
            case "add": {
                GameModeConfig mode = config.addMode();
                saveAnd(ref, store, "добавлен режим «" + mode.getName() + "» (ключ " + mode.getId() + ").");
                return;
            }
            case "reload":
                this.service.reloadConfig();
                saveless(ref, store, "настройки перечитаны из файла.");
                return;
            case "diag":
                for (String line : ServerApi.diagnostics()) {
                    say(line);
                }
                say("файл настроек: " + this.service.store().getFile());
                return;
            case "selfadmin": {
                String username = ServerApi.username(this.playerRef);
                config.addAdmin(username);
                saveAnd(ref, store, username + " добавлен в админы. Остальным панель закрыта.");
                return;
            }
            default:
                break;
        }

        if (action.startsWith("tog")) {
            int row = number(action.substring(3));
            if (row >= 0 && row < modes.size()) {
                GameModeConfig mode = modes.get(row);
                if (!mode.isEnabled() && !mode.getArena().isSet()) {
                    say("сначала поставьте точку телепорта у режима «" + mode.getName() + "».");
                    return;
                }
                mode.setEnabled(!mode.isEnabled());
                saveAnd(ref, store, "«" + mode.getName() + "» "
                        + (mode.isEnabled() ? "включён" : "выключен") + ".");
            }
            return;
        }

        if (action.startsWith("edit")) {
            int row = number(action.substring(4));
            if (row >= 0 && row < modes.size()) {
                open(ref, store, modes.get(row).getId());
            }
        }
    }

    private void handleModeAction(Ref<EntityStore> ref, Store<EntityStore> store, String action) {
        GroupFinderConfig config = this.service.config();
        GameModeConfig mode = config.findMode(this.editingModeId);
        if (mode == null) {
            open(ref, store, null);
            return;
        }

        switch (action) {
            case "en":
                if (!mode.isEnabled() && !mode.getArena().isSet()) {
                    say("сначала поставьте точку телепорта.");
                    return;
                }
                mode.setEnabled(!mode.isEnabled());
                saveAnd(ref, store, null);
                return;
            case "min-":
                mode.setMinPlayers(mode.getMinPlayers() - 1);
                saveAnd(ref, store, null);
                return;
            case "min+":
                mode.setMinPlayers(mode.getMinPlayers() + 1);
                if (mode.getMaxPlayers() < mode.getMinPlayers()) {
                    mode.setMaxPlayers(mode.getMinPlayers());
                }
                saveAnd(ref, store, null);
                return;
            case "max-":
                mode.setMaxPlayers(mode.getMaxPlayers() - 1);
                saveAnd(ref, store, null);
                return;
            case "max+":
                mode.setMaxPlayers(mode.getMaxPlayers() + 1);
                saveAnd(ref, store, null);
                return;
            case "cd-":
                mode.setCountdownSeconds(mode.getCountdownSeconds() - 1);
                saveAnd(ref, store, null);
                return;
            case "cd+":
                mode.setCountdownSeconds(mode.getCountdownSeconds() + 1);
                saveAnd(ref, store, null);
                return;
            case "sp-":
                mode.setSpreadRadius(mode.getSpreadRadius() - 1);
                saveAnd(ref, store, null);
                return;
            case "sp+":
                mode.setSpreadRadius(mode.getSpreadRadius() + 1);
                saveAnd(ref, store, null);
                return;
            case "cardprev":
                config.bindCard(mode, nextCard(mode.getCardIndex(), -1));
                saveAnd(ref, store, null);
                return;
            case "cardnext":
                config.bindCard(mode, nextCard(mode.getCardIndex(), 1));
                saveAnd(ref, store, null);
                return;
            case "sethere":
                setArenaHere(ref, store, mode);
                return;
            case "cleararena":
                mode.getArena().clear();
                mode.setEnabled(false);
                saveAnd(ref, store, "точка сброшена, режим выключен.");
                return;
            case "test":
                testTeleport(ref, store, mode);
                return;
            case "clearqueue":
                this.service.clearQueue(mode.getId());
                saveless(ref, store, "очередь режима очищена.");
                return;
            case "del": {
                String name = mode.getName();
                this.service.clearQueue(mode.getId());
                config.removeMode(mode.getId());
                this.service.saveConfig();
                say("режим «" + name + "» удалён.");
                open(ref, store, null);
                return;
            }
            default:
                break;
        }
    }

    /** Записывает в точку телепорта место, где стоит админ. */
    private void setArenaHere(Ref<EntityStore> ref, Store<EntityStore> store, GameModeConfig mode) {
        double[] position = ServerApi.position(store, ref);
        if (position == null) {
            say("не получилось прочитать ваши координаты — посмотрите «ДИАГНОСТИКА».");
            return;
        }
        mode.getArena().setTo(
                ServerApi.worldName(this.world),
                position[0],
                position[1],
                position[2],
                (float) position[3],
                (float) position[4]
        );
        saveAnd(ref, store, "точка режима «" + mode.getName() + "»: " + mode.getArena().describe());
    }

    /** Уносит самого админа в точку — проверить, что она не в стене. */
    private void testTeleport(Ref<EntityStore> ref, Store<EntityStore> store, GameModeConfig mode) {
        ArenaPoint arena = mode.getArena();
        if (!arena.isSet()) {
            say("точка не задана.");
            return;
        }
        Object targetWorld = null;
        String wanted = arena.getWorld();
        if (wanted != null && !wanted.isEmpty() && !wanted.equalsIgnoreCase(ServerApi.worldName(this.world))) {
            targetWorld = ServerApi.findWorld(wanted);
            if (targetWorld == null) {
                say("мир «" + wanted + "» не найден.");
                return;
            }
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        String error = ServerApi.teleport(store, ref, player, this.world, targetWorld,
                arena.getX(), arena.getY(), arena.getZ(), arena.getYaw(), arena.getPitch());
        if (error == null) {
            say("переносим вас в точку режима «" + mode.getName() + "».");
            this.close();
        } else {
            say("телепорт не сработал: " + error);
        }
    }

    /** Следующая карточка меню по кругу: нет → 1 → 2 → 3 → 4 → нет. */
    private static int nextCard(int current, int step) {
        int count = GroupFinderConfig.MENU_CARDS;
        int value = current + step;
        if (value < -1) {
            value = count - 1;
        }
        if (value >= count) {
            value = -1;
        }
        return value;
    }

    // ------------------------------------------------------------- мелочи

    private boolean ensureAdmin(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (this.service.isAdmin(player, ServerApi.username(this.playerRef))) {
            return true;
        }
        say("нужны права администратора.");
        this.close();
        return false;
    }

    private void saveAnd(Ref<EntityStore> ref, Store<EntityStore> store, String message) {
        if (!this.service.saveConfig()) {
            say("настройки не сохранились: " + this.service.store().getLastError());
        } else if (message != null) {
            say(message);
        }
        open(ref, store, this.editingModeId);
    }

    private void saveless(Ref<EntityStore> ref, Store<EntityStore> store, String message) {
        if (message != null) {
            say(message);
        }
        open(ref, store, this.editingModeId);
    }

    private void open(Ref<EntityStore> ref, Store<EntityStore> store, String modeId) {
        this.pageManager.openCustomPage(ref, store,
                new AdminPage(this.playerRef, this.pageManager, this.service, this.world, modeId));
    }

    private void bind(UIEventBuilder evt, String selector, String action) {
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData().append("Action", action),
                false
        );
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
    public static class AdminEventData {

        public static final BuilderCodec<AdminEventData> CODEC = BuilderCodec
                .builder(AdminEventData.class, AdminEventData::new)
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (eventData, value, extraInfo) -> eventData.action = value,
                        (eventData, extraInfo) -> eventData.action
                )
                .add()
                .build();

        private String action;

        public AdminEventData() {
        }

        public String getAction() {
            return this.action;
        }
    }
}
