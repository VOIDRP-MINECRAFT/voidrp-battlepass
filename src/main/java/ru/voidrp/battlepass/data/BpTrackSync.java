package ru.voidrp.battlepass.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import ru.voidrp.battlepass.season.BpReward;
import ru.voidrp.battlepass.season.Season;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.util.TreeSet;

/**
 * Builds a Battle Pass reward-track snapshot (free + premium rows per level with
 * claimed state) and pushes it to the backend so the WebGUI can render the track.
 * Best-effort; network work runs off the main thread.
 */
public final class BpTrackSync {

    private BpTrackSync() {}

    public static void push(JavaPlugin plugin, BattlePassStorage storage, PremiumStorage premium,
                            SeasonRewards rewards, BackendSyncClient backend, Player player) {
        if (backend == null || !backend.isConfigured() || player == null) return;

        BattlePassData data = storage.get(player.getUniqueId());
        boolean hasPremium = premium.hasPremium(player.getUniqueId());

        TreeSet<Integer> levels = new TreeSet<>();
        levels.addAll(rewards.getAllFreeRewards().keySet());
        levels.addAll(rewards.getAllPremiumRewards().keySet());

        JsonArray arr = new JsonArray();
        for (int lvl : levels) {
            JsonObject row = new JsonObject();
            row.addProperty("level", lvl);
            BpReward free = rewards.getFreeReward(lvl);
            BpReward prem = rewards.getPremiumReward(lvl);
            if (free != null) row.add("free", reward(free));
            if (prem != null) row.add("premium", reward(prem));
            row.addProperty("free_claimed", data.isFreeClaimed(lvl));
            row.addProperty("premium_claimed", data.isPremiumClaimed(lvl));
            arr.add(row);
        }

        JsonObject body = new JsonObject();
        body.addProperty("minecraft_nickname", player.getName());
        // Show the human season name in the WebGUI (data.getSeason() is the storage key = start date).
        String seasonName = plugin.getConfig().getString("season-name", data.getSeason());
        body.addProperty("season", seasonName);
        body.addProperty("level", data.getLevel());
        body.addProperty("xp", data.getXp());
        body.addProperty("xp_per_level", (int) BattlePassData.XP_PER_LEVEL);
        body.addProperty("has_premium", hasPremium);
        body.addProperty("ends_in_days", Season.daysUntilReset());
        body.add("levels", arr);
        String json = body.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> backend.pushTrack(json));
    }

    private static JsonObject reward(BpReward r) {
        JsonObject o = new JsonObject();
        o.addProperty("type", r.getType() != null ? r.getType().name().toLowerCase() : null);
        o.addProperty("display_name", r.getDisplayName());
        o.addProperty("amount", r.getAmount());
        o.addProperty("material", r.getMaterial());
        o.addProperty("count", r.getCount());
        o.addProperty("icon", r.getIcon());
        return o;
    }
}
