package com.gr4v1ty.supplylines.colony.manager.migration.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Holds extracted Factory Panel configuration for migration during building
 * upgrades. This data is cached when a level 4->5 upgrade is initiated and
 * applied after construction completes.
 */
public class PanelMigrationData {

    private static final String TAG_FROM_LEVEL = "FromLevel";
    private static final String TAG_TO_LEVEL = "ToLevel";
    private static final String TAG_PANELS = "Panels";

    private final List<PanelConfig> panels = new ArrayList<>();
    private final int fromLevel;
    private final int toLevel;

    /**
     * Represents a single panel's configuration.
     */
    public static class PanelConfig {
        private static final String TAG_ITEM = "Item";
        private static final String TAG_THRESHOLD = "Threshold";
        private static final String TAG_UP_TO = "UpTo";
        private static final String TAG_NETWORK_ID = "NetworkId";
        private static final String TAG_ADDRESS = "Address";

        private final ItemStack filterItem;
        private final int threshold;
        private final boolean upTo; // true = items, false = stacks
        private final UUID networkId;
        private final String recipeAddress;

        public PanelConfig(ItemStack filterItem, int threshold, boolean upTo, UUID networkId, String recipeAddress) {
            this.filterItem = filterItem.copy();
            this.threshold = threshold;
            this.upTo = upTo;
            this.networkId = networkId;
            this.recipeAddress = recipeAddress != null ? recipeAddress : "";
        }

        public ItemStack getFilterItem() {
            return filterItem;
        }

        public int getThreshold() {
            return threshold;
        }

        public boolean isUpTo() {
            return upTo;
        }

        public UUID getNetworkId() {
            return networkId;
        }

        public String getRecipeAddress() {
            return recipeAddress;
        }

        /**
         * Calculate the actual target quantity for RestockPolicy. If upTo=true,
         * threshold is in items. If upTo=false, threshold is in stacks.
         */
        public int getTargetQuantity() {
            if (upTo) {
                return threshold;
            }
            return threshold * filterItem.getMaxStackSize();
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put(TAG_ITEM, filterItem.save(new CompoundTag()));
            tag.putInt(TAG_THRESHOLD, threshold);
            tag.putBoolean(TAG_UP_TO, upTo);
            tag.putUUID(TAG_NETWORK_ID, networkId);
            tag.putString(TAG_ADDRESS, recipeAddress);
            return tag;
        }

        public static PanelConfig fromNBT(CompoundTag tag) {
            ItemStack item = ItemStack.of(tag.getCompound(TAG_ITEM));
            int threshold = tag.getInt(TAG_THRESHOLD);
            boolean upTo = tag.getBoolean(TAG_UP_TO);
            UUID networkId = tag.getUUID(TAG_NETWORK_ID);
            String address = tag.getString(TAG_ADDRESS);
            return new PanelConfig(item, threshold, upTo, networkId, address);
        }
    }

    public PanelMigrationData(int fromLevel, int toLevel) {
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
    }

    /**
     * Add a panel configuration. Skips empty or invalid panels.
     */
    public void addPanel(PanelConfig config) {
        if (config.filterItem.isEmpty() || config.threshold <= 0) {
            return;
        }
        panels.add(config);
    }

    public List<PanelConfig> getPanels() {
        return Collections.unmodifiableList(panels);
    }

    public int getFromLevel() {
        return fromLevel;
    }

    public int getToLevel() {
        return toLevel;
    }

    public boolean isEmpty() {
        return panels.isEmpty();
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_FROM_LEVEL, fromLevel);
        tag.putInt(TAG_TO_LEVEL, toLevel);
        ListTag list = new ListTag();
        for (PanelConfig panel : panels) {
            list.add(panel.toNBT());
        }
        tag.put(TAG_PANELS, list);
        return tag;
    }

    public static PanelMigrationData fromNBT(CompoundTag tag) {
        int fromLevel = tag.getInt(TAG_FROM_LEVEL);
        int toLevel = tag.getInt(TAG_TO_LEVEL);
        PanelMigrationData data = new PanelMigrationData(fromLevel, toLevel);
        ListTag list = tag.getList(TAG_PANELS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.panels.add(PanelConfig.fromNBT(list.getCompound(i)));
        }
        return data;
    }
}
