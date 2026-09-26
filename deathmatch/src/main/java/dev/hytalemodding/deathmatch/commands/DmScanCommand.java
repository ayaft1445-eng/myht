package dev.hytalemodding.deathmatch.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.deathmatch.match.ItemCatalog;
import dev.hytalemodding.deathmatch.match.MatchService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * /dmscan — показывает настоящие идентификаторы предметов этого сервера.
 *
 * Список предметов лежит в ассетах игры, и угадать имена нельзя: на каждой
 * сборке они свои. Поэтому команда спрашивает сам сервер двумя способами —
 * читает инвентарь игрока (это работает всегда) и пробует достать полный
 * список из хранилища ассетов.
 *
 * Как пользоваться: возьмите в инвентарь оружие и броню, которые хотите
 * выдавать в дезматче, и наберите команду. Имена уйдут в чат и в файл
 * found-items.txt рядом с настройками.
 */
public class DmScanCommand extends DmCommandBase {

    /** Сколько строк показывать в чате: остальное уходит в файл. */
    private static final int SHOWN = 20;

    public DmScanCommand(MatchService service) {
        super("dmscan", "Показать идентификаторы предметов сервера", service);
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

        Path dataDirectory = this.service.store().getFile().getParent();
        List<String> inventory = ItemCatalog.fromInventory(playerRef);
        List<String> all = ItemCatalog.fromAssets();
        if (all.isEmpty()) {
            // Рефлексия не дотянулась — читаем имена прямо из Assets.zip сервера.
            all = ItemCatalog.fromAssetsZip(dataDirectory);
            Path archive = ItemCatalog.archivePath(dataDirectory);
            if (archive != null) {
                say(context, "читаю ассеты: " + archive);
            }
        }

        if (inventory.isEmpty() && all.isEmpty()) {
            say(context, "ничего не нашлось: инвентарь пуст, ассеты не дались.");
            say(context, "возьмите в руки оружие и броню и наберите команду снова —"
                    + " их настоящие имена появятся здесь.");
            say(context, "либо положите Assets.zip игры рядом с сервером — мод прочитает имена"
                    + " прямо из него.");
            return;
        }

        if (!inventory.isEmpty()) {
            say(context, "предметы у вас в инвентаре — это и есть настоящие имена:");
            int shown = 0;
            for (String id : inventory) {
                if (shown >= SHOWN) {
                    say(context, "  …ещё " + (inventory.size() - shown) + " в файле.");
                    break;
                }
                say(context, "  " + id);
                shown++;
            }
        } else {
            say(context, "инвентарь пуст — возьмите в него оружие и броню, чтобы увидеть их имена.");
        }

        if (!all.isEmpty()) {
            say(context, "всего предметов на сервере: " + all.size() + ". Оружие и броня по названию:");
            int shown = 0;
            for (String id : all) {
                if (!looksLikeGear(id)) {
                    continue;
                }
                if (shown >= SHOWN) {
                    say(context, "  …остальное в файле.");
                    break;
                }
                say(context, "  " + id);
                shown++;
            }
        }

        Path file = writeFile(inventory, all);
        if (file != null) {
            say(context, "полный список: " + file);
        }
        say(context, "перенесите нужные строки в deathmatch.json и наберите /dmreload.");
    }

    /** Грубый фильтр для чата: в файл всё равно уходит весь список. */
    private static boolean looksLikeGear(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.contains("sword") || lower.contains("axe") || lower.contains("blade")
                || lower.contains("spear") || lower.contains("bow") || lower.contains("hammer")
                || lower.contains("dagger") || lower.contains("mace") || lower.contains("staff")
                || lower.contains("helm") || lower.contains("chest") || lower.contains("leg")
                || lower.contains("boot") || lower.contains("armor") || lower.contains("armour")
                || lower.contains("shield") || lower.contains("glove") || lower.contains("gauntlet");
    }

    private Path writeFile(List<String> inventory, List<String> all) {
        try {
            Path file = this.service.store().getFile().resolveSibling("found-items.txt");
            StringBuilder text = new StringBuilder();
            text.append("# Настоящие идентификаторы предметов этого сервера.\n");
            text.append("# Собрано командой /dmscan. Перенесите нужные строки в deathmatch.json\n");
            text.append("# (поля weapon, helmet, chest, hands, legs) и наберите /dmreload.\n\n");

            text.append("# --- то, что лежало в инвентаре игрока\n");
            for (String id : inventory) {
                text.append(id).append('\n');
            }

            if (!all.isEmpty()) {
                text.append("\n# --- всё, что знает сервер (").append(all.size()).append(")\n");
                for (String id : all) {
                    text.append(id).append('\n');
                }
            } else {
                text.append("\n# Полный список ассетов достать не вышло — берите имена из инвентаря.\n");
            }

            Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (IOException | RuntimeException problem) {
            return null;
        }
    }
}
