package ru.voidrp.battlepass.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import ru.voidrp.battlepass.quest.BpActiveQuest;
import ru.voidrp.battlepass.quest.BpQuestStorage;

import java.util.List;

/**
 * Builds today's Battle Pass quest snapshot (free + premium, with progress and XP reward)
 * and pushes it to the backend so the WebGUI can render the daily quests. Best-effort;
 * network work runs off the main thread.
 */
public final class BpQuestSync {

    private BpQuestSync() {}

    public static void push(Plugin plugin, BpQuestStorage quests, PremiumStorage premium,
                            BackendSyncClient backend, Player player) {
        if (backend == null || !backend.isConfigured() || player == null) return;

        BpQuestStorage.PlayerQuestRecord rec = quests.getToday(player.getUniqueId());
        boolean hasPremium = premium.hasPremium(player.getUniqueId());

        JsonObject body = new JsonObject();
        body.addProperty("minecraft_nickname", player.getName());
        body.addProperty("date", rec.getDate());
        body.addProperty("has_premium", hasPremium);
        body.add("free", questArray(rec.getFreeQuests()));
        body.add("premium", questArray(rec.getPremiumQuests()));
        String json = body.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> backend.pushQuests(json));
    }

    private static JsonArray questArray(List<BpActiveQuest> quests) {
        JsonArray arr = new JsonArray();
        for (BpActiveQuest q : quests) {
            JsonObject o = new JsonObject();
            o.addProperty("id", q.getTemplateId());
            o.addProperty("name", q.getDisplayName());
            o.addProperty("description", q.getDescription());
            o.addProperty("type", q.getType() != null ? q.getType().name().toLowerCase() : null);
            o.addProperty("progress", q.getProgress());
            o.addProperty("required", q.getRequired());
            o.addProperty("xp", q.getXpReward());
            o.addProperty("completed", q.isCompleted());
            arr.add(o);
        }
        return arr;
    }
}
