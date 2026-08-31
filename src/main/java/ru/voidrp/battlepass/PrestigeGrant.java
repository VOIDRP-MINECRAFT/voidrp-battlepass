package ru.voidrp.battlepass;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;

/**
 * Prestige: once a player passes level {@link BattlePassData#MAX_LEVEL} their extra XP keeps
 * accumulating, and every further XP_PER_LEVEL grants a flat Void Coin reward — a repeatable,
 * uncapped incentive to keep filling the pass after 100. Granted levels are persisted so a
 * relog never double-rewards.
 */
public final class PrestigeGrant {

    public static final int VOIDCOIN_PER_PRESTIGE = 50;

    private PrestigeGrant() {}

    /** Grants any newly-earned prestige levels. Safe to call after every XP gain. */
    public static void check(Plugin plugin, BattlePassStorage storage, Player player) {
        if (plugin == null || storage == null || player == null) return;
        UUID uuid = player.getUniqueId();
        BattlePassData data = storage.get(uuid);
        if (data == null) return;
        int prestige = data.getPrestige();
        if (prestige <= data.getPrestigeGranted()) return;

        int gained = prestige - data.getPrestigeGranted();
        data.setPrestigeGranted(prestige);
        storage.save(uuid);
        final long vc = (long) gained * VOIDCOIN_PER_PRESTIGE;
        final int pr = prestige;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vrgs voidcoin " + player.getName() + " " + vc);
            player.sendMessage("§5§l✦ ПРЕСТИЖ " + pr + "! §d+" + vc + " Void Coin §7(престиж-уровень пройден)");
            player.sendTitle("§5§l✦ ПРЕСТИЖ " + pr, "§d+" + vc + " Void Coin", 10, 70, 20);
        });
    }
}
