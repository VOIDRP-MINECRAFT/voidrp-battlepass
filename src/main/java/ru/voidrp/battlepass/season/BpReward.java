package ru.voidrp.battlepass.season;

public final class BpReward {

    private final BpRewardType type;
    private final double amount;
    private final String material;
    private final int count;
    private final String displayName;
    private final String command;
    private final String icon;   // item id for the WebGUI texture ("minecraft:diamond" | "ftbevolution:ruby_gem")

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
}
