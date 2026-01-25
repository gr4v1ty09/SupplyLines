package com.gr4v1ty.supplylines.colony.buildings.modules;

import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * Settings module for the Stock Keeper building. Provides per-building
 * overrides for select server configuration options, particularly timing
 * settings that depend on supplier latency.
 */
public class DeliverySettingsModule extends AbstractBuildingModule implements IPersistentModule {

    // === Setting Keys (also used as NBT tags) ===

    /** Boolean: Enable speculative ordering for this building. */
    public static final String SETTING_SPECULATIVE_ORDERING = "enableSpeculativeOrdering";

    /** Boolean: Enable idle patrol/wander behavior. */
    public static final String SETTING_IDLE_WANDER = "enableIdleWander";

    /** Boolean: Randomize patrol order vs sequential. */
    public static final String SETTING_RANDOM_PATROL = "randomPatrol";

    /** Integer: Timeout after ETA before expiring orders (ticks). */
    public static final String SETTING_ORDER_EXPIRY_BUFFER = "orderExpiryBufferTicks";

    /** Integer: Delay before triggering speculative order (ticks). */
    public static final String SETTING_SPECULATIVE_DELAY = "speculativeDelayTicks";

    /** Integer: Assumed delivery time for ETA calculations (ticks). */
    public static final String SETTING_DEFAULT_DELIVERY = "defaultDeliveryTicks";

    /** Integer: Timeout for staging requests before cancellation (ticks). */
    public static final String SETTING_STAGING_TIMEOUT = "stagingTimeoutTicks";

    // === AI/Movement Setting Keys ===

    /** Double (scaled x100): Walk speed multiplier (0.5-2.0). */
    public static final String SETTING_WALK_SPEED = "walkSpeed";

    /** Double (scaled x100): Arrival detection distance squared (1.0-16.0). */
    public static final String SETTING_ARRIVE_DISTANCE_SQ = "arriveDistanceSq";

    /** Integer: Pause duration at patrol points in state machine ticks (1-20). */
    public static final String SETTING_INSPECT_DURATION = "inspectDurationTicks";

    /** Integer: Percentage chance to trigger idle wander (0-100). */
    public static final String SETTING_IDLE_WANDER_CHANCE = "idleWanderChance";

    /** Integer: Minimum seconds between idle wanders (1-60). */
    public static final String SETTING_IDLE_WANDER_COOLDOWN = "idleWanderCooldown";

    /** Integer: Seconds spent at each idle wander location (1-10). */
    public static final String SETTING_IDLE_INSPECT_DURATION = "idleInspectDuration";

    // === Default sentinel values (indicates "use global config") ===
    private static final int USE_GLOBAL_BOOLEAN = -1;
    private static final int USE_GLOBAL_INT = -1;

    // === Setting storage ===
    // -1 means "use global config", 0 = false, 1 = true for booleans
    private int enableSpeculativeOrdering = USE_GLOBAL_BOOLEAN;
    private int enableIdleWander = USE_GLOBAL_BOOLEAN;
    private int randomPatrol = USE_GLOBAL_BOOLEAN;

    // -1 means "use global config" for integers
    private int orderExpiryBufferTicks = USE_GLOBAL_INT;
    private int speculativeDelayTicks = USE_GLOBAL_INT;
    private int defaultDeliveryTicks = USE_GLOBAL_INT;
    private int stagingTimeoutTicks = USE_GLOBAL_INT;

    // AI/Movement settings
    // Doubles stored as scaled ints (x100) to use the same -1 sentinel pattern
    private int walkSpeed = USE_GLOBAL_INT; // 100 = 1.0x speed
    private int arriveDistanceSq = USE_GLOBAL_INT; // 400 = 4.0 blocks²
    private int inspectDurationTicks = USE_GLOBAL_INT;
    private int idleWanderChance = USE_GLOBAL_INT;
    private int idleWanderCooldown = USE_GLOBAL_INT;
    private int idleInspectDuration = USE_GLOBAL_INT;

