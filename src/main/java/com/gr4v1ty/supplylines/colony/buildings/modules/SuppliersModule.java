package com.gr4v1ty.supplylines.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
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

        public SupplierEntry(UUID networkId, int priority, String requestAddress, String label) {
            this.networkId = networkId;
            this.priority = priority;
            this.requestAddress = requestAddress != null ? requestAddress : "";
            this.label = label != null ? label : "";
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

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_NETWORK_ID, networkId);
            tag.putInt(TAG_PRIORITY, priority);
            tag.putString(TAG_REQUEST_ADDRESS, requestAddress);
            tag.putString(TAG_LABEL, label);
            return tag;
        }

        public static SupplierEntry fromNBT(CompoundTag tag) {
            UUID id = tag.getUUID(TAG_NETWORK_ID);
            int priority = tag.getInt(TAG_PRIORITY);
            String address = tag.contains(TAG_REQUEST_ADDRESS) ? tag.getString(TAG_REQUEST_ADDRESS) : "";
            String name = tag.contains(TAG_LABEL) ? tag.getString(TAG_LABEL) : "";
            return new SupplierEntry(id, priority, address, name);
        }

        public void toBuf(FriendlyByteBuf buf) {
            buf.writeUUID(networkId);
            buf.writeInt(priority);
            buf.writeUtf(requestAddress);
            buf.writeUtf(label);
        }

        public static SupplierEntry fromBuf(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            int priority = buf.readInt();
            String address = buf.readUtf();
            String name = buf.readUtf();
            return new SupplierEntry(id, priority, address, name);
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
        }
    }
}
