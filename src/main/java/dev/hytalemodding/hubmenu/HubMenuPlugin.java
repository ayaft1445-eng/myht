package dev.hytalemodding.hubmenu;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.hubmenu.commands.HubCommand;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа плагина.
 *
 * Команда /hub открывает чёрно-белое меню HUB с тремя кнопками-карточками:
 * «Мини-игры», «Новости» и «ДС сервер». Каждая кнопка открывает своё окно
 * с кнопкой «Назад».
 */
public class HubMenuPlugin extends JavaPlugin {

    public HubMenuPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(new HubCommand());
        this.getLogger().at(Level.INFO).log("[HubMenu] Loaded. Type /hub in chat to open the menu.");
    }
}