    @Override
    public void deserializeNBT(@NotNull CompoundTag compound) {
        if (compound.contains(SETTING_SPECULATIVE_ORDERING)) {
            enableSpeculativeOrdering = compound.getInt(SETTING_SPECULATIVE_ORDERING);
        }
        if (compound.contains(SETTING_IDLE_WANDER)) {
            enableIdleWander = compound.getInt(SETTING_IDLE_WANDER);
        }
        if (compound.contains(SETTING_RANDOM_PATROL)) {
            randomPatrol = compound.getInt(SETTING_RANDOM_PATROL);
        }
        if (compound.contains(SETTING_ORDER_EXPIRY_BUFFER)) {
            orderExpiryBufferTicks = compound.getInt(SETTING_ORDER_EXPIRY_BUFFER);
        }
        if (compound.contains(SETTING_SPECULATIVE_DELAY)) {
            speculativeDelayTicks = compound.getInt(SETTING_SPECULATIVE_DELAY);
        }
        if (compound.contains(SETTING_DEFAULT_DELIVERY)) {
            defaultDeliveryTicks = compound.getInt(SETTING_DEFAULT_DELIVERY);
        }
        if (compound.contains(SETTING_STAGING_TIMEOUT)) {
            stagingTimeoutTicks = compound.getInt(SETTING_STAGING_TIMEOUT);
        }
        // AI/Movement settings
        if (compound.contains(SETTING_WALK_SPEED)) {
            walkSpeed = compound.getInt(SETTING_WALK_SPEED);
        }
        if (compound.contains(SETTING_ARRIVE_DISTANCE_SQ)) {
            arriveDistanceSq = compound.getInt(SETTING_ARRIVE_DISTANCE_SQ);
        }
        if (compound.contains(SETTING_INSPECT_DURATION)) {
            inspectDurationTicks = compound.getInt(SETTING_INSPECT_DURATION);
        }
        if (compound.contains(SETTING_IDLE_WANDER_CHANCE)) {
            idleWanderChance = compound.getInt(SETTING_IDLE_WANDER_CHANCE);
        }
        if (compound.contains(SETTING_IDLE_WANDER_COOLDOWN)) {
            idleWanderCooldown = compound.getInt(SETTING_IDLE_WANDER_COOLDOWN);
        }
        if (compound.contains(SETTING_IDLE_INSPECT_DURATION)) {
            idleInspectDuration = compound.getInt(SETTING_IDLE_INSPECT_DURATION);
        }
    }

    @Override
    public void serializeNBT(@NotNull CompoundTag compound) {
        compound.putInt(SETTING_SPECULATIVE_ORDERING, enableSpeculativeOrdering);
        compound.putInt(SETTING_IDLE_WANDER, enableIdleWander);
        compound.putInt(SETTING_RANDOM_PATROL, randomPatrol);
        compound.putInt(SETTING_ORDER_EXPIRY_BUFFER, orderExpiryBufferTicks);
        compound.putInt(SETTING_SPECULATIVE_DELAY, speculativeDelayTicks);
        compound.putInt(SETTING_DEFAULT_DELIVERY, defaultDeliveryTicks);
        compound.putInt(SETTING_STAGING_TIMEOUT, stagingTimeoutTicks);
        // AI/Movement settings
        compound.putInt(SETTING_WALK_SPEED, walkSpeed);
        compound.putInt(SETTING_ARRIVE_DISTANCE_SQ, arriveDistanceSq);
        compound.putInt(SETTING_INSPECT_DURATION, inspectDurationTicks);
        compound.putInt(SETTING_IDLE_WANDER_CHANCE, idleWanderChance);
        compound.putInt(SETTING_IDLE_WANDER_COOLDOWN, idleWanderCooldown);
        compound.putInt(SETTING_IDLE_INSPECT_DURATION, idleInspectDuration);
    }

    @Override
    public void serializeToView(@NotNull FriendlyByteBuf buf, boolean fullSync) {
        buf.writeInt(enableSpeculativeOrdering);
        buf.writeInt(enableIdleWander);
        buf.writeInt(randomPatrol);
        buf.writeInt(orderExpiryBufferTicks);
        buf.writeInt(speculativeDelayTicks);
        buf.writeInt(defaultDeliveryTicks);
        buf.writeInt(stagingTimeoutTicks);
        // AI/Movement settings
        buf.writeInt(walkSpeed);
        buf.writeInt(arriveDistanceSq);
        buf.writeInt(inspectDurationTicks);
        buf.writeInt(idleWanderChance);
        buf.writeInt(idleWanderCooldown);
        buf.writeInt(idleInspectDuration);
    }

    // === Getters with global fallback ===

