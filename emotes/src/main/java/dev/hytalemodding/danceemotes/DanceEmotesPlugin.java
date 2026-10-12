package dev.hytalemodding.danceemotes;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Мод с тремя танцами для меню эмоций.
 *
 * Кода тут почти нет: всё делает пак ассетов внутри jar.
 *   Server/Emote/Dance_*.json        — описание эмоций (сервер сам рассылает их клиентам);
 *   Common/Characters/Animations/... — сами анимации .blockyanim;
 *   Common/Icons/Emotes/Dance_*.png  — иконки в меню эмоций;
 *   Server/Languages/<язык>/danceemotes.lang — названия танцев.
 *
 * Класс нужен только как точка входа плагина.
 */
public class DanceEmotesPlugin extends JavaPlugin {

    public DanceEmotesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.getLogger().at(Level.INFO).log("[DanceEmotes] три танца добавлены в меню эмоций");
    }
}
