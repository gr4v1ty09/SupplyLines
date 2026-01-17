package com.gr4v1ty.supplylines.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.*;

/**
 * Server-side configuration for SupplyLines. These values are synced to clients
 * and affect gameplay mechanics.
 */
public final class ServerConfig {

    // === Timing/Performance ===
    public final IntValue defaultRescanIntervalTicks;
    public final IntValue defaultStockSnapshotIntervalTicks;
    public final IntValue defaultRestockIntervalTicks;
    public final IntValue stagingTimeoutTicks;
    public final IntValue bufferWindowTicks;
    public final IntValue displayUpdateIntervalTicks;
    public final IntValue defaultDeliveryTicks;
    public final IntValue orderExpiryBufferTicks;
    public final IntValue defaultInvSigIntervalTicks;
    public final IntValue defaultStagingProcessIntervalTicks;

    // === Building Levels ===
    public final IntValue stockTickerRequiredLevel;
    public final IntValue restockPolicyRequiredLevel;

    // === Limits ===
    public final IntValue auxiliaryScanRadius;

    // === Request System ===
    public final IntValue resolverPriority;

    // === AI/Movement ===
    public final IntValue stateMachineTickRate;
    public final DoubleValue walkSpeed;
    public final DoubleValue arriveDistanceSq;
    public final IntValue inspectDurationTicks;

    // === Skill Scaling: Rescan ===
    public final IntValue rescanBase;
    public final IntValue rescanMin;
    public final DoubleValue rescanStrengthMultiplier;

    // === Skill Scaling: Snapshot ===
    public final IntValue snapshotBase;
    public final IntValue snapshotMin;
    public final DoubleValue snapshotDexterityMultiplier;

    // === Skill Scaling: Staging ===
    public final IntValue stagingBase;
    public final IntValue stagingMin;
    public final DoubleValue stagingStrengthMultiplier;

    // === Skill Scaling: Restock ===
    public final IntValue restockBase;
    public final IntValue restockMin;
    public final DoubleValue restockDexterityMultiplier;

    // === XP Awards: Stack/Food/Burnable ===
    public final DoubleValue stackXpBase;
    public final DoubleValue stackXpDivisor;
    public final DoubleValue stackXpCap;

    // === XP Awards: Tag ===
    public final DoubleValue tagXpBase;
    public final DoubleValue tagXpDivisor;
    public final DoubleValue tagXpCap;

    // === XP Awards: Tool ===
    public final DoubleValue toolXpFixed;

    // === XP Awards: StackList ===
    public final DoubleValue stackListXpBase;
    public final DoubleValue stackListXpItemDivisor;
    public final DoubleValue stackListXpItemCap;
    public final DoubleValue stackListXpTypeDivisor;
    public final DoubleValue stackListXpTypeCap;

    // === Display Settings ===
    public final IntValue itemNameTruncation;
    public final IntValue etaFormatThresholdSeconds;

    ServerConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("SupplyLines Server Configuration").push("server");

        // Timing/Performance
        builder.comment("Timing and Performance Settings").push("timing");

        defaultRescanIntervalTicks = builder
                .comment("Default interval for rack rescan when no worker skill applies (ticks).",
                        "20 ticks = 1 second.")
                .defineInRange("defaultRescanIntervalTicks", 400, 20, 2400);

        defaultStockSnapshotIntervalTicks = builder.comment("Default interval for stock snapshot updates (ticks).")
                .defineInRange("defaultStockSnapshotIntervalTicks", 200, 10, 1200);

        defaultRestockIntervalTicks = builder.comment("Default interval for restock policy checks (ticks).")
                .defineInRange("defaultRestockIntervalTicks", 600, 100, 3600);

        stagingTimeoutTicks = builder
                .comment("Timeout for staging requests before cancellation (ticks).", "Default 1200 = 60 seconds.")
                .defineInRange("stagingTimeoutTicks", 1200, 200, 6000);

        bufferWindowTicks = builder
                .comment("Window for batching multiple requests together (ticks).", "Default 60 = 3 seconds.")
                .defineInRange("bufferWindowTicks", 60, 10, 300);

        displayUpdateIntervalTicks = builder
                .comment("Interval for display board updates (ticks).", "Default 100 = 5 seconds.")
                .defineInRange("displayUpdateIntervalTicks", 100, 20, 600);

        defaultDeliveryTicks = builder
                .comment("Default assumed delivery time for ETA calculations (ticks).", "Default 400 = 20 seconds.")
                .defineInRange("defaultDeliveryTicks", 400, 100, 2400);