    /**
     * Gets whether speculative ordering is enabled for this building. Falls back to
     * global config if not explicitly set.
     *
     * @return true if speculative ordering is enabled.
     */
    public boolean isSpeculativeOrderingEnabled() {
        if (enableSpeculativeOrdering == USE_GLOBAL_BOOLEAN) {
            return ModConfig.SERVER.enableSpeculativeOrdering.get();
        }
        return enableSpeculativeOrdering == 1;
    }

    /**
     * Gets whether idle wander/patrol is enabled for this building. Falls back to
     * global config if not explicitly set.
     *
     * @return true if idle wander is enabled.
     */
    public boolean isIdleWanderEnabled() {
        if (enableIdleWander == USE_GLOBAL_BOOLEAN) {
            return ModConfig.SERVER.enableIdleWander.get();
        }
        return enableIdleWander == 1;
    }

    /**
     * Gets whether random patrol order is enabled for this building. Falls back to
     * global config if not explicitly set.
     *
     * @return true if random patrol is enabled.
     */
    public boolean isRandomPatrol() {
        if (randomPatrol == USE_GLOBAL_BOOLEAN) {
            return ModConfig.SERVER.randomPatrol.get();
        }
        return randomPatrol == 1;
    }

    /**
     * Gets the order expiry buffer ticks for this building. Falls back to global
     * config if not explicitly set.
     *
     * @return order expiry buffer in ticks.
     */
    public int getOrderExpiryBufferTicks() {
        if (orderExpiryBufferTicks == USE_GLOBAL_INT) {
            return ModConfig.SERVER.orderExpiryBufferTicks.get();
        }
        return orderExpiryBufferTicks;
    }

    /**
     * Gets the speculative delay ticks for this building. Falls back to global
     * config if not explicitly set.
     *
     * @return speculative delay in ticks.
     */
    public int getSpeculativeDelayTicks() {
        if (speculativeDelayTicks == USE_GLOBAL_INT) {
            return ModConfig.SERVER.speculativeDelayTicks.get();
        }
        return speculativeDelayTicks;
    }

    /**
     * Gets the default delivery ticks for this building. Falls back to global
     * config if not explicitly set.
     *
     * @return default delivery time in ticks.
     */
    public int getDefaultDeliveryTicks() {
        if (defaultDeliveryTicks == USE_GLOBAL_INT) {
            return ModConfig.SERVER.defaultDeliveryTicks.get();
        }
        return defaultDeliveryTicks;
    }

    /**
     * Gets the staging timeout ticks for this building. Falls back to global config
     * if not explicitly set.
     *
     * @return staging timeout in ticks.
     */
    public int getStagingTimeoutTicks() {
        if (stagingTimeoutTicks == USE_GLOBAL_INT) {
            return ModConfig.SERVER.stagingTimeoutTicks.get();
        }
        return stagingTimeoutTicks;
    }

    // === AI/Movement Getters with global fallback ===

    /**
     * Gets the walk speed multiplier for this building. Falls back to global config
     * if not explicitly set.
     *
     * @return walk speed multiplier (0.5-2.0).
     */
    public double getWalkSpeed() {
        if (walkSpeed == USE_GLOBAL_INT) {
            return ModConfig.SERVER.walkSpeed.get();
        }
        return walkSpeed / 100.0;
    }

    /**
     * Gets the arrival distance squared for this building. Falls back to global
     * config if not explicitly set.
     *
     * @return arrival distance squared (1.0-16.0).
     */
    public double getArriveDistanceSq() {
        if (arriveDistanceSq == USE_GLOBAL_INT) {
            return ModConfig.SERVER.arriveDistanceSq.get();
        }
        return arriveDistanceSq / 100.0;
    }

    /**
     * Gets the inspect duration in state machine ticks for this building. Falls
     * back to global config if not explicitly set.
     *
     * @return inspect duration in state machine ticks.
     */
    public int getInspectDurationTicks() {
        if (inspectDurationTicks == USE_GLOBAL_INT) {
            return ModConfig.SERVER.inspectDurationTicks.get();
        }
        return inspectDurationTicks;
    }

    /**
     * Gets the idle wander chance for this building. Falls back to global config if
     * not explicitly set.
     *
     * @return idle wander chance (0-100).
     */
    public int getIdleWanderChance() {
        if (idleWanderChance == USE_GLOBAL_INT) {
            return ModConfig.SERVER.idleWanderChance.get();
        }
        return idleWanderChance;
    }

