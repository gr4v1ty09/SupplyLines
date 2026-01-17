package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.util.ItemMatch;
import java.util.Map;

/**
 * Functional interface for receiving notifications when stock levels change.
 * Used by NetworkIntegration to notify RestockManager of item arrivals.
 */
@FunctionalInterface
public interface StockChangeListener {
    /**
     * Called when stock levels have increased for one or more items.
     *
     * @param increases
     *            Map of item keys to the quantity increase (only positive deltas)
     */
    void onStockChanged(Map<ItemMatch.ItemStackKey, Long> increases);
}
