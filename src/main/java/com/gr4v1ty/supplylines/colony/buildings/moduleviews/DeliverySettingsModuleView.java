package com.gr4v1ty.supplylines.colony.buildings.moduleviews;

import com.gr4v1ty.supplylines.config.ModConfig;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side view for the Stock Keeper settings module. Displays per-building
 * settings with global config fallback.
 */
public class DeliverySettingsModuleView extends AbstractBuildingModuleView {

    // Raw setting values (-1 = use global)
    private int enableSpeculativeOrdering;
    private int enableIdleWander;
    private int randomPatrol;
    private int orderExpiryBufferTicks;
    private int speculativeDelayTicks;
    private int defaultDeliveryTicks;
    private int stagingTimeoutTicks;

    // AI/Movement settings (doubles stored as scaled ints x100)
    private int walkSpeed;
    private int arriveDistanceSq;
    private int inspectDurationTicks;
    private int idleWanderChance;
    private int idleWanderCooldown;
    private int idleInspectDuration;

    @Override
    public void deserialize(@NotNull FriendlyByteBuf buf) {
        enableSpeculativeOrdering = buf.readInt();
        enableIdleWander = buf.readInt();
        randomPatrol = buf.readInt();
        orderExpiryBufferTicks = buf.readInt();
        speculativeDelayTicks = buf.readInt();
        defaultDeliveryTicks = buf.readInt();
        stagingTimeoutTicks = buf.readInt();
        // AI/Movement settings
        walkSpeed = buf.readInt();
        arriveDistanceSq = buf.readInt();
        inspectDurationTicks = buf.readInt();
        idleWanderChance = buf.readInt();
        idleWanderCooldown = buf.readInt();
        idleInspectDuration = buf.readInt();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow() {
        return createWindow();
    }

    @OnlyIn(Dist.CLIENT)
    private BOWindow createWindow() {
        return new com.gr4v1ty.supplylines.colony.buildings.modulewindows.DeliverySettingsModuleWindow(this);
    }

    @Override
    @SuppressWarnings("removal")
    public ResourceLocation getIconResourceLocation() {
        return new ResourceLocation("minecolonies", "textures/gui/modules/settings.png");
    }

    @Override
    @Nullable
    public Component getDesc() {
        return Component.translatable("com.supplylines.gui.stockkeeper.settings");
    }

    // === Raw value getters for display ===

    public int getRawSpeculativeOrdering() {
        return enableSpeculativeOrdering;
    }

    public int getRawIdleWander() {
        return enableIdleWander;
    }

    public int getRawRandomPatrol() {
        return randomPatrol;
    }

    public int getRawOrderExpiryBuffer() {
        return orderExpiryBufferTicks;
    }

    public int getRawSpeculativeDelay() {
        return speculativeDelayTicks;
    }

    public int getRawDefaultDelivery() {
        return defaultDeliveryTicks;
    }

    public int getRawStagingTimeout() {
        return stagingTimeoutTicks;
    }

    // === AI/Movement Raw Getters ===

    public int getRawWalkSpeed() {
        return walkSpeed;
    }

    public int getRawArriveDistanceSq() {
        return arriveDistanceSq;
    }

    public int getRawInspectDuration() {
        return inspectDurationTicks;
    }

    public int getRawIdleWanderChance() {
        return idleWanderChance;
    }

    public int getRawIdleWanderCooldown() {
        return idleWanderCooldown;
    }

    public int getRawIdleInspectDuration() {
        return idleInspectDuration;
    }

    // === Effective value getters (with global fallback) ===

    /**
     * Gets the effective value for a boolean setting, considering global fallback.
     *
     * @param raw
     *            the raw value (-1 = global, 0 = false, 1 = true).
     * @param globalDefault
     *            the global default from config.
     * @return the effective boolean value.
     */
    private boolean getEffectiveBoolean(int raw, boolean globalDefault) {
        if (raw == -1) {
            return globalDefault;
        }
        return raw == 1;
    }

    /**
     * Gets the effective value for an integer setting, considering global fallback.
     *
     * @param raw
     *            the raw value (-1 = global).
     * @param globalDefault
     *            the global default from config.
     * @return the effective integer value.
     */
    private int getEffectiveInt(int raw, int globalDefault) {
        if (raw == -1) {
            return globalDefault;
        }
        return raw;
    }

    public boolean isSpeculativeOrderingEnabled() {
        return getEffectiveBoolean(enableSpeculativeOrdering, ModConfig.SERVER.enableSpeculativeOrdering.get());
    }

    public boolean isIdleWanderEnabled() {
        return getEffectiveBoolean(enableIdleWander, ModConfig.SERVER.enableIdleWander.get());
    }

    public boolean isRandomPatrol() {
        return getEffectiveBoolean(randomPatrol, ModConfig.SERVER.randomPatrol.get());
    }

    public int getOrderExpiryBufferTicks() {
        return getEffectiveInt(orderExpiryBufferTicks, ModConfig.SERVER.orderExpiryBufferTicks.get());
    }

    public int getSpeculativeDelayTicks() {
        return getEffectiveInt(speculativeDelayTicks, ModConfig.SERVER.speculativeDelayTicks.get());
    }

    public int getDefaultDeliveryTicks() {
        return getEffectiveInt(defaultDeliveryTicks, ModConfig.SERVER.defaultDeliveryTicks.get());
    }

    public int getStagingTimeoutTicks() {
        return getEffectiveInt(stagingTimeoutTicks, ModConfig.SERVER.stagingTimeoutTicks.get());
    }

    // === Global config getters for display ===

    public boolean getGlobalSpeculativeOrdering() {
        return ModConfig.SERVER.enableSpeculativeOrdering.get();
    }

    public boolean getGlobalIdleWander() {
        return ModConfig.SERVER.enableIdleWander.get();
    }

    public boolean getGlobalRandomPatrol() {
        return ModConfig.SERVER.randomPatrol.get();
    }

    public int getGlobalOrderExpiryBuffer() {
        return ModConfig.SERVER.orderExpiryBufferTicks.get();
    }

    public int getGlobalSpeculativeDelay() {
        return ModConfig.SERVER.speculativeDelayTicks.get();
    }

    public int getGlobalDefaultDelivery() {
        return ModConfig.SERVER.defaultDeliveryTicks.get();
    }

    public int getGlobalStagingTimeout() {
        return ModConfig.SERVER.stagingTimeoutTicks.get();
    }

    // === AI/Movement Global Getters ===

    public double getGlobalWalkSpeed() {
        return ModConfig.SERVER.walkSpeed.get();
    }

    public double getGlobalArriveDistanceSq() {
        return ModConfig.SERVER.arriveDistanceSq.get();
    }

    public int getGlobalInspectDuration() {
        return ModConfig.SERVER.inspectDurationTicks.get();
    }

    public int getGlobalIdleWanderChance() {
        return ModConfig.SERVER.idleWanderChance.get();
    }

    public int getGlobalIdleWanderCooldown() {
        return ModConfig.SERVER.idleWanderCooldown.get();
    }

    public int getGlobalIdleInspectDuration() {
        return ModConfig.SERVER.idleInspectDuration.get();
    }

    // === Setters for client-side optimistic updates ===

    /**
     * Sets a raw value by key for immediate UI feedback. The server will sync the
     * actual value later.
     *
     * @param key
     *            the setting key.
     * @param value
     *            the new raw value.
     */
    public void setRawValue(String key, int value) {
        switch (key) {
            case "enableSpeculativeOrdering" -> enableSpeculativeOrdering = value;
            case "enableIdleWander" -> enableIdleWander = value;
            case "randomPatrol" -> randomPatrol = value;
            case "orderExpiryBufferTicks" -> orderExpiryBufferTicks = value;
            case "speculativeDelayTicks" -> speculativeDelayTicks = value;
            case "defaultDeliveryTicks" -> defaultDeliveryTicks = value;
            case "stagingTimeoutTicks" -> stagingTimeoutTicks = value;
            // AI/Movement settings
            case "walkSpeed" -> walkSpeed = value;
            case "arriveDistanceSq" -> arriveDistanceSq = value;
            case "inspectDurationTicks" -> inspectDurationTicks = value;
            case "idleWanderChance" -> idleWanderChance = value;
            case "idleWanderCooldown" -> idleWanderCooldown = value;
            case "idleInspectDuration" -> idleInspectDuration = value;
            default -> {
                /* ignore unknown settings */ }
        }
    }
}