    /**
     * Gets the idle wander cooldown in seconds for this building. Falls back to
     * global config if not explicitly set.
     *
     * @return idle wander cooldown in seconds.
     */
    public int getIdleWanderCooldown() {
        if (idleWanderCooldown == USE_GLOBAL_INT) {
            return ModConfig.SERVER.idleWanderCooldown.get();
        }
        return idleWanderCooldown;
    }

    /**
     * Gets the idle inspect duration in seconds for this building. Falls back to
     * global config if not explicitly set.
     *
     * @return idle inspect duration in seconds.
     */
    public int getIdleInspectDuration() {
        if (idleInspectDuration == USE_GLOBAL_INT) {
            return ModConfig.SERVER.idleInspectDuration.get();
        }
        return idleInspectDuration;
    }

    // === Raw value getters for view display ===

    /**
     * Gets the raw value for speculative ordering setting. -1 = use global, 0 =
     * false, 1 = true.
     *
     * @return the raw setting value.
     */
    public int getRawSpeculativeOrdering() {
        return enableSpeculativeOrdering;
    }

    /**
     * Gets the raw value for idle wander setting. -1 = use global, 0 = false, 1 =
     * true.
     *
     * @return the raw setting value.
     */
    public int getRawIdleWander() {
        return enableIdleWander;
    }

    /**
     * Gets the raw value for random patrol setting. -1 = use global, 0 = false, 1 =
     * true.
     *
     * @return the raw setting value.
     */
    public int getRawRandomPatrol() {
        return randomPatrol;
    }

    /**
     * Gets the raw value for order expiry buffer setting. -1 = use global,
     * otherwise the value in ticks.
     *
     * @return the raw setting value.
     */
    public int getRawOrderExpiryBuffer() {
        return orderExpiryBufferTicks;
    }

    /**
     * Gets the raw value for speculative delay setting. -1 = use global, otherwise
     * the value in ticks.
     *
     * @return the raw setting value.
     */
    public int getRawSpeculativeDelay() {
        return speculativeDelayTicks;
    }

    /**
     * Gets the raw value for default delivery setting. -1 = use global, otherwise
     * the value in ticks.
     *
     * @return the raw setting value.
     */
    public int getRawDefaultDelivery() {
        return defaultDeliveryTicks;
    }

    /**
     * Gets the raw value for staging timeout setting. -1 = use global, otherwise
     * the value in ticks.
     *
     * @return the raw setting value.
     */
    public int getRawStagingTimeout() {
        return stagingTimeoutTicks;
    }

    // === AI/Movement Raw Getters ===

    /**
     * Gets the raw value for walk speed setting. -1 = use global, otherwise value
     * scaled by 100.
     *
     * @return the raw setting value.
     */
    public int getRawWalkSpeed() {
        return walkSpeed;
    }

    /**
     * Gets the raw value for arrive distance squared setting. -1 = use global,
     * otherwise value scaled by 100.
     *
     * @return the raw setting value.
     */
    public int getRawArriveDistanceSq() {
        return arriveDistanceSq;
    }

    /**
     * Gets the raw value for inspect duration setting. -1 = use global, otherwise
     * the value in state machine ticks.
     *
     * @return the raw setting value.
     */
    public int getRawInspectDuration() {
        return inspectDurationTicks;
    }

    /**
     * Gets the raw value for idle wander chance setting. -1 = use global, otherwise
     * value 0-100.
     *
     * @return the raw setting value.
     */
    public int getRawIdleWanderChance() {
        return idleWanderChance;
    }

    /**
     * Gets the raw value for idle wander cooldown setting. -1 = use global,
     * otherwise value in seconds.
     *
     * @return the raw setting value.
     */
    public int getRawIdleWanderCooldown() {
        return idleWanderCooldown;
    }

    /**
     * Gets the raw value for idle inspect duration setting. -1 = use global,
     * otherwise value in seconds.
     *
     * @return the raw setting value.
     */
    public int getRawIdleInspectDuration() {
        return idleInspectDuration;
    }

    // === Setters ===

    /**
     * Sets the speculative ordering setting.
     *
     * @param value
     *            -1 for global, 0 for false, 1 for true.
     */
    public void setSpeculativeOrdering(int value) {
        this.enableSpeculativeOrdering = value;
        markDirty();
    }

