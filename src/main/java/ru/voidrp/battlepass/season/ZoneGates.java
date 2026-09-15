package ru.voidrp.battlepass.season;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import ru.voidrp.battlepass.data.BackendSyncClient;
import ru.voidrp.battlepass.data.BattlePassData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Zone gates of the active season: levels past a gate need a progression tier (epoch).
 *
 * <p>Gates come with the season from the backend; a player's unlocked tiers are fetched from
 * the backend and cached (refreshed on join, when the pass is opened and periodically). Until a
 * player's tiers are known nothing is blocked — a backend outage must not lock people out.
 */
public final class ZoneGates {

    public record Gate(int level, String tier, String label) { }

    private static volatile List<Gate> gates = List.of();
    private static final Map<UUID, Set<String>> TIERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CAP_NOTICE_AT = new ConcurrentHashMap<>();
    private static final long CAP_NOTICE_EVERY_MS = 10 * 60 * 1000L;

    private ZoneGates() {}

    /** Replace gates from the season payload ({@code [{level, tier, label}]}); null/absent = none. */
    public static void load(JsonElement arr, Logger log) {
        List<Gate> out = new ArrayList<>();
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement el : (JsonArray) arr) {
                if (!el.isJsonObject()) continue;
                JsonObject g = el.getAsJsonObject();
                try {
                    int level = g.get("level").getAsInt();
                    String tier = g.get("tier").getAsString();
                    String label = g.has("label") && !g.get("label").isJsonNull() ? g.get("label").getAsString() : tier;
                    out.add(new Gate(level, tier, label));
                } catch (Exception e) {
                    log.warning("[BattlePass] Bad zone gate " + g + ": " + e.getMessage());
                }
            }
        }
        out.sort(Comparator.comparingInt(Gate::level));
        gates = List.copyOf(out);
        log.info("[BattlePass] Zone gates: " + (out.isEmpty() ? "none" : out));
    }

    public static List<Gate> all() { return gates; }

    /** Blocking fetch of the player's tiers; call off the main thread. */
    public static void refresh(BackendSyncClient backend, UUID uuid) {
        if (gates.isEmpty() || backend == null || !backend.isConfigured()) return;
        Set<String> tiers = backend.fetchPlayerTiers(uuid.toString());
        if (tiers != null) TIERS.put(uuid, tiers);
    }

    public static void forget(UUID uuid) {
        TIERS.remove(uuid);
    }

    public static boolean isUnlocked(UUID uuid, Gate gate) {
        Set<String> tiers = TIERS.get(uuid);
        return tiers == null || tiers.contains(gate.tier());
    }

    /** The first gate the player hasn't passed, or null. */
    public static Gate firstClosed(UUID uuid) {
        for (Gate g : gates) {
            if (!isUnlocked(uuid, g)) return g;
        }
        return null;
    }

    /** The closed gate standing before {@code level} (level is past it), or null if reachable. */
    public static Gate blocking(UUID uuid, int level) {
        Gate g = firstClosed(uuid);
        return g != null && level > g.level() ? g : null;
    }

    /**
     * Highest total XP the player may hold: the last XP of the gate level (the bar fills up,
     * the next level waits for the epoch). {@link Long#MAX_VALUE} when nothing blocks.
     */
    public static long xpCap(UUID uuid) {
        Gate g = firstClosed(uuid);
        if (g == null || g.level() >= BattlePassData.MAX_LEVEL) return Long.MAX_VALUE;
        return (long) g.level() * BattlePassData.XP_PER_LEVEL - 1;
    }

    /** Tells a capped player why the pass stopped — at most once per {@link #CAP_NOTICE_EVERY_MS}. */
    public static void noticeIfCapped(Player player, BattlePassData data) {
        UUID uuid = player.getUniqueId();
        long cap = xpCap(uuid);
        if (cap == Long.MAX_VALUE || data.getXp() < cap) return;
        long now = System.currentTimeMillis();
        if (now - CAP_NOTICE_AT.getOrDefault(uuid, 0L) < CAP_NOTICE_EVERY_MS) return;
        CAP_NOTICE_AT.put(uuid, now);
        Gate g = firstClosed(uuid);
        player.sendMessage("§6§l✦ §eBattle Pass: §fуровень " + g.level() + " — граница зоны. §7Чтобы качаться дальше, открой §6"
                + g.label() + "§7.");
    }
}
