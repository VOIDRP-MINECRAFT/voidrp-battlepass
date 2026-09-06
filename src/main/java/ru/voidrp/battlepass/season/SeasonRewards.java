package ru.voidrp.battlepass.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import ru.voidrp.battlepass.data.BackendSyncClient;

/**
 * Source of the per-level reward definitions.
 *
 * <p>Primary source is the backend (admin-edited table, per season); if the backend is
 * unreachable or returns nothing the bundled {@code rewards.yml} is used as a fallback.
 * This lets the admin panel add/remove/replace rewards without touching the plugin jar.
 */
public final class SeasonRewards {

    private Map<Integer, BpReward> freeRewards = new HashMap<>();
    private Map<Integer, BpReward> premiumRewards = new HashMap<>();

    private final JavaPlugin plugin;
    private final Logger log;
    private BackendSyncClient backend;   // nullable — set after construction

    public SeasonRewards(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        reload();
    }

    /** Wire the backend client so {@link #reload()} can pull the admin-edited table. */
    public void setBackend(BackendSyncClient backend) {
        this.backend = backend;
    }

    public void reload() {
        // 1) try the backend (admin-edited, authoritative)
        if (backend != null && backend.isConfigured()) {
            String season = Season.currentKey();
            JsonObject resp = backend.fetchRewards(season);
            if (resp != null && loadFromBackend(resp)) {
                log.info("[BattlePass] Loaded " + freeRewards.size() + " free and "
                        + premiumRewards.size() + " premium rewards from backend (season " + season + ").");
                return;
            }
            log.warning("[BattlePass] Backend rewards unavailable — falling back to rewards.yml.");
        }
        // 2) fall back to the bundled YAML
        loadFromYaml();
    }

    // ── Backend JSON → reward maps ───────────────────────────────────────────
    private boolean loadFromBackend(JsonObject resp) {
        try {
            Map<Integer, BpReward> free = parseBackendTrack(resp, "free");
            Map<Integer, BpReward> premium = parseBackendTrack(resp, "premium");
            if (free.isEmpty() && premium.isEmpty()) return false;
            freeRewards = Collections.unmodifiableMap(free);
            premiumRewards = Collections.unmodifiableMap(premium);
            return true;
        } catch (Exception e) {
            log.warning("[BattlePass] Failed to parse backend rewards: " + e.getMessage());
            return false;
        }
    }

    private Map<Integer, BpReward> parseBackendTrack(JsonObject resp, String track) {
        Map<Integer, BpReward> map = new HashMap<>();
        if (!resp.has(track) || !resp.get(track).isJsonObject()) return map;
        JsonObject obj = resp.getAsJsonObject(track);
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            int level;
            try {
                level = Integer.parseInt(e.getKey());
            } catch (NumberFormatException nfe) {
                continue;
            }
            if (!e.getValue().isJsonObject()) continue;
            JsonObject r = e.getValue().getAsJsonObject();
            String typeStr = str(r, "type", "MONEY").toUpperCase();
            BpRewardType type;
            try {
                type = BpRewardType.valueOf(typeStr);
            } catch (IllegalArgumentException iae) {
                log.warning("[BattlePass] Unknown backend reward type '" + typeStr + "' at level " + level);
                continue;
            }
            BpReward reward = switch (type) {
                case MONEY -> new BpReward(BpRewardType.MONEY, dbl(r, "amount"));
                case EXP -> new BpReward(BpRewardType.EXP, dbl(r, "amount"));
                case VOIDCOIN -> new BpReward(BpRewardType.VOIDCOIN, dbl(r, "amount"));
                case ITEM -> {
                    String mat = str(r, "material", "PAPER");
                    int count = r.has("count") && !r.get("count").isJsonNull() ? r.get("count").getAsInt() : 1;
                    String name = str(r, "displayName", mat);
                    yield new BpReward(mat, count, name);
                }
                case COMMAND -> {
                    String cmd = str(r, "command", "");
                    String name = str(r, "displayName", "Награда");
                    String icon = r.has("icon") && !r.get("icon").isJsonNull() ? r.get("icon").getAsString() : null;
                    if (icon == null) {
                        for (String tok : cmd.split(" ")) {
                            if (tok.contains(":") && !tok.startsWith("minecraft:give") && !tok.startsWith("/minecraft:give")) { icon = tok; break; }
                        }
                    }
                    yield new BpReward(cmd, name, icon);
                }
            };
            map.put(level, reward);
        }
        return map;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static double dbl(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
    }

    // ── Bundled YAML fallback ────────────────────────────────────────────────
    private void loadFromYaml() {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        if (!file.exists()) {
            plugin.saveResource("rewards.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        freeRewards = loadTrack(cfg, "free");
        premiumRewards = loadTrack(cfg, "premium");
        log.info("[BattlePass] Loaded " + freeRewards.size() + " free rewards and "
                + premiumRewards.size() + " premium rewards (rewards.yml).");
    }

    private Map<Integer, BpReward> loadTrack(YamlConfiguration cfg, String section) {
        Map<Integer, BpReward> map = new HashMap<>();
        if (!cfg.isConfigurationSection(section)) return map;
        for (String key : cfg.getConfigurationSection(section).getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                log.warning("[BattlePass] Invalid reward level key '" + key + "' in section '" + section + "'");
                continue;
            }
            String path = section + "." + key;
            String typeStr = cfg.getString(path + ".type", "MONEY").toUpperCase();
            BpRewardType type;
            try {
                type = BpRewardType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                log.warning("[BattlePass] Unknown reward type '" + typeStr + "' at level " + level);
                continue;
            }
            BpReward reward = switch (type) {
                case MONEY -> new BpReward(BpRewardType.MONEY, cfg.getDouble(path + ".amount", 0));
                case EXP -> new BpReward(BpRewardType.EXP, cfg.getDouble(path + ".amount", 0));
                case VOIDCOIN -> new BpReward(BpRewardType.VOIDCOIN, cfg.getDouble(path + ".amount", 0));
                case ITEM -> {
                    String mat = cfg.getString(path + ".material", "PAPER");
                    int count = cfg.getInt(path + ".count", 1);
                    String name = cfg.getString(path + ".displayName", mat);
                    yield new BpReward(mat, count, name);
                }
                case COMMAND -> {
                    String cmd = cfg.getString(path + ".command", "");
                    String name = cfg.getString(path + ".displayName", "Награда");
                    String icon = cfg.getString(path + ".icon", null);
                    if (icon == null) {
                        for (String tok : cmd.split(" ")) {   // fall back to the give id in the command
                            if (tok.contains(":") && !tok.startsWith("minecraft:give") && !tok.startsWith("/minecraft:give")) { icon = tok; break; }
                        }
                    }
                    yield new BpReward(cmd, name, icon);
                }
            };
            map.put(level, reward);
        }
        return Collections.unmodifiableMap(map);
    }

    public BpReward getFreeReward(int level) {
        return freeRewards.get(level);
    }

    public BpReward getPremiumReward(int level) {
        return premiumRewards.get(level);
    }

    public Map<Integer, BpReward> getAllFreeRewards() {
        return freeRewards;
    }

    public Map<Integer, BpReward> getAllPremiumRewards() {
        return premiumRewards;
    }
}