    /**
     * Sets the idle wander setting.
     *
     * @param value
     *            -1 for global, 0 for false, 1 for true.
     */
    public void setIdleWander(int value) {
        this.enableIdleWander = value;
        markDirty();
    }

    /**
     * Sets the random patrol setting.
     *
     * @param value
     *            -1 for global, 0 for false, 1 for true.
     */
    public void setRandomPatrol(int value) {
        this.randomPatrol = value;
        markDirty();
    }

    /**
     * Sets the order expiry buffer setting.
     *
     * @param value
     *            -1 for global, otherwise value in ticks.
     */
    public void setOrderExpiryBuffer(int value) {
        this.orderExpiryBufferTicks = value;
        markDirty();
    }

    /**
     * Sets the speculative delay setting.
     *
     * @param value
     *            -1 for global, otherwise value in ticks.
     */
    public void setSpeculativeDelay(int value) {
        this.speculativeDelayTicks = value;
        markDirty();
    }

    /**
     * Sets the default delivery setting.
     *
     * @param value
     *            -1 for global, otherwise value in ticks.
     */
    public void setDefaultDelivery(int value) {
        this.defaultDeliveryTicks = value;
        markDirty();
    }

    /**
     * Sets the staging timeout setting.
     *
     * @param value
     *            -1 for global, otherwise value in ticks.
     */
    public void setStagingTimeout(int value) {
        this.stagingTimeoutTicks = value;
        markDirty();
    }

    // === AI/Movement Setters ===

    /**
     * Sets the walk speed setting.
     *
     * @param value
     *            -1 for global, otherwise value scaled by 100.
     */
    public void setWalkSpeed(int value) {
        this.walkSpeed = value;
        markDirty();
    }

    /**
     * Sets the arrive distance squared setting.
     *
     * @param value
     *            -1 for global, otherwise value scaled by 100.
     */
    public void setArriveDistanceSq(int value) {
        this.arriveDistanceSq = value;
        markDirty();
    }

    /**
     * Sets the inspect duration setting.
     *
     * @param value
     *            -1 for global, otherwise value in state machine ticks.
     */
    public void setInspectDuration(int value) {
        this.inspectDurationTicks = value;
        markDirty();
    }

    /**
     * Sets the idle wander chance setting.
     *
     * @param value
     *            -1 for global, otherwise value 0-100.
     */
    public void setIdleWanderChance(int value) {
        this.idleWanderChance = value;
        markDirty();
    }

    /**
     * Sets the idle wander cooldown setting.
     *
     * @param value
     *            -1 for global, otherwise value in seconds.
     */
    public void setIdleWanderCooldown(int value) {
        this.idleWanderCooldown = value;
        markDirty();
    }

    /**
     * Sets the idle inspect duration setting.
     *
     * @param value
     *            -1 for global, otherwise value in seconds.
     */
    public void setIdleInspectDuration(int value) {
        this.idleInspectDuration = value;
        markDirty();
    }

    /**
     * Sets a setting by key name.
     *
     * @param settingKey
     *            the setting key.
     * @param value
     *            the new value.
     */
    public void setSetting(String settingKey, int value) {
        switch (settingKey) {
            case SETTING_SPECULATIVE_ORDERING -> setSpeculativeOrdering(value);
            case SETTING_IDLE_WANDER -> setIdleWander(value);
            case SETTING_RANDOM_PATROL -> setRandomPatrol(value);
            case SETTING_ORDER_EXPIRY_BUFFER -> setOrderExpiryBuffer(value);
            case SETTING_SPECULATIVE_DELAY -> setSpeculativeDelay(value);
            case SETTING_DEFAULT_DELIVERY -> setDefaultDelivery(value);
            case SETTING_STAGING_TIMEOUT -> setStagingTimeout(value);
            // AI/Movement settings
            case SETTING_WALK_SPEED -> setWalkSpeed(value);
            case SETTING_ARRIVE_DISTANCE_SQ -> setArriveDistanceSq(value);
            case SETTING_INSPECT_DURATION -> setInspectDuration(value);
            case SETTING_IDLE_WANDER_CHANCE -> setIdleWanderChance(value);
            case SETTING_IDLE_WANDER_COOLDOWN -> setIdleWanderCooldown(value);
            case SETTING_IDLE_INSPECT_DURATION -> setIdleInspectDuration(value);
            default -> {
                /* ignore unknown settings */ }
        }
    }
}
