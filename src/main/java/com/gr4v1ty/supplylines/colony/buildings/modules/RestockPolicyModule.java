package com.gr4v1ty.supplylines.colony.buildings.modules;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.util.ResearchEffects;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-side module for managing restock policies. Defines which items should
 * be kept in the local stock network and their target quantities. Unlocked at
 * building level 5.
 */
public class RestockPolicyModule extends AbstractBuildingModule implements IPersistentModule {
    private static final String TAG_POLICIES = "policies";
    private static final String TAG_ITEM = "item";
    private static final String TAG_TARGET = "target";

    /** Base maximum number of policy entries allowed. */
    private static final int BASE_MAX_POLICIES = 20;

    /**
     * Calculate the maximum number of policies based on research effects.
     *
     * @return the current max policy limit.
     */
    public int getMaxPolicies() {
        final double multiplier = 1 + building.getColony().getResearchManager().getResearchEffects()
                .getEffectStrength(ResearchEffects.RESTOCK_POLICY_LIMIT);
        return (int) (BASE_MAX_POLICIES * multiplier);
    }

    /**
     * List of restock policy entries.
     */
    private final List<PolicyEntry> policies = new ArrayList<>();

    /**
     * Represents a restock policy entry.
     */
    public static class PolicyEntry {
        private final ItemStorage item;
        private int targetQuantity;

        public PolicyEntry(ItemStorage item, int targetQuantity) {
            this.item = item;
            this.targetQuantity = targetQuantity;
        }

        public ItemStorage getItem() {
            return item;
        }

        public int getTargetQuantity() {
            return targetQuantity;
        }

        public void setTargetQuantity(int targetQuantity) {
            this.targetQuantity = targetQuantity;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put(TAG_ITEM, item.getItemStack().save(new CompoundTag()));
            tag.putInt(TAG_TARGET, targetQuantity);
            return tag;
        }

        public static PolicyEntry fromNBT(CompoundTag tag) {
            ItemStack stack = ItemStack.of(tag.getCompound(TAG_ITEM));
            ItemStorage storage = new ItemStorage(stack);
            int target = tag.getInt(TAG_TARGET);
            return new PolicyEntry(storage, target);
        }

        public void toBuf(FriendlyByteBuf buf) {
            buf.writeItem(item.getItemStack());
            buf.writeInt(targetQuantity);
        }

        public static PolicyEntry fromBuf(FriendlyByteBuf buf) {
            ItemStack stack = buf.readItem();
            ItemStorage storage = new ItemStorage(stack);
            int target = buf.readInt();
            return new PolicyEntry(storage, target);
        }
    }

    /**
     * Add or update a policy entry.
     *
     * @param item
     *            the item to track.
     * @param targetQuantity
     *            the target quantity in stacks.
     * @return true if added/updated, false if at max capacity.
     */
    public boolean addOrUpdatePolicy(ItemStorage item, int targetQuantity) {
        // Check if already exists
        for (PolicyEntry entry : policies) {
            if (entry.getItem().equals(item)) {
                entry.setTargetQuantity(targetQuantity);
                markDirty();
                return true;
            }
        }

        // Check capacity
        if (policies.size() >= getMaxPolicies()) {
            return false;
        }

        policies.add(new PolicyEntry(item, targetQuantity));
        markDirty();
        return true;
    }

    /**
     * Remove a policy entry.
     *
     * @param item
     *            the item to remove.
     * @return true if removed.
     */
    public boolean removePolicy(ItemStorage item) {
        boolean removed = policies.removeIf(entry -> entry.getItem().equals(item));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    /**
     * Remove a policy by item stack.
     *
     * @param stack
     *            the item stack to match.
     * @return true if removed.
     */
    public boolean removePolicy(ItemStack stack) {
        return removePolicy(new ItemStorage(stack));
    }

    /**
     * Get an unmodifiable list of policies.
     *
     * @return the policy list.
     */
    public List<PolicyEntry> getPolicies() {
        return Collections.unmodifiableList(policies);
    }

    /**
     * Check if we've reached the maximum number of policies.
     *
     * @return true if at max capacity.
     */
    public boolean hasReachedLimit() {
        return policies.size() >= getMaxPolicies();
    }

    @Override
    public void deserializeNBT(@NotNull CompoundTag compound) {
        policies.clear();
        if (compound.contains(TAG_POLICIES)) {
            ListTag list = compound.getList(TAG_POLICIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                policies.add(PolicyEntry.fromNBT(list.getCompound(i)));
            }
        }
    }

    @Override
    public void serializeNBT(@NotNull CompoundTag compound) {
        ListTag list = new ListTag();
        for (PolicyEntry entry : policies) {
            list.add(entry.toNBT());
        }
        compound.put(TAG_POLICIES, list);
    }

    @Override
    public void serializeToView(@NotNull FriendlyByteBuf buf) {
        // Get suppliers module for remote stock calculation
        SuppliersModule suppliersModule = building.getModule(SuppliersModule.class);

        buf.writeInt(policies.size());
        for (PolicyEntry entry : policies) {
            entry.toBuf(buf);

            // Local stock = Stock Keeper hut's vault inventory
            long localStock = 0;
            if (building instanceof BuildingStockKeeper stockKeeper) {
                localStock = stockKeeper.getStockLevel(entry.getItem().getItemStack());
            }
            buf.writeLong(localStock);

            // Remote stock = sum across all linked supplier networks
            long remoteStock = 0;
            if (suppliersModule != null) {
                for (SuppliersModule.SupplierEntry supplier : suppliersModule.getSuppliers()) {
                    try {
                        InventorySummary summary = LogisticsManager.getSummaryOfNetwork(supplier.getNetworkId(), false);
                        if (summary != null && !summary.isEmpty()) {
                            for (BigItemStack bigStack : summary.getStacks()) {
                                if (ItemStack.isSameItemSameTags(bigStack.stack, entry.getItem().getItemStack())) {
                                    remoteStock += bigStack.count;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Network may be unloaded or invalid
                    }
                }
            }
            buf.writeLong(remoteStock);
        }
        buf.writeBoolean(hasReachedLimit());
    }
}
