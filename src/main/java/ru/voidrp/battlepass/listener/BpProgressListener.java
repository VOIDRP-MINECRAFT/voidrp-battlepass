package ru.voidrp.battlepass.listener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import ru.voidrp.gamesync.event.PlayerMarketTradeEvent;
import ru.voidrp.battlepass.data.BackendSyncClient;
import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.quest.BpActiveQuest;
import ru.voidrp.battlepass.quest.BpQuestStorage;
import ru.voidrp.battlepass.quest.BpQuestType;
import ru.voidrp.battlepass.season.BpReward;
import ru.voidrp.battlepass.season.SeasonRewards;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BpProgressListener implements Listener {

    private static final Set<String> VANILLA_BOSSES = Set.of(
            "minecraft:wither",
            "minecraft:ender_dragon",
            "minecraft:elder_guardian"
    );

    private static final Set<String> MODDED_BOSSES = Set.of(
            "iceandfire:fire_dragon",
            "iceandfire:ice_dragon",
            "iceandfire:sea_serpent",
            "twilightforest:naga",
            "twilightforest:lich",
            "twilightforest:hydra",
            "twilightforest:ur_ghast",
            "cataclysm:lich",
            "cataclysm:harbinger_of_doom",
            "mowziesmobs:frostmaw",
            "mowziesmobs:barako"
    );

    private final BattlePassStorage storage;
    private final PremiumStorage premiumStorage;
    private final BpQuestStorage questStorage;
    private final SeasonRewards seasonRewards;
    private final Economy economy;
    private Plugin plugin;
    private BackendSyncClient backendClient;

    // Per-player daily "grind" XP tracker: uuid -> {epochDay, earnedToday}.
    // NOT evicted on quit so relogging can't reset the cap; persists within the same day.
    private final java.util.Map<UUID, long[]> grindXpToday = new java.util.concurrent.ConcurrentHashMap<>();
    // Suppress grind XP briefly after a reward claim: claimed items trigger advancements/pickups,
    // which would otherwise level up the pass — the exact circular XP the season removed.
    private final java.util.Map<UUID, Long> claimSuppressUntil = new java.util.concurrent.ConcurrentHashMap<>();

    public BpProgressListener(BattlePassStorage storage, PremiumStorage premiumStorage,
                               BpQuestStorage questStorage, SeasonRewards seasonRewards,
                               Economy economy) {
        this.storage = storage;
        this.premiumStorage = premiumStorage;
        this.questStorage = questStorage;
        this.seasonRewards = seasonRewards;
        this.economy = economy;
    }

    public void setPlugin(Plugin plugin) { this.plugin = plugin; }
    public void setBackendClient(BackendSyncClient client) { this.backendClient = client; }

    // ── Login / Quit ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        if (plugin == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            premiumStorage.syncFromBackend(uuid, name);
            pushProgressAsync(uuid);
            // Nudge: unclaimed rewards waiting → drives opening /bp.
            int unclaimed = countUnclaimed(uuid);
            if (unclaimed > 0 && backendClient != null && backendClient.isConfigured()) {
                backendClient.pushNotification(name, "battlepass",
                        "Награды Battle Pass ждут! (" + unclaimed + ")",
                        "У тебя " + unclaimed + " неполученных наград сезона. Забери их во вкладке Battle Pass.",
                        "battlepass", "gold", "route", "battlepass", "Открыть");
            }
            // Weekend x2 XP announce.
            if (xpMultiplier() > 1.0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage("§6§l✦ §eВыходные: §a×2 XP Battle Pass §7— самое время качать пасс!");
                });
            }
        });
    }

    /** Number of unclaimed rewards the player can already take (free + premium if they have it). */
    private int countUnclaimed(UUID uuid) {
        BattlePassData data = storage.get(uuid);
        if (data == null) return 0;
        boolean prem = premiumStorage.hasPremium(uuid);
        int lvl = data.getLevel();
        int n = 0;
        for (int L = 1; L <= lvl; L++) {
            if (seasonRewards.getFreeReward(L) != null && !data.isFreeClaimed(L)) n++;
            if (prem && seasonRewards.getPremiumReward(L) != null && !data.isPremiumClaimed(L)) n++;
        }
        return n;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        premiumStorage.evict(e.getPlayer().getUniqueId());
    }

    // ── Entity Death ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Entity entity = event.getEntity();
        String entityKey = entity.getType().getKey().toString();

        // XP for kills (subject to the daily grind cap)
        long xpGain;
        if (VANILLA_BOSSES.contains(entityKey)) {
            xpGain = 500;
        } else if (MODDED_BOSSES.contains(entityKey)) {
            xpGain = 150;
        } else {
            xpGain = 3;
        }

        addGrindXp(killer, xpGain);

        // Quest progress: KILL quests
        tickQuestProgress(killer, BpQuestType.KILL, entityKey, 1);
    }

    // ── Block Break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String blockKey = event.getBlock().getType().name();

        // Quest progress: MINE quests
        tickQuestProgress(player, BpQuestType.MINE, blockKey, 1);
    }

    // ── Item Pickup ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        String materialKey = event.getItem().getItemStack().getType().name();
        int amount = event.getItem().getItemStack().getAmount();

        // Quest progress: COLLECT quests
        tickQuestProgress(player, BpQuestType.COLLECT, materialKey, amount);
    }

    // ── Fishing ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();

        // Quest progress: FISH quests (ANY target)
        tickQuestProgress(player, BpQuestType.FISH, "ANY", 1);
    }

    // ── Market Trades ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMarketTrade(PlayerMarketTradeEvent e) {
        Player player = Bukkit.getPlayerExact(e.getPlayerName());
        if (player == null || !player.isOnline()) return;

        // XP based on trade value: 1 XP per 300 coins, min 500 coins to award anything, cap 150 XP/trade
        // (subject to the daily grind cap)
        double gross = e.getGrossTotal();
        if (gross < 500.0) return;
        long xp = Math.min(150L, (long) (gross / 300.0));
        if (xp <= 0) return;
        addGrindXp(player, xp);

        // Quest progress
        BpQuestType type = e.getRole() == PlayerMarketTradeEvent.Role.SELLER
                ? BpQuestType.MARKET_SELL
                : BpQuestType.MARKET_BUY;
        tickQuestProgress(player, type, e.getItemKey(), e.getAmount());
    }

    // ── Advancement ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Advancement adv = event.getAdvancement();
        // Skip recipe unlocks
        if (adv.getKey().getKey().startsWith("recipes/")) return;
        addGrindXp(event.getPlayer(), 100);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Grants "grind" XP (mob kills, market trades, advancements) subject to a per-player
     * daily cap ({@code daily-xp-cap} in config.yml, default 8000; 0 disables the cap).
     * Quest and daily-quest-plugin XP bypass this cap since they are naturally limited.
     */
    /** Days before season end that count as the "finale" (x2 XP hype window). */
    public static final int FINALE_DAYS = 7;
    private static final double FINALE_MULT = 2.0;

    /** Global Battle Pass XP multiplier: x2 on weekends, x2 during the season finale (stacks). */
    public static double xpMultiplier() {
        double m = 1.0;
        java.time.DayOfWeek d = java.time.LocalDate.now().getDayOfWeek();
        if (d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY) m *= 2.0;
        if (ru.voidrp.battlepass.season.Season.isActive()
                && ru.voidrp.battlepass.season.Season.daysUntilReset() <= FINALE_DAYS) {
            m *= FINALE_MULT;   // last week of the season — final push
        }
        return m;
    }

    /** True while the season is in its final-week finale. */
    public static boolean isFinale() {
        return ru.voidrp.battlepass.season.Season.isActive()
                && ru.voidrp.battlepass.season.Season.daysUntilReset() <= FINALE_DAYS;
    }

    private void addGrindXp(Player player, long amount) {
        if (amount <= 0) return;
        // Skip XP from items/advancements triggered by a just-claimed reward.
        if (System.currentTimeMillis() < claimSuppressUntil.getOrDefault(player.getUniqueId(), 0L)) return;
        double mult = xpMultiplier() * catchUpMultiplier(player);
        amount = Math.round(amount * mult);
        long cap = plugin != null ? plugin.getConfig().getLong("daily-xp-cap", 8000L) : 8000L;
        if (cap > 0) cap = Math.round(cap * mult);   // weekend also raises the cap so 2x actually lands
        if (cap > 0) {
            long today = java.time.LocalDate.now().toEpochDay();
            long[] rec = grindXpToday.get(player.getUniqueId());
            if (rec == null || rec[0] != today) {
                rec = new long[]{today, 0L};
                grindXpToday.put(player.getUniqueId(), rec);
            }
            long remaining = cap - rec[1];
            if (remaining <= 0) return;
            if (amount > remaining) amount = remaining;
            rec[1] += amount;
        }
        addXpAndNotify(player, amount);
    }

    /**
     * Catch-up boost for players behind the season pace: the further below the expected
     * level (season fraction × MAX_LEVEL) they are, the bigger the grind-XP multiplier
     * (up to 2x). Keeps late joiners from giving up. Returns 1.0 when on pace or unknown.
     */
    private double catchUpMultiplier(Player player) {
        java.time.LocalDate start = ru.voidrp.battlepass.season.Season.getStartDate();
        java.time.LocalDate end = ru.voidrp.battlepass.season.Season.getEndDate();
        if (start == null || end == null || !ru.voidrp.battlepass.season.Season.isActive()) return 1.0;
        long total = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        if (total <= 0) return 1.0;
        long elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, java.time.LocalDate.now());
        double frac = Math.max(0.0, Math.min(1.0, elapsed / (double) total));
        int expected = (int) Math.round(frac * BattlePassData.MAX_LEVEL);
        if (expected <= 1) return 1.0;
        int actual = storage.get(player.getUniqueId()).getLevel();
        if (actual >= expected * 0.7) return 1.0;   // within pace — no boost
        double behind = (expected - actual) / (double) expected;   // 0..1
        return Math.min(2.0, 1.0 + behind);
    }

    private void addXpAndNotify(Player player, long amount) {
        UUID uuid = player.getUniqueId();
        amount = ru.voidrp.battlepass.NationResearchBonus.apply(player, amount);
        int oldLevel = storage.addXp(uuid, amount);
        BattlePassData data = storage.get(uuid);
        int newLevel = data.getLevel();
        if (newLevel > oldLevel) {
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                final int displayLvl = lvl;
                Bukkit.getScheduler().runTask(
                        Bukkit.getPluginManager().getPlugin("VoidRpBattlePass"),
                        () -> {
                            if (!player.isOnline()) return;
                            player.sendMessage("§6§l✦ §eБаттл Пасс: Уровень " + displayLvl + "! §6/bp для наград");
                            if (displayLvl >= BattlePassData.MAX_LEVEL) {
                                player.sendTitle("§6§l✦ УРОВЕНЬ МАКСИМУМ!", "§eBattle Pass — все награды доступны!", 10, 80, 20);
                            } else {
                                player.sendTitle("§6§l✦ Уровень " + displayLvl + "!", "§eBattle Pass — §7/bp для наград", 10, 60, 20);
                            }
                            spawnLevelUpFirework(player);
                        });
            }
            // Push updated progress to backend
            if (backendClient != null && backendClient.isConfigured() && plugin != null) {
                final int finalLevel = newLevel;
                final long finalXp = data.getXp();
                final int finalPrestige = data.getPrestige();
                final String nick = player.getName();
                Bukkit.getScheduler().runTaskAsynchronously(plugin,
                        () -> backendClient.pushProgress(uuid.toString(), nick, ru.voidrp.battlepass.season.Season.currentKey(), finalLevel, finalXp, finalPrestige));
            }
        }
        // Prestige (past level 100) — checked on every gain, not just on a visible level-up.
        ru.voidrp.battlepass.PrestigeGrant.check(plugin, storage, player);
    }

    private void pushProgressAsync(UUID uuid) {
        if (backendClient == null || !backendClient.isConfigured()) return;
        BattlePassData data = storage.get(uuid);
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String nick = op.getName() != null ? op.getName() : uuid.toString();
        backendClient.pushProgress(uuid.toString(), nick, ru.voidrp.battlepass.season.Season.currentKey(), data.getLevel(), data.getXp(), data.getPrestige());
    }

    private void tickQuestProgress(Player player, BpQuestType type, String key, int amount) {
        UUID uuid = player.getUniqueId();
        boolean hasPremium = premiumStorage.hasPremium(uuid);
        BpQuestStorage.PlayerQuestRecord record = questStorage.getToday(uuid);

        List<BpActiveQuest> allQuests = new java.util.ArrayList<>(record.getFreeQuests());
        if (hasPremium) allQuests.addAll(record.getPremiumQuests());

        for (BpActiveQuest quest : allQuests) {
            if (quest.isRewardClaimed()) continue;
            if (quest.getType() != type) continue;
            if (!matchesTarget(quest.getTarget(), key, type)) continue;

            boolean wasDone = quest.isCompleted();
            quest.addProgress(amount);
            if (!wasDone && quest.isCompleted()) {
                // Quest just completed — award XP
                int xpReward = quest.getXpReward();
                quest.setRewardClaimed(true);
                questStorage.save(uuid);

                addXpAndNotify(player, xpReward);
                player.sendMessage("§b⭐ §aBP квест выполнен: §f" + quest.getDisplayName()
                        + " §b+" + xpReward + " XP");
                if (backendClient != null && backendClient.isConfigured() && plugin != null) {
                    ru.voidrp.battlepass.data.BpQuestSync.push(plugin, questStorage, premiumStorage, backendClient, player);
                }
            }
        }
    }

    private boolean matchesTarget(String questTarget, String eventKey, BpQuestType type) {
        if (type == BpQuestType.FISH || type == BpQuestType.MARKET_SELL || type == BpQuestType.MARKET_BUY) {
            if (questTarget.equalsIgnoreCase("ANY")) return true;
        }
        if (questTarget.equalsIgnoreCase("any")) return true;
        return questTarget.equalsIgnoreCase(eventKey);
    }

    /** Called externally when a level reward is claimed to give the item/money/exp. */
    public void giveReward(Player player, BpReward reward) {
        // Don't let items granted by this claim award grind XP (advancements/pickups) and level the pass.
        claimSuppressUntil.put(player.getUniqueId(), System.currentTimeMillis() + 4000L);
        switch (reward.getType()) {
            case MONEY -> {
                if (economy != null) {
                    economy.depositPlayer(player, reward.getAmount());
                    player.sendMessage("§6💰 Получено §e" + (long) reward.getAmount() + " монет§6!");
                }
            }
            case EXP -> {
                player.giveExp((int) reward.getAmount());
                player.sendMessage("§a✨ Получено §e" + (int) reward.getAmount() + " опыта§a!");
            }
            case VOIDCOIN -> {
                long vc = (long) reward.getAmount();
                // granted via the game-sync admin command (runs as console → has permission)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vrgs voidcoin " + player.getName() + " " + vc);
                player.sendMessage("§5◆ Получено §d" + vc + " Void Coin§5!");
            }
            case ITEM -> {
                org.bukkit.Material mat;
                try {
                    mat = org.bukkit.Material.valueOf(reward.getMaterial().toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[BattlePass] Unknown material '" + reward.getMaterial() + "' — giving PAPER instead.");
                    mat = org.bukkit.Material.PAPER;
                }
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat, Math.max(1, reward.getCount()));
                var leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                    player.sendMessage("§e⚠ Инвентарь полон — предмет выброшен рядом с тобой!");
                }
                player.sendMessage("§b📦 Получено: §f" + (reward.getDisplayName() != null ? reward.getDisplayName() : mat.name()) + "§b!");
            }
            case COMMAND -> {
                String cmd = reward.getCommand().replace("{player}", player.getName());
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                plugin.getLogger().info("[BattlePass] Reward command for " + player.getName() + ": " + cmd);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                player.sendMessage("§d🎁 Получена особая награда: §f"
                        + (reward.getDisplayName() != null ? reward.getDisplayName() : "Предмет") + "§d!");
            }
        }
    }

    private void spawnLevelUpFirework(Player player) {
        org.bukkit.Location loc = player.getLocation().add(0, 1, 0);
        org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework)
                loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.FIREWORK_ROCKET);
        org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(0);
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE)
                .withFade(org.bukkit.Color.WHITE)
                .flicker(true)
                .trail(true)
                .build());
        fw.setFireworkMeta(meta);
        fw.detonate();
    }
}
