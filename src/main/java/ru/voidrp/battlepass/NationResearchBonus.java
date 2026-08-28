package ru.voidrp.battlepass;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import ru.voidrp.gamesync.VoidRpGameSyncPlugin;
import ru.voidrp.gamesync.model.NationDefinition;

/**
 * Resolves the Battle Pass XP multiplier granted by a player's nation research
 * ("Академия наук" → {@code bp_xp_bonus_percent}). Falls back to 1.0 (no bonus)
 * whenever GameSync is absent, the player has no nation, or anything goes wrong,
 * so XP grants never break if the research system is unavailable.
 */
public final class NationResearchBonus {

    private NationResearchBonus() {}

    public static double multiplier(Player player) {
        if (player == null) return 1.0;
        try {
            Plugin gs = Bukkit.getPluginManager().getPlugin("VoidRpGameSync");
            if (!(gs instanceof VoidRpGameSyncPlugin gameSync)) return 1.0;
            NationDefinition nation = gameSync.getNationRegistry().findByPlayer(player.getName());
            if (nation == null) return 1.0;
            double pct = gameSync.getNationResearchEffectService().getEffect(nation.slug(), "bp_xp_bonus_percent");
            if (pct <= 0) return 1.0;
            return 1.0 + pct / 100.0;
        } catch (Throwable ignored) {
            return 1.0;
        }
    }

    /** Applies the nation research multiplier to a base XP amount. */
    public static long apply(Player player, long baseXp) {
        double mult = multiplier(player);
        return mult == 1.0 ? baseXp : Math.round(baseXp * mult);
    }
}
