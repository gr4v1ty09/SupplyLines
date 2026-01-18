package com.gr4v1ty.supplylines.colony.manager;

import net.minecraft.world.item.ItemStack;

/**
 * Common interface for orders that should be displayed on the incoming
 * shipments board. Implemented by RestockOrder and SpeculativeOrder.
 */
public interface IncomingOrder {
    /**
     * @return The item being ordered.
     */
    ItemStack getItem();

    /**
     * @return The quantity ordered.
     */
    int getQuantity();

    /**
     * @return The game tick when the order is expected to arrive.
     */
    long getEstimatedArrivalTick();
}
