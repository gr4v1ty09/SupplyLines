package com.gr4v1ty.supplylines.compat.create;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Helper class for extracting Create mod logistics network information from
 * items. This works with tuned packager links, stock tickers, and other
 * logistically linked items.
 */
public final class CreateNetworkHelper {
    /** NBT key for block entity data on items. */
    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    /** NBT key for network frequency/ID within block entity data. */
    private static final String FREQ_KEY = "Freq";

    private CreateNetworkHelper() {
        // Utility class
    }

    /**
     * Check if an item stack is a tuned Create logistics network item. A tuned item
     * has been configured to connect to a specific network.
     *
     * @param stack
     *            the item stack to check.
     * @return true if the item is tuned to a network.
     */
    public static boolean isTunedNetworkItem(final ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return false;
        }
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BLOCK_ENTITY_TAG)) {
            return false;
        }
        final CompoundTag beTag = tag.getCompound(BLOCK_ENTITY_TAG);
        return beTag.hasUUID(FREQ_KEY);
    }

    /**
     * Extract the network UUID from a tuned Create logistics item.
     *
     * @param stack
     *            the item stack to extract from.
     * @return the network UUID, or null if not a tuned item.
     */
    @Nullable
    public static UUID getNetworkId(final ItemStack stack) {
        if (!isTunedNetworkItem(stack)) {
            return null;
        }
        final CompoundTag beTag = stack.getTag().getCompound(BLOCK_ENTITY_TAG);
        return beTag.getUUID(FREQ_KEY);
    }

    /**
     * Format a network UUID for display purposes. Shows first 8 characters followed
     * by ellipsis.
     *
     * @param networkId
     *            the network UUID.
     * @return formatted string for display.
     */
    public static String formatNetworkId(final UUID networkId) {
        if (networkId == null) {
            return "Unknown";
        }
        final String str = networkId.toString().replace("-", "");
        return str.substring(0, Math.min(8, str.length())) + "...";
    }

    /**
     * Extract the network UUID from a placed Create logistics block (Stock Ticker,
     * Stock Link, Factory Gauge, etc.). These blocks use
     * LogisticallyLinkedBehaviour which stores the network frequency ID.
     *
     * @param level
     *            the world.
     * @param pos
     *            the block position.
     * @return the network UUID, or null if not a valid logistics block.
     */
    @Nullable
    public static UUID getNetworkIdFromBlock(final Level level, final BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }

        // Check if block entity is a SmartBlockEntity with LogisticallyLinkedBehaviour
        if (be instanceof SmartBlockEntity smartBE) {
            final LogisticallyLinkedBehaviour behaviour = smartBE.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            if (behaviour != null && behaviour.freqId != null) {
                return behaviour.freqId;
            }
        }

        return null;
    }
}
