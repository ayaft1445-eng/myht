package dev.hytalemodding.hubmenu;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.hytalemodding.hubmenu.commands.HubCommand;
import dev.hytalemodding.hubmenu.mountedcombat.MountedCombat;
import dev.hytalemodding.hubmenu.mountedcombat.MountedCombatConfig;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Точка входа плагина.
 *
 * Команда /hub открывает чёрно-белое меню HUB с тремя кнопками-карточками:
 * «Мини-игры», «Новости» и «ДС сервер». Каждая кнопка открывает своё окно
 * с кнопкой «Назад».
 *
 * Кроме меню плагин включает бой из седла: с оружием в руке можно бить,
 * не спрыгивая с лошади (см. {@link MountedCombat} и файл MOUNTED-COMBAT-RU.md).
 */
public class HubMenuPlugin extends JavaPlugin {

    private final Config<MountedCombatConfig> mountedCombatConfig;

    public HubMenuPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.mountedCombatConfig = this.withConfig("mounted_combat", MountedCombatConfig.CODEC);
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(new HubCommand());
        this.mountedCombatConfig.save();
        MountedCombat.register(this, this.mountedCombatConfig);
        this.getLogger().at(Level.INFO).log("[HubMenu] Loaded. Type /hub in chat to open the menu.");
    }
}
