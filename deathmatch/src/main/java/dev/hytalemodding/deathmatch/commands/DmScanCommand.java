package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.Equipment;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /dmscan — ищет на сервере настоящие идентификаторы оружия и брони.
 *
 * Зачем. Список предметов лежит в ассетах игры, а не в серверном коде, и у
 * каждой сборки он свой. Перебрать его напрямую мод не может, зато может
 * спросить сервер про конкретный идентификатор: на выдуманный сервер отдаёт
 * заглушку Item.UNKNOWN — ту самую, которую клиент рисует знаком вопроса.
 *
 * Поэтому команда собирает правдоподобные имена из материалов, типов
 * предметов и разных стилей записи и проверяет каждое. Что нашлось — пишет
 * в чат и целиком в файл found-items.txt рядом с настройками: оттуда
 * идентификаторы переносятся в deathmatch.json.
 */
public class DmScanCommand extends DmCommandBase {

    /** Сколько находок показывать в чате: остальное уходит в файл. */
    private static final int SHOWN = 24;

    private static final String[] MATERIALS = {
            "wood", "wooden", "stone", "flint", "bone", "copper", "bronze", "iron", "steel",
            "silver", "gold", "golden", "cobalt", "thorium", "mithril", "diamond", "crystal",
            "obsidian", "titanium", "emerald"
    };

    private static final String[] WEAPONS = {
            "sword", "blade", "axe", "battleaxe", "hammer", "mace", "spear", "dagger",
            "knife", "bow", "crossbow", "staff", "scythe", "club", "pickaxe"
    };

    private static final String[] ARMOR = {
            "helmet", "helm", "cap", "hood", "chestplate", "chest", "cuirass", "tunic",
            "leggings", "legs", "pants", "greaves", "boots", "shoes",
            "gauntlets", "gloves", "shield"
    };

    public DmScanCommand(MatchService service) {
        super("dmscan", "Найти настоящие предметы сервера", service);
    }

    @Override
    protected void run(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        if (!requireAdmin(context, playerRef)) {
            return;
        }

        say(context, "ищу предметы, это займёт секунду…");

        List<String> weapons = scan(WEAPONS);
        List<String> armor = scan(ARMOR);

        if (weapons.isEmpty() && armor.isEmpty()) {
            say(context, "ни одного знакомого имени не нашлось.");
            say(context, "значит, предметы на этой сборке названы иначе — посмотрите"
                    + " Assets.zip игры, папку с предметами, и впишите имена в deathmatch.json.");
            return;
        }

        show(context, "оружие", weapons);
        show(context, "броня", armor);

        Path file = writeFile(weapons, armor);
        if (file != null) {
            say(context, "полный список: " + file);
        }
        say(context, "перенесите подходящие строки в deathmatch.json и наберите /dmreload.");
    }

    /** Перебирает материалы и стили записи для каждого типа предмета. */
    private static List<String> scan(String[] types) {
        Set<String> found = new LinkedHashSet<>();
        for (String type : types) {
            for (String material : MATERIALS) {
                for (String candidate : variants(material, type)) {
                    if (Equipment.exists(candidate)) {
                        found.add(candidate);
                    }
                }
            }
            // Предмет может и не зависеть от материала: просто «sword».
            for (String candidate : plain(type)) {
                if (Equipment.exists(candidate)) {
                    found.add(candidate);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Стили записи, которые встречаются у ассетов: с префиксом и без, в разном регистре. */
    private static List<String> variants(String material, String type) {
        String snake = material + "_" + type;
        String reversed = type + "_" + material;
        String camel = capitalize(material) + capitalize(type);
        String camelReversed = capitalize(type) + capitalize(material);

        List<String> list = new ArrayList<>(8);
        list.add(snake);
        list.add(reversed);
        list.add(camel);
        list.add(camelReversed);
        list.add(capitalize(material) + "_" + capitalize(type));
        list.add(capitalize(type) + "_" + capitalize(material));
        list.add("hytale:" + snake);
        list.add("hytale:" + camel);
        return list;
    }

    private static List<String> plain(String type) {
        List<String> list = new ArrayList<>(4);
        list.add(type);
        list.add(capitalize(type));
        list.add("hytale:" + type);
        return list;
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void show(CommandContext context, String title, List<String> found) {
        if (found.isEmpty()) {
            say(context, title + ": ничего не нашлось.");
            return;
        }
        say(context, title + " — найдено " + found.size() + ":");
        int shown = 0;
        for (String id : found) {
            if (shown >= SHOWN) {
                say(context, "  …ещё " + (found.size() - shown) + " в файле.");
                break;
            }
            say(context, "  " + id);
            shown++;
        }
    }

    /** Складывает находки рядом с настройками — копировать из файла удобнее, чем из чата. */
    private Path writeFile(List<String> weapons, List<String> armor) {
        try {
            Path file = this.service.store().getFile().resolveSibling("found-items.txt");
            StringBuilder text = new StringBuilder();
            text.append("# Предметы, которые сервер знает. Найдено командой /dmscan.\n");
            text.append("# Перенесите нужные строки в deathmatch.json и наберите /dmreload.\n\n");
            text.append("# оружие\n");
            for (String id : weapons) {
                text.append(id).append('\n');
            }
            text.append("\n# броня\n");
            for (String id : armor) {
                text.append(id).append('\n');
            }
            Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (IOException | RuntimeException problem) {
            return null;
        }
    }
}
