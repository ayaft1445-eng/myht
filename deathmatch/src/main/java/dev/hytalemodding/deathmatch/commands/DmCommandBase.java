package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.bridge.ServerApi;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;

/**
 * Общая часть всех команд мода.
 *
 * Аргументов у команд нет намеренно: на каждое действие своя команда. Так
 * ничего не ломается от опечатки в разборе аргументов, а игроку не нужно
 * помнить синтаксис — только имя команды, которое подсказывает /dmadmin.
 */
public abstract class DmCommandBase extends AbstractPlayerCommand {

    protected static final String PREFIX = "[Дезматч] ";

    protected final MatchService service;

    protected DmCommandBase(String name, String description, MatchService service) {
        super(name, description);
        this.service = service;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        // Мир запоминаем при любой команде: из него мод потом шлёт объявления
        // и перечисляет игроков.
        this.service.rememberWorld(world);
        try {
            run(context, store, ref, playerRef, world);
        } catch (RuntimeException | LinkageError problem) {
            say(context, "команда сорвалась: " + problem);
        }
    }

    protected abstract void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    );

    /**
     * Пускает дальше только админа. Отказ сразу объясняет, что делать:
     * иначе владелец сервера с опкой упирается в «нет прав» и не знает,
     * куда смотреть.
     */
    protected boolean requireAdmin(CommandContext context, PlayerRef playerRef) {
        if (this.service.isAdmin(playerRef)) {
            return true;
        }
        String username = ServerApi.username(playerRef);
        say(context, "нужны права администратора. Ваш ник для мода: «" + username + "».");
        say(context, "впишите его в список admins в файле " + this.service.store().getFile()
                + " и наберите /dmreload — или выдайте право deathmatch.admin.");
        return false;
    }

    protected void say(CommandContext context, String text) {
        context.sendMessage(Message.raw(PREFIX + text));
    }

    protected String keyOf(PlayerRef playerRef) {
        return ServerApi.playerKey(playerRef);
    }
}
