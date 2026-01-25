package com.gr4v1ty.supplylines.colony.manager.migration;

import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.colony.manager.migration.data.PanelMigrationData;
import com.gr4v1ty.supplylines.util.LogTags;
import com.minecolonies.api.crafting.ItemStorage;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages migration of Create Factory Panel configurations to RestockPolicies
 * during building upgrades from level 4 to level 5.
 */
public class PanelMigrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelMigrationManager.class);

    private PanelMigrationManager() {
        // Utility class
    }

    /**
     * Scans building bounds for FactoryPanelBlockEntity instances and extracts
     * their configurations.
     *
     * @param level
     *            The world
     * @param corners
     *            Building bounds (min, max)
     * @param fromLevel
     *            Current building level
     * @param toLevel
     *            Target building level
     * @return Migration data containing all panel configurations, or null if none
     *         found
     */
    @Nullable
    public static PanelMigrationData scanAndExtractPanelData(Level level, Tuple<BlockPos, BlockPos> corners,
            int fromLevel, int toLevel) {

        if (level == null || corners == null) {
            return null;
        }

        BlockPos min = corners.getA();
        BlockPos max = corners.getB();

        PanelMigrationData data = new PanelMigrationData(fromLevel, toLevel);
        int panelCount = 0;

        // Debug counters for diagnosing scan issues
        int factoryPanelBECount = 0;
        int restockerSkipCount = 0;
        int inactiveSlotCount = 0;
        int emptyFilterSkipCount = 0;
        int noNetworkSkipCount = 0;

        // Two-pass approach: collect gauge data, then correlate pairs
        // Supplier gauges (count=0) provide the network UUID
        // Requester gauges (count>0) provide the target quantity and address
        Map<ItemStorage, UUID> supplierNetworks = new HashMap<>();
        Map<ItemStorage, Integer> requestedCounts = new HashMap<>();
        Map<ItemStorage, String> addresses = new HashMap<>();

        LOGGER.info("{} Scanning for Factory Panels in bounds {} to {}", LogTags.MIGRATION, min, max);

        // First pass: collect gauge data
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);

                    if (be instanceof FactoryPanelBlockEntity panelBE) {
                        factoryPanelBECount++;
                        LOGGER.debug("{} Found FactoryPanelBlockEntity at {}", LogTags.MIGRATION, pos);

                        // Skip restocker panels (attached to packagers)
                        if (panelBE.restocker) {
                            restockerSkipCount++;
                            LOGGER.debug("{} Skipping restocker panel at {}", LogTags.MIGRATION, pos);
                            continue;
                        }

                        // Extract data from each active panel slot
                        for (PanelSlot slot : PanelSlot.values()) {
                            FactoryPanelBehaviour behaviour = panelBE.panels.get(slot);
                            if (behaviour == null || !behaviour.isActive()) {
                                if (behaviour != null) {
                                    inactiveSlotCount++;
                                    LOGGER.debug("{} Skipping inactive panel slot {} at {}", LogTags.MIGRATION, slot,
                                            pos);
                                }
                                continue;
                            }

                            ItemStack filter = behaviour.getFilter();
                            UUID panelNetwork = behaviour.network;
                            int count = behaviour.count;

                            // Log detailed info about this panel for diagnostics
                            LOGGER.debug("{} Panel slot {} at {}: filter={}, network={}, count={}", LogTags.MIGRATION,
                                    slot, pos, filter.isEmpty() ? "EMPTY" : filter.getDisplayName().getString(),
                                    panelNetwork, count);

                            if (filter.isEmpty()) {
                                emptyFilterSkipCount++;
                                continue;
                            }

                            if (panelNetwork == null) {
                                noNetworkSkipCount++;
                                continue;
                            }

                            ItemStorage itemKey = new ItemStorage(filter);

                            if (count == 0) {
                                // Supplier gauge - record the network
                                supplierNetworks.put(itemKey, panelNetwork);
                            } else {
                                // Requester gauge - record the count (converted to items) and address
                                // If upTo=true, count is already in items; if upTo=false, count is in stacks
                                int itemCount = behaviour.upTo ? count : count * filter.getMaxStackSize();
                                requestedCounts.put(itemKey, itemCount);

                                String address = behaviour.recipeAddress;
                                if (address != null && !address.isBlank()) {
                                    addresses.put(itemKey, address);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Second pass: correlate supplier and requester gauges by item
        for (ItemStorage item : supplierNetworks.keySet()) {
            UUID supplierNetwork = supplierNetworks.get(item);
            Integer count = requestedCounts.get(item);
            String address = addresses.get(item);

            if (count == null || count <= 0) {
                LOGGER.debug("{} Skipping {} - no requester gauge found", LogTags.MIGRATION,
                        item.getItemStack().getDisplayName().getString());
                continue;
            }

            PanelMigrationData.PanelConfig config = new PanelMigrationData.PanelConfig(item.getItemStack(), count, true,
                    supplierNetwork, address);
            data.addPanel(config);
            panelCount++;

            LOGGER.debug("{} Matched gauge pair: {} x{} -> network={}, address={}", LogTags.MIGRATION,
                    item.getItemStack().getDisplayName().getString(), count, supplierNetwork, address);
        }

        LOGGER.info("{} Extracted {} panel configurations for migration", LogTags.MIGRATION, panelCount);
        LOGGER.info(
                "{} Scan summary: {} FactoryPanelBE found, skipped: {} restocker, {} inactive, {} empty-filter, {} no-network",
                LogTags.MIGRATION, factoryPanelBECount, restockerSkipCount, inactiveSlotCount, emptyFilterSkipCount,
                noNetworkSkipCount);

        return data.isEmpty() ? null : data;
    }

    /**
     * Applies cached panel configurations to RestockPolicyModule and
     * SuppliersModule.
     *
     * @param data
     *            The cached migration data
     * @param policyModule
     *            The RestockPolicyModule to populate
     * @param suppliersModule
     *            The SuppliersModule to populate
     * @return Number of policies successfully created
     */
    public static int applyMigrationData(PanelMigrationData data, RestockPolicyModule policyModule,
            SuppliersModule suppliersModule) {

        if (data == null || data.isEmpty()) {
            return 0;
        }

        // Track networks we've already registered
        Set<UUID> registeredNetworks = new HashSet<>();

        // Track items we've already added policies for (to handle duplicates)
        Map<ItemStorage, Integer> existingPolicies = new HashMap<>();
        for (RestockPolicyModule.PolicyEntry entry : policyModule.getPolicies()) {
            existingPolicies.put(entry.getItem(), entry.getTargetQuantity());
        }

        // Track existing suppliers
        for (SuppliersModule.SupplierEntry entry : suppliersModule.getSuppliers()) {
            registeredNetworks.add(entry.getNetworkId());
        }

        int policiesCreated = 0;
        int suppliersCreated = 0;

        for (PanelMigrationData.PanelConfig panel : data.getPanels()) {
            // 1. Register the network as a supplier if address is configured and network
            // not already registered
            String address = panel.getRecipeAddress();
            if (address != null && !address.isBlank() && !registeredNetworks.contains(panel.getNetworkId())) {
                boolean added = suppliersModule.addSupplier(panel.getNetworkId(), address);
                if (added) {
                    registeredNetworks.add(panel.getNetworkId());
                    suppliersCreated++;
                    LOGGER.info("{} Registered supplier network {} with address '{}'", LogTags.MIGRATION,
                            panel.getNetworkId(), address);
                }
            }

            // 2. Add or update the restock policy
            ItemStorage itemStorage = new ItemStorage(panel.getFilterItem());
            int targetQuantity = panel.getTargetQuantity();

            // Check for duplicates - take the higher target quantity
            if (existingPolicies.containsKey(itemStorage)) {
                int existingQty = existingPolicies.get(itemStorage);
                if (targetQuantity <= existingQty) {
                    LOGGER.debug("{} Skipping duplicate policy for {} (existing: {}, new: {})", LogTags.MIGRATION,
                            panel.getFilterItem().getDisplayName().getString(), existingQty, targetQuantity);
                    continue;
                }
            }

            // Check policy limit
            if (policyModule.hasReachedLimit()) {
                LOGGER.warn("{} Reached max policy limit ({}), cannot add policy for {}", LogTags.MIGRATION,
                        policyModule.getMaxPolicies(), panel.getFilterItem().getDisplayName().getString());
                continue;
            }

            boolean added = policyModule.addOrUpdatePolicy(itemStorage, targetQuantity);
            if (added) {
                existingPolicies.put(itemStorage, targetQuantity);
                policiesCreated++;
                LOGGER.info("{} Created restock policy: {} -> {} items", LogTags.MIGRATION,
                        panel.getFilterItem().getDisplayName().getString(), targetQuantity);
            }
        }

        LOGGER.info("{} Migration complete: {} policies, {} suppliers created", LogTags.MIGRATION, policiesCreated,
                suppliersCreated);

        return policiesCreated;
    }
}