        orderExpiryBufferTicks = builder
                .comment("Fallback timeout after ETA before expiring undetected orders (ticks).",
                        "With postbox watching, this is only used when arrivals can't be detected.",
                        "Default 12000 = 10 minutes. Increase for very long-distance deliveries.")
                .defineInRange("orderExpiryBufferTicks", 12000, 200, 72000);

        defaultInvSigIntervalTicks = builder
                .comment("Interval for inventory signature refresh checks (ticks).", "Default 40 = 2 seconds.")
                .defineInRange("defaultInvSigIntervalTicks", 40, 10, 200);

        defaultStagingProcessIntervalTicks = builder
                .comment("Interval for staging process when no skill manager available (ticks).",
                        "Default 60 = 3 seconds.")
                .defineInRange("defaultStagingProcessIntervalTicks", 60, 10, 300);

        builder.pop();

        // Building Levels
        builder.comment("Building Level Requirements").push("buildingLevels");

        stockTickerRequiredLevel = builder
                .comment("Building level required for Stock Ticker functionality.",
                        "Enables access to the colony stock network.")
                .defineInRange("stockTickerRequiredLevel", 4, 1, 5);

        restockPolicyRequiredLevel = builder
                .comment("Building level required for Restock Policy functionality.",
                        "Enables automatic restocking from remote suppliers.")
                .defineInRange("restockPolicyRequiredLevel", 5, 1, 5);

        builder.pop();

        // Limits
        builder.comment("System Limits").push("limits");

        auxiliaryScanRadius = builder.comment("Radius for scanning auxiliary blocks like Stock Tickers (blocks).")
                .defineInRange("auxiliaryScanRadius", 16, 8, 64);

        builder.pop();

        // Request System
        builder.comment("Request System Settings").push("requestSystem");

        resolverPriority = builder.comment("Priority for SupplyLines resolvers in the MineColonies request system.",
                "Lower values = higher priority.").defineInRange("resolverPriority", 80, 1, 200);

        builder.pop();

        // AI/Movement
        builder.comment("AI and Movement Settings").push("ai");

        stateMachineTickRate = builder
                .comment("Tick rate for the Stock Keeper AI state machine (game ticks).",
                        "Lower values = more responsive but higher CPU usage.", "Requires world reload to take effect.")
                .defineInRange("stateMachineTickRate", 20, 1, 200);

        walkSpeed = builder.comment("Walking speed multiplier for Stock Keeper AI.").defineInRange("walkSpeed", 1.0,
                0.5, 2.0);

        arriveDistanceSq = builder.comment("Squared distance threshold for arrival detection (blocks squared).",
                "Default 4.0 = 2 blocks.").defineInRange("arriveDistanceSq", 4.0, 1.0, 16.0);

        inspectDurationTicks = builder
                .comment("Duration of inspection pause at patrol points (state machine ticks).",
                        "Actual game ticks = this value * stateMachineTickRate.")
                .defineInRange("inspectDurationTicks", 4, 1, 20);

        builder.pop();

        // Skill Scaling
        builder.comment("Skill-Based Interval Scaling",
                "Controls how worker skills reduce action intervals (faster = more efficient).",
                "Formula: interval = max(min, base - skill * multiplier)",
                "  - base: Starting interval (ticks) for a worker with 0 skill",
                "  - min: Fastest possible interval (floor) regardless of skill",
                "  - multiplier: How much each skill point reduces the interval",
                "Example: base=400, min=60, mult=6.8, skill=50 -> max(60, 400-50*6.8) = max(60, 60) = 60 ticks")
                .push("skillScaling");

        builder.comment("Rescan interval: How often the Stock Keeper checks racks for inventory changes.",
                "Affected by Strength skill. Lower = faster detection of new items.").push("rescan");
        rescanBase = builder.comment("Interval (ticks) at skill level 0. Default 400 = 20 seconds.")
                .defineInRange("base", 400, 60, 1200);
        rescanMin = builder.comment("Minimum interval (ticks) at max skill. Default 60 = 3 seconds.")
                .defineInRange("min", 60, 10, 200);
        rescanStrengthMultiplier = builder.comment("Ticks reduced per Strength point.")
                .defineInRange("strengthMultiplier", 6.8, 0.1, 20.0);
        builder.pop();

