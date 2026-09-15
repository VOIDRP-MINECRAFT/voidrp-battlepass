package ru.voidrp.battlepass.gui;

import org.bukkit.Material;

import ru.voidrp.battlepass.season.BpReward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Human text + icon for rewards, shared by the pass GUI and the choice GUI. */
final class RewardText {

    private RewardText() {}

    /** One line describing a plain reward (random amounts shown as "от–до"). */
    static String line(BpReward r) {
        return switch (r.getType()) {
            case MONEY -> "§6💰 " + r.amountText() + " монет";
            case EXP -> "§a✨ " + r.amountText() + " опыта";
            case VOIDCOIN -> "§5◆ " + r.amountText() + " Void Coin §7(премиум-валюта)";
            case ITEM -> "§b📦 " + name(r);
            case COMMAND -> "§d🎁 " + name(r);
            case CHOICE -> "§e🎲 " + (r.getDisplayName() != null ? r.getDisplayName() : "Награда на выбор");
        };
    }

    static List<String> lore(BpReward r) {
        List<String> lore = new ArrayList<>();
        lore.add(line(r));
        if (r.getType() == ru.voidrp.battlepass.season.BpRewardType.CHOICE) {
            lore.add("§7Выбери один вариант:");
            for (BpReward o : r.getOptions()) lore.add("§7 • " + line(o));
        }
        if (r.hasCountRange() || r.hasAmountRange()) lore.add("§8Количество выпадает случайно");
        return lore;
    }

    /** Stored name with the count swapped for the range when it's random. */
    private static String name(BpReward r) {
        String n = r.getDisplayName() != null ? r.getDisplayName() : "Особая награда";
        if (r.hasCountRange()) return n.replaceAll("\\s*×\\s*[\\d–-]+$", "") + " ×" + r.countText();
        return n;
    }

    /** Best-effort icon: vanilla material, modded item via Mohist's NAMESPACE_ITEM, else a fallback. */
    static Material icon(BpReward r) {
        switch (r.getType()) {
            case MONEY: return Material.GOLD_NUGGET;
            case EXP: return Material.EXPERIENCE_BOTTLE;
            case VOIDCOIN: return Material.AMETHYST_SHARD;
            default: break;
        }
        String id = r.getIcon();
        if (id == null && r.getMaterial() != null) id = "minecraft:" + r.getMaterial();
        if (id != null) {
            Material m = Material.matchMaterial(id);
            if (m != null && m.isItem()) return m;
            try {
                m = Material.valueOf(id.replace(':', '_').toUpperCase(Locale.ROOT));
                if (m.isItem()) return m;
            } catch (IllegalArgumentException ignored) { }
        }
        return r.getType() == ru.voidrp.battlepass.season.BpRewardType.CHOICE ? Material.ENDER_CHEST : Material.CHEST;
    }
}
