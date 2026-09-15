package ru.voidrp.battlepass.listener;

import org.bukkit.entity.Player;

import ru.voidrp.battlepass.data.BattlePassData;
import ru.voidrp.battlepass.data.BattlePassStorage;
import ru.voidrp.battlepass.data.PremiumStorage;
import ru.voidrp.battlepass.season.BpReward;
import ru.voidrp.battlepass.season.BpRewardType;
import ru.voidrp.battlepass.season.SeasonRewards;
import ru.voidrp.battlepass.season.ZoneGates;

import java.util.UUID;

/**
 * The one place a Battle Pass reward is claimed — the chest GUI, the choice GUI and
 * {@code /bp claim} (WebGUI) all go through it, so level/premium/zone-gate checks and
 * choice handling can't drift apart between them.
 */
public final class RewardClaimService {

    public enum Result { OK, NEED_CHOICE, FAILED }

    private final BattlePassStorage storage;
    private final PremiumStorage premiumStorage;
    private final SeasonRewards seasonRewards;
    private final BpProgressListener progressListener;

    public RewardClaimService(BattlePassStorage storage, PremiumStorage premiumStorage,
                              SeasonRewards seasonRewards, BpProgressListener progressListener) {
        this.storage = storage;
        this.premiumStorage = premiumStorage;
        this.seasonRewards = seasonRewards;
        this.progressListener = progressListener;
    }

    public BpReward rewardAt(boolean premium, int level) {
        return premium ? seasonRewards.getPremiumReward(level) : seasonRewards.getFreeReward(level);
    }

    /**
     * Claims the reward at (track, level). For a choice reward {@code option} is the 0-based
     * variant; {@code null} returns {@link Result#NEED_CHOICE} without claiming anything.
     * Explains every refusal to the player.
     */
    public Result claim(Player player, boolean premium, int level, Integer option) {
        UUID uuid = player.getUniqueId();
        BattlePassData data = storage.get(uuid);
        BpReward reward = rewardAt(premium, level);
        if (reward == null) {
            player.sendMessage("§c✦ Нет награды для этого уровня.");
            return Result.FAILED;
        }
        if (premium && !premiumStorage.hasPremium(uuid)) {
            player.sendMessage("§c✦ Требуется Premium для получения этой награды.");
            return Result.FAILED;
        }
        if (data.getLevel() < level) {
            player.sendMessage("§c✦ Необходим уровень §e" + level + " §cдля получения этой награды.");
            return Result.FAILED;
        }
        ZoneGates.Gate gate = ZoneGates.blocking(uuid, level);
        if (gate != null) {
            player.sendMessage("§c✦ Награды после уровня §e" + gate.level() + " §cоткроются, когда ты откроешь §6" + gate.label() + "§c.");
            return Result.FAILED;
        }
        if (premium ? data.isPremiumClaimed(level) : data.isFreeClaimed(level)) {
            player.sendMessage("§c✦ Эта награда уже получена.");
            return Result.FAILED;
        }

        BpReward toGive = reward;
        if (reward.getType() == BpRewardType.CHOICE) {
            if (option == null) return Result.NEED_CHOICE;
            if (option < 0 || option >= reward.getOptions().size()) {
                player.sendMessage("§c✦ Такого варианта нет.");
                return Result.FAILED;
            }
            toGive = reward.getOptions().get(option);
        }

        if (premium) data.claimPremium(level); else data.claimFree(level);
        storage.save(uuid);
        progressListener.giveReward(player, toGive);
        return Result.OK;
    }
}
