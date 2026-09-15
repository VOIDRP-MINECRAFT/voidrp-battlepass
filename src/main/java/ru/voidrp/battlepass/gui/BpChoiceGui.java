package ru.voidrp.battlepass.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import ru.voidrp.battlepass.season.BpReward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pick one variant of a choice reward. The click is handled in BpGuiListener. */
public final class BpChoiceGui {

    public static final String TITLE = "§5§l✦ Выбери награду";
    public static final int BACK_SLOT = 22;

    /** What the player is choosing for; the options live in the reward itself. */
    public record Pending(boolean premium, int level, int page) { }

    public static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private BpChoiceGui() {}

    /** Slot of option {@code i} out of {@code n} — centred in the middle row. */
    public static int slotOf(int i, int n) {
        int start = 9 + (9 - Math.min(n, 9)) / 2;
        return start + i;
    }

    public static int optionAt(int slot, int n) {
        for (int i = 0; i < Math.min(n, 9); i++) if (slotOf(i, n) == slot) return i;
        return -1;
    }

    public static void open(Player player, BpReward reward, boolean premium, int level, int page) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        ItemStack filler = pane(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int s = 0; s < 27; s++) inv.setItem(s, filler);

        List<BpReward> opts = reward.getOptions();
        for (int i = 0; i < Math.min(opts.size(), 9); i++) {
            BpReward o = opts.get(i);
            ItemStack it = new ItemStack(RewardText.icon(o));
            ItemMeta m = it.getItemMeta();
            m.setDisplayName("§e§lВариант " + (i + 1));
            List<String> lore = new ArrayList<>(RewardText.lore(o));
            lore.add(" ");
            lore.add("§aНажми, чтобы забрать");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(slotOf(i, opts.size()), it);
        }

        ItemStack head = pane(Material.BOOK, "§6Уровень " + level + (premium ? " §b[Premium]" : " §a[Free]"));
        ItemMeta hm = head.getItemMeta();
        hm.setLore(List.of("§7Можно взять только один вариант.", "§7Выбор нельзя поменять."));
        head.setItemMeta(hm);
        inv.setItem(4, head);
        inv.setItem(BACK_SLOT, pane(Material.ARROW, "§7◄ Назад к Battle Pass"));

        PENDING.put(player.getUniqueId(), new Pending(premium, level, page));
        player.openInventory(inv);
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }
}
