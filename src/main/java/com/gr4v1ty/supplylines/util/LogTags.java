package com.gr4v1ty.supplylines.util;

/**
 * Logistics-themed log tag constants for consistent, filterable logging
 * throughout SupplyLines.
 */
public final class LogTags {
    private LogTags() {
    }

    /** Scheduling, pickup, transport operations */
    public static final String DELIVERY = "[Delivery]";

    /** Stock network requests, request placement */
    public static final String ORDERING = "[Ordering]";

    /** Rack scanning, stock snapshots */
    public static final String INVENTORY = "[Inventory]";

    /** Create network broadcasts, package sending */
    public static final String DISPATCH = "[Dispatch]";

    /** Delivery verification, request completion */
    public static final String FULFILLMENT = "[Fulfillment]";

    /** Automatic restocking from remote suppliers */
    public static final String RESTOCK = "[Restock]";

    /** Building upgrade data migration */
    public static final String MIGRATION = "[Migration]";
}
