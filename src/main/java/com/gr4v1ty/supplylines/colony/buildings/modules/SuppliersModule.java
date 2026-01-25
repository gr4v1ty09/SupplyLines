package com.gr4v1ty.supplylines.colony.buildings.modules;

import com.gr4v1ty.supplylines.util.ResearchEffects;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-side module for managing remote Create stock network suppliers.
 * Unlocked at building level 5.
 */
public class SuppliersModule extends AbstractBuildingModule implements IPersistentModule {
    private static final String TAG_SUPPLIERS = "suppliers";
    private static final String TAG_NETWORK_ID = "networkId";
    private static final String TAG_PRIORITY = "priority";
    private static final String TAG_REQUEST_ADDRESS = "requestAddress";
    private static final String TAG_LABEL = "label";
    private static final String TAG_ALLOW_SPECULATIVE = "allowSpeculative";

    /**
     * Network status for display in the UI.
     */
    public enum NetworkStatus {
        /** Network is online and has items. */
        ONLINE,
        /** Network is reachable but has no items. */
        EMPTY,
        /** Network could not be queried (may be unloaded or invalid). */
        OFFLINE
    }

    /**
     * List of supplier entries, ordered by priority.
     */
    private final List<SupplierEntry> suppliers = new ArrayList<>();

    /**
     * Represents a supplier network entry.
     */
    public static class SupplierEntry {
        private final UUID networkId;
        private int priority;
        private String requestAddress;
        private String label;
        private boolean allowSpeculativeOrders;

        public SupplierEntry(UUID networkId, int priority, String requestAddress, String label) {
            this(networkId, priority, requestAddress, label, false);
        }

        public SupplierEntry(UUID networkId, int priority, String requestAddress, String label,
                boolean allowSpeculativeOrders) {
            this.networkId = networkId;
            this.priority = priority;
            this.requestAddress = requestAddress != null ? requestAddress : "";
            this.label = label != null ? label : "";
            this.allowSpeculativeOrders = allowSpeculativeOrders;
        }

        public UUID getNetworkId() {
            return networkId;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public String getRequestAddress() {
            return requestAddress;
        }

        public void setRequestAddress(String requestAddress) {
            this.requestAddress = requestAddress != null ? requestAddress : "";
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label != null ? label : "";
        }

        /**
         * Check if this supplier has a valid delivery address configured.
         *
         * @return true if the request address is non-empty.
         */
        public boolean hasValidAddress() {
            return requestAddress != null && !requestAddress.trim().isEmpty();
        }

        /**
         * Check if speculative ordering is allowed from this supplier.
         *
         * @return true if speculative orders are allowed.
         */
        public boolean allowsSpeculativeOrders() {
            return allowSpeculativeOrders;
        }

        /**
         * Set whether speculative ordering is allowed from this supplier.
         *
         * @param allowSpeculativeOrders
         *            true to allow speculative orders.
         */
        public void setAllowSpeculativeOrders(boolean allowSpeculativeOrders) {
            this.allowSpeculativeOrders = allowSpeculativeOrders;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_NETWORK_ID, networkId);
            tag.putInt(TAG_PRIORITY, priority);
            tag.putString(TAG_REQUEST_ADDRESS, requestAddress);
            tag.putString(TAG_LABEL, label);
            tag.putBoolean(TAG_ALLOW_SPECULATIVE, allowSpeculativeOrders);
            return tag;
        }

        public static SupplierEntry fromNBT(CompoundTag tag) {
            UUID id = tag.getUUID(TAG_NETWORK_ID);
            int priority = tag.getInt(TAG_PRIORITY);
            String address = tag.contains(TAG_REQUEST_ADDRESS) ? tag.getString(TAG_REQUEST_ADDRESS) : "";
            String label = tag.contains(TAG_LABEL) ? tag.getString(TAG_LABEL) : "";
            boolean allowSpeculative = tag.contains(TAG_ALLOW_SPECULATIVE)
                    ? tag.getBoolean(TAG_ALLOW_SPECULATIVE)
                    : false;
            return new SupplierEntry(id, priority, address, label, allowSpeculative);
        }

        public void toBuf(FriendlyByteBuf buf) {
            buf.writeUUID(networkId);
            buf.writeInt(priority);
            buf.writeUtf(requestAddress);
            buf.writeUtf(label);
            buf.writeBoolean(allowSpeculativeOrders);
        }

        public static SupplierEntry fromBuf(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            int priority = buf.readInt();
            String address = buf.readUtf();
            String label = buf.readUtf();
            boolean allowSpeculative = buf.readBoolean();
            return new SupplierEntry(id, priority, address, label, allowSpeculative);
        }
    }

    /**
     * Add a new supplier network.
     *
     * @param networkId
     *            the UUID of the Create logistics network.
     * @param requestAddress
     *            the destination address for package requests.
     * @return true if added, false if already exists.
     */
    public boolean addSupplier(UUID networkId, String requestAddress) {
        for (SupplierEntry entry : suppliers) {
            if (entry.getNetworkId().equals(networkId)) {
                return false;
            }
        }
        suppliers.add(new SupplierEntry(networkId, suppliers.size(), requestAddress, ""));
        sortByPriority();
        markDirty();
        return true;
    }

