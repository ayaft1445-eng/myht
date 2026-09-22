package dev.hytalemodding.serverhub;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.serverhub.commands.HubCommand;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа плагина.
 *
 * Плагин добавляет команду /hub: она открывает меню сервера с тремя кнопками
 * (Правила, Мини-игры, Дискорд сервер). Нажатие на кнопку открывает окно
 * соответствующего раздела с кнопкой «Назад».
 */
public class ServerHubPlugin extends JavaPlugin {

    public ServerHubPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(new HubCommand());
        this.getLogger().at(Level.INFO).log("[ServerHub] Loaded. Type /hub in chat to open the server menu.");
    }
}
