package com.gr4v1ty.supplylines.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.core.colony.managers.StatisticsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * Statistics module for the Stock Keeper building. Tracks order counts and
 * per-item delivery statistics.
 *
 * <p>
 * Stat key prefixes:
 * <ul>
 * <li>{@code order:} - Order count statistics (restock, speculative)</li>
 * <li>{@code item:} - Per-item delivery counts (e.g.,
 * {@code item:minecraft:iron_ingot})</li>
 * </ul>
 */
public class DeliveryStatisticsModule extends AbstractBuildingModule implements IPersistentModule {

    /** Prefix for order count statistics. */
    public static final String PREFIX_ORDER = "order:";

    /** Prefix for item delivery statistics. */
    public static final String PREFIX_ITEM = "item:";

    /** Stat ID for restock policy orders placed. */
    public static final String STAT_RESTOCK_ORDERS = PREFIX_ORDER + "restock";

    /** Stat ID for speculative orders placed. */
    public static final String STAT_SPECULATIVE_ORDERS = PREFIX_ORDER + "speculative";

    /** The statistics manager instance. */
    private IStatisticsManager statisticsManager = new StatisticsManager();

    @Override
    public void deserializeNBT(@NotNull CompoundTag compound) {
        statisticsManager.readFromNBT(compound);
    }

    @Override
    public void serializeNBT(@NotNull CompoundTag compound) {
        statisticsManager.writeToNBT(compound);
    }

    @Override
    public void serializeToView(@NotNull FriendlyByteBuf buf, boolean fullSync) {
        statisticsManager.serialize(buf, fullSync);
    }

    /**
     * Get the statistics manager for this building.
     *
     * @return the statistics manager.
     */
    public IStatisticsManager getBuildingStatisticsManager() {
        return statisticsManager;
    }

    /**
     * Increment a statistic by 1.
     *
     * @param statId
     *            the stat ID to increment.
     */
    public void increment(String statId) {
        statisticsManager.increment(statId, building.getColony().getDay());
        // Mark dirty occasionally to sync to clients
        if (MathUtils.RANDOM.nextInt(10) == 0) {
            markDirty();
        }
    }

    /**
     * Increment a statistic by a given count.
     *
     * @param statId
     *            the stat ID to increment.
     * @param count
     *            the count to add.
     */
    public void incrementBy(String statId, int count) {
        statisticsManager.incrementBy(statId, count, building.getColony().getDay());
        // Mark dirty more often for larger increments
        if (MathUtils.RANDOM.nextInt(10) <= count) {
            markDirty();
        }
    }

    /**
     * Track an item delivery. Records the count under a stat key prefixed with
     * "item:" followed by the item's registry name.
     *
     * @param item
     *            the item stack being delivered.
     * @param count
     *            the number of items delivered.
     */
    public void trackItemDelivery(ItemStack item, int count) {
        if (item.isEmpty() || count <= 0) {
            return;
        }
        var registryName = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (registryName == null) {
            return;
        }
        String statKey = PREFIX_ITEM + registryName.toString();
        incrementBy(statKey, count);
    }
}