    /**
     * Set the request address for a supplier.
     *
     * @param networkId
     *            the supplier network UUID.
     * @param requestAddress
     *            the new request address.
     */
    public void setSupplierAddress(UUID networkId, String requestAddress) {
        for (SupplierEntry entry : suppliers) {
            if (entry.getNetworkId().equals(networkId)) {
                entry.setRequestAddress(requestAddress);
                markDirty();
                return;
            }
        }
    }

    /**
     * Set the label for a supplier.
     *
     * @param networkId
     *            the supplier network UUID.
     * @param label
     *            the new label.
     */
    public void setSupplierLabel(UUID networkId, String label) {
        for (SupplierEntry entry : suppliers) {
            if (entry.getNetworkId().equals(networkId)) {
                entry.setLabel(label);
                markDirty();
                return;
            }
        }
    }

    /**
     * Set whether speculative ordering is allowed for a supplier.
     *
     * @param networkId
     *            the supplier network UUID.
     * @param allowSpeculative
     *            true to allow speculative orders from this supplier.
     */
    public void setSupplierSpeculativeOrdering(UUID networkId, boolean allowSpeculative) {
        for (SupplierEntry entry : suppliers) {
            if (entry.getNetworkId().equals(networkId)) {
                entry.setAllowSpeculativeOrders(allowSpeculative);
                markDirty();
                return;
            }
        }
    }

    /**
     * Remove a supplier network.
     *
     * @param networkId
     *            the network UUID to remove.
     * @return true if removed.
     */
    public boolean removeSupplier(UUID networkId) {
        boolean removed = suppliers.removeIf(entry -> entry.getNetworkId().equals(networkId));
        if (removed) {
            reindexPriorities();
            markDirty();
        }
        return removed;
    }

    /**
     * Set the priority for a supplier.
     *
     * @param networkId
     *            the supplier network UUID.
     * @param priority
     *            the new priority (0 = highest).
     */
    public void setSupplierPriority(UUID networkId, int priority) {
        for (SupplierEntry entry : suppliers) {
            if (entry.getNetworkId().equals(networkId)) {
                entry.setPriority(priority);
                sortByPriority();
                markDirty();
                return;
            }
        }
    }

    /**
     * Get an unmodifiable list of suppliers ordered by priority.
     *
     * @return the supplier list.
     */
    public List<SupplierEntry> getSuppliers() {
        return Collections.unmodifiableList(suppliers);
    }

    /**
     * Check if any supplier has speculative ordering enabled.
     *
     * @return true if at least one supplier allows speculative orders.
     */
    public boolean hasAnySpeculativeSupplier() {
        for (SupplierEntry entry : suppliers) {
            if (entry.allowsSpeculativeOrders()) {
                return true;
            }
        }
        return false;
    }

    private void sortByPriority() {
        suppliers.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
    }

    private void reindexPriorities() {
        for (int i = 0; i < suppliers.size(); i++) {
            suppliers.get(i).setPriority(i);
        }
    }

    @Override
    public void deserializeNBT(@NotNull CompoundTag compound) {
        suppliers.clear();
        if (compound.contains(TAG_SUPPLIERS)) {
            ListTag list = compound.getList(TAG_SUPPLIERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                suppliers.add(SupplierEntry.fromNBT(list.getCompound(i)));
            }
            sortByPriority();
        }
    }

    @Override
    public void serializeNBT(@NotNull CompoundTag compound) {
        ListTag list = new ListTag();
        for (SupplierEntry entry : suppliers) {
            list.add(entry.toNBT());
        }
        compound.put(TAG_SUPPLIERS, list);
    }

    @Override
    public void serializeToView(@NotNull FriendlyByteBuf buf) {
        buf.writeInt(suppliers.size());
        for (SupplierEntry entry : suppliers) {
            entry.toBuf(buf);
            // Include network status for this supplier
            NetworkStatus status = getNetworkStatus(entry.getNetworkId());
            buf.writeEnum(status);
        }
        // Send whether speculative ordering research is unlocked
        boolean speculativeUnlocked = building.getColony().getResearchManager().getResearchEffects()
                .getEffectStrength(ResearchEffects.SPECULATIVE_ORDERING) > 0;
        buf.writeBoolean(speculativeUnlocked);
    }

    /**
     * Query the status of a Create logistics network.
     *
     * @param networkId
     *            the network UUID to query.
     * @return the network status.
     */
    private NetworkStatus getNetworkStatus(UUID networkId) {
        try {
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(networkId, false);
            if (summary == null) {
                return NetworkStatus.OFFLINE;
            }
            if (summary.isEmpty()) {
                return NetworkStatus.EMPTY;
            }
            return NetworkStatus.ONLINE;
        } catch (Exception e) {
            return NetworkStatus.OFFLINE;
        }
    }
}
