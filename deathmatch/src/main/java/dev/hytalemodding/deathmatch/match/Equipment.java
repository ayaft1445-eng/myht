package dev.hytalemodding.deathmatch.match;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemodding.deathmatch.model.Loadout;

/**
 * Выдача комплекта: оружие в руки, броня в слоты, всё лишнее — вон.
 *
 * Каждый вызов обёрнут в try/catch: набор предметов на сборках разный, и
 * неизвестный идентификатор или отличающаяся подпись метода не должны ронять
 * матч. Что не получилось — возвращается строкой, её показывают админу.
 */
public final class Equipment {

    private Equipment() {
    }

    /** Результат выдачи: что удалось и что нет. */
    public static class Result {

        private final boolean ok;
        private final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public boolean isOk() {
            return this.ok;
        }

        public String getMessage() {
            return this.message;
        }
    }

    /**
     * Ставит игроку комплект уровня. Инвентарь перед этим чистится, иначе
     * за матч у бойца накопятся все прошлые мечи.
     */
    public static Result give(PlayerRef playerRef, Loadout loadout) {
        if (playerRef == null || loadout == null) {
            return new Result(false, "некому или нечего выдавать");
        }

        try {
            Player player = playerRef.getComponent(Player.getComponentType());
            if (player == null) {
                return new Result(false, "игрок не найден в мире");
            }

            Inventory inventory = player.getInventory();
            if (inventory == null) {
                return new Result(false, "у игрока нет инвентаря");
            }

            clear(inventory);

            StringBuilder failed = new StringBuilder();
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar != null && !loadout.getWeapon().isEmpty()) {
                if (!put(hotbar, (short) 0, loadout.getWeapon())) {
                    append(failed, loadout.getWeapon());
                }
            }

            // Остальное — в хотбар со второго слота: зелья, блоки, стрелы.
            short slot = 1;
            if (hotbar != null) {
                for (String extra : loadout.getExtras()) {
                    if (slot >= hotbar.getCapacity()) {
                        break;
                    }
                    if (!put(hotbar, slot, extra)) {
                        append(failed, extra);
                    }
                    slot++;
                }
            }

            ItemContainer armorSection = inventory.getArmor();
            if (armorSection != null) {
                String[] armor = loadout.armorBySlot();
                for (short index = 0; index < armor.length && index < armorSection.getCapacity(); index++) {
                    if (armor[index].isEmpty()) {
                        continue;
                    }
                    if (!put(armorSection, index, armor[index])) {
                        append(failed, armor[index]);
                    }
                }
            }

            if (failed.length() > 0) {
                return new Result(false, "не выдалось: " + failed);
            }
            return new Result(true, "комплект «" + loadout.getName() + "» выдан");
        } catch (RuntimeException | LinkageError problem) {
            // LinkageError — это когда сборка сервера отличается от той, под
            // которую собран мод. Матч из-за этого падать не должен.
            return new Result(false, "инвентарь не дался: " + problem);
        }
    }

    /** Чистит инвентарь целиком — вызывается перед выдачей и на выходе из боя. */
    public static void clear(PlayerRef playerRef) {
        try {
            Player player = playerRef == null ? null : playerRef.getComponent(Player.getComponentType());
            if (player != null && player.getInventory() != null) {
                clear(player.getInventory());
            }
        } catch (RuntimeException | LinkageError ignored) {
            // на выходе из боя чистка не критична
        }
    }

    private static void clear(Inventory inventory) {
        clearSection(inventory.getHotbar());
        clearSection(inventory.getArmor());
        clearSection(inventory.getStorage());
        clearSection(inventory.getUtility());
    }

    private static void clearSection(ItemContainer container) {
        if (container == null) {
            return;
        }
        try {
            container.clear();
        } catch (RuntimeException | LinkageError ignored) {
            // одна непослушная секция не повод бросать остальные
        }
    }

    /**
     * Кладёт предмет в слот. Проверка — getItem(): именно он ищет ассет по
     * идентификатору и отдаёт null, если такого предмета на сборке нет.
     * Раньше проверялось isValid(), но оно пропускало выдуманные строки —
     * и в хотбаре появлялся знак вопроса вместо оружия.
     */
    private static boolean put(ItemContainer container, short slot, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return true;
        }
        try {
            ItemStack stack = new ItemStack(itemId, 1);
            if (!known(stack)) {
                return false;
            }
            container.setItemStackForSlot(slot, stack);
            return true;
        } catch (RuntimeException | LinkageError problem) {
            return false;
        }
    }

    /** Знает ли сервер такой предмет. Пустая строка — считаем, что слот пуст. */
    public static boolean exists(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return false;
        }
        try {
            return known(new ItemStack(itemId, 1));
        } catch (RuntimeException | LinkageError problem) {
            return false;
        }
    }

    /**
     * Есть ли такой предмет на сборке.
     *
     * Сервер на неизвестный идентификатор отдаёт не null, а заглушку
     * Item.UNKNOWN — её клиент и рисует знаком вопроса. Поэтому сравниваем
     * с заглушкой и заодно сверяем идентификатор: так выдуманная строка в
     * хотбар уже не попадёт.
     */
    private static boolean known(ItemStack stack) {
        Item item = stack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return false;
        }
        String resolved = item.getId();
        return resolved != null && resolved.equalsIgnoreCase(stack.getItemId());
    }

    private static void append(StringBuilder text, String itemId) {
        if (text.length() > 0) {
            text.append(", ");
        }
        text.append(itemId);
    }
}