        builder.comment("Snapshot interval: How often stock levels are updated for display boards.",
                "Affected by Dexterity skill. Lower = more responsive stock displays.").push("snapshot");
        snapshotBase = builder.comment("Interval (ticks) at skill level 0. Default 200 = 10 seconds.")
                .defineInRange("base", 200, 20, 600);
        snapshotMin = builder.comment("Minimum interval (ticks) at max skill. Default 20 = 1 second.")
                .defineInRange("min", 20, 5, 100);
        snapshotDexterityMultiplier = builder.comment("Ticks reduced per Dexterity point.")
                .defineInRange("dexterityMultiplier", 3.6, 0.1, 15.0);
        builder.pop();

        builder.comment("Staging interval: How often pending orders are processed for fulfillment.",
                "Affected by Strength skill. Lower = faster order processing.").push("staging");
        stagingBase = builder.comment("Interval (ticks) at skill level 0. Default 60 = 3 seconds.")
                .defineInRange("base", 60, 10, 200);
        stagingMin = builder.comment("Minimum interval (ticks) at max skill. Default 10 = 0.5 seconds.")
                .defineInRange("min", 10, 1, 50);
        stagingStrengthMultiplier = builder.comment("Ticks reduced per Strength point.")
                .defineInRange("strengthMultiplier", 1.0, 0.1, 5.0);
        builder.pop();

        builder.comment("Restock interval: How often restock policies are checked for items to order.",
                "Affected by Dexterity skill. Lower = faster restocking of low stock items.").push("restock");
        restockBase = builder.comment("Interval (ticks) at skill level 0. Default 600 = 30 seconds.")
                .defineInRange("base", 600, 200, 1800);
        restockMin = builder.comment("Minimum interval (ticks) at max skill. Default 200 = 10 seconds.")
                .defineInRange("min", 200, 50, 400);
        restockDexterityMultiplier = builder.comment("Ticks reduced per Dexterity point.")
                .defineInRange("dexterityMultiplier", 8.0, 0.5, 30.0);
        builder.pop();

        builder.pop(); // skillScaling

        // XP Awards
        builder.comment("XP Award Configuration", "Formula: base + min(count / divisor, cap)").push("xpAwards");

        builder.comment("XP for Stack, Food, and Burnable requests").push("stack");
        stackXpBase = builder.comment("Base XP awarded").defineInRange("base", 1.0, 0.0, 10.0);
        stackXpDivisor = builder.comment("Divisor for scaling XP by item count").defineInRange("divisor", 16.0, 1.0,
                64.0);
        stackXpCap = builder.comment("Maximum bonus XP from item count").defineInRange("cap", 4.0, 0.0, 20.0);
        builder.pop();

        builder.comment("XP for Tag requests").push("tag");
        tagXpBase = builder.comment("Base XP awarded").defineInRange("base", 1.5, 0.0, 10.0);
        tagXpDivisor = builder.comment("Divisor for scaling XP by item count").defineInRange("divisor", 16.0, 1.0,
                64.0);
        tagXpCap = builder.comment("Maximum bonus XP from item count").defineInRange("cap", 4.0, 0.0, 20.0);
        builder.pop();

        builder.comment("XP for Tool requests").push("tool");
        toolXpFixed = builder.comment("Fixed XP awarded per tool delivery").defineInRange("fixed", 2.0, 0.0, 10.0);
        builder.pop();

        builder.comment("XP for StackList requests",
                "Formula: base + min(itemCount / itemDivisor, itemCap) + min(typeCount / typeDivisor, typeCap)")
                .push("stackList");
        stackListXpBase = builder.comment("Base XP awarded").defineInRange("base", 2.0, 0.0, 10.0);
        stackListXpItemDivisor = builder.comment("Divisor for scaling XP by total item count")
                .defineInRange("itemDivisor", 16.0, 1.0, 64.0);
        stackListXpItemCap = builder.comment("Maximum bonus XP from item count").defineInRange("itemCap", 2.0, 0.0,
                10.0);
        stackListXpTypeDivisor = builder.comment("Divisor for scaling XP by number of item types")
                .defineInRange("typeDivisor", 4.0, 1.0, 16.0);
        stackListXpTypeCap = builder.comment("Maximum bonus XP from item types").defineInRange("typeCap", 2.0, 0.0,
                10.0);
        builder.pop();

        builder.pop(); // xpAwards

        // Display Settings
        builder.comment("Display Board Settings").push("display");

        itemNameTruncation = builder.comment("Maximum length for item names on display boards before truncation.")
                .defineInRange("itemNameTruncation", 12, 6, 32);

        etaFormatThresholdSeconds = builder
                .comment("Threshold in seconds for ETA format.", "Below this value shows seconds, above shows minutes.")
                .defineInRange("etaFormatThresholdSeconds", 60, 10, 300);

        builder.pop(); // display

        builder.pop(); // server
    }
}
