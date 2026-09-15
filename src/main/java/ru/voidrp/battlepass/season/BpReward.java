package ru.voidrp.battlepass.season;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BpReward {

    private final BpRewardType type;
    private final double amount;
    private final String material;
    private final int count;
    private final String displayName;
    private final String command;
    private final String icon;   // item id for the WebGUI texture ("minecraft:diamond" | "ftbevolution:ruby_gem")

    // Random amounts: count/amount are the lower bound; 0 = fixed. Rolled at claim time.
    private int countMax;
    private double amountMax;
    // CHOICE: the variants the player picks from (each a plain reward).
    private List<BpReward> options = List.of();

    /** Constructor for MONEY / EXP / VOIDCOIN rewards. */
    public BpReward(BpRewardType type, double amount) {
        this.type = type;
        this.amount = amount;
        this.material = null;
        this.count = 0;
        this.displayName = null;
        this.command = null;
        this.icon = null;
    }

    /** Constructor for ITEM rewards (icon = the vanilla material). */
    public BpReward(String material, int count, String displayName) {
        this.type = BpRewardType.ITEM;
        this.amount = 0;
        this.material = material;
        this.count = count;
        this.displayName = displayName;
        this.command = null;
        this.icon = material != null ? "minecraft:" + material.toLowerCase() : null;
    }

    /** Constructor for COMMAND rewards with a count (rolled into the {@code {count}} placeholder). */
    public BpReward(String command, String displayName, String icon, int count) {
        this.type = BpRewardType.COMMAND;
        this.amount = 0;
        this.material = null;
        this.count = count;
        this.displayName = displayName;
        this.command = command;
        this.icon = icon;
    }

    /** Constructor for CHOICE rewards. */
    public BpReward(String displayName, String icon, List<BpReward> options) {
        this.type = BpRewardType.CHOICE;
        this.amount = 0;
        this.material = null;
        this.count = 0;
        this.displayName = displayName;
        this.command = null;
        this.icon = icon;
        this.options = List.copyOf(options);
    }

    /** Constructor for COMMAND rewards; icon = the given item id so the WebGUI can show its texture. */
    public BpReward(String command, String displayName, String icon) {
        this.type = BpRewardType.COMMAND;
        this.amount = 0;
        this.material = null;
        this.count = 0;
        this.displayName = displayName;
        this.command = command;
        this.icon = icon;
    }

    public BpRewardType getType() { return type; }
    public double getAmount() { return amount; }
    public String getMaterial() { return material; }
    public int getCount() { return count; }
    public String getDisplayName() { return displayName; }
    public String getCommand() { return command; }
    public String getIcon() { return icon; }
    public int getCountMax() { return countMax; }
    public double getAmountMax() { return amountMax; }
    public List<BpReward> getOptions() { return options; }

    /** Sets the random upper bounds (≤ lower bound = fixed amount). */
    public BpReward withRange(int countMax, double amountMax) {
        this.countMax = countMax;
        this.amountMax = amountMax;
        return this;
    }

    public boolean hasCountRange() { return countMax > count; }
    public boolean hasAmountRange() { return amountMax > amount; }

    /** A count in [count, countMax], or the fixed count. */
    public int rollCount() {
        return hasCountRange() ? ThreadLocalRandom.current().nextInt(count, countMax + 1) : count;
    }

    /** An amount in [amount, amountMax] (whole numbers), or the fixed amount. */
    public double rollAmount() {
        if (!hasAmountRange()) return amount;
        return ThreadLocalRandom.current().nextLong((long) amount, (long) amountMax + 1);
    }

    /** "32" or "32–48" — for lore and chat. */
    public String countText() {
        return hasCountRange() ? count + "–" + countMax : String.valueOf(count);
    }

    public String amountText() {
        return hasAmountRange() ? (long) amount + "–" + (long) amountMax : String.valueOf((long) amount);
    }
}
