package dev.hytalemodding.armorsets;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * Мод с двумя комплектами брони: белым и красным.
 *
 * Кода тут нет: всё делает пак ассетов внутри jar.
 *   Server/Item/Items/Armor/<набор>/Armor_<набор>_<часть>.json — предметы и характеристики;
 *   Common/Items/Armors/<набор>/                              — модели и текстуры;
 *   Common/Icons/ItemsGenerated/Armor_<набор>_<часть>.png      — иконки в инвентаре;
 *   Server/Languages/<язык>/armorsets.lang                     — названия.
 *
 * Класс нужен только как точка входа плагина.
 */
public class ArmorSetsPlugin extends JavaPlugin {

    public ArmorSetsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }
}
