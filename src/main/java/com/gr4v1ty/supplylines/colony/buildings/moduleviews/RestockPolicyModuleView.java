package com.gr4v1ty.supplylines.colony.buildings.moduleviews;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side view for the restock policy module. Displays and manages item
 * restock policies.
 */
public class RestockPolicyModuleView extends AbstractBuildingModuleView {
    /** Local copy of policy entries from server. */
    private final List<RestockPolicyModule.PolicyEntry> policies = new ArrayList<>();

    /**
     * Local stock levels (hut vault) for each policy item, keyed by ItemStorage.
     */
    private final Map<ItemStorage, Long> localStockLevels = new HashMap<>();

    /**
     * Remote stock levels (sum across suppliers) for each policy item, keyed by
     * ItemStorage.
     */
    private final Map<ItemStorage, Long> remoteStockLevels = new HashMap<>();

    /** Whether the policy limit has been reached. */
    private boolean reachedLimit = false;

    @Override
    public void deserialize(@NotNull FriendlyByteBuf buf) {
        policies.clear();
        localStockLevels.clear();
        remoteStockLevels.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            RestockPolicyModule.PolicyEntry entry = RestockPolicyModule.PolicyEntry.fromBuf(buf);
            long localStock = buf.readLong();
            long remoteStock = buf.readLong();
            policies.add(entry);
            localStockLevels.put(entry.getItem(), localStock);
            remoteStockLevels.put(entry.getItem(), remoteStock);
        }
        reachedLimit = buf.readBoolean();
    }

    /**
     * Get the local stock level (hut vault) for a policy item.
     *
     * @param item
     *            the item storage to look up.
     * @return the local stock level, or 0 if not found.
     */
    public long getLocalStockLevel(ItemStorage item) {
        return localStockLevels.getOrDefault(item, 0L);
    }

    /**
     * Get the remote stock level (sum across suppliers) for a policy item.
     *
     * @param item
     *            the item storage to look up.
     * @return the remote stock level, or 0 if not found.
     */
    public long getRemoteStockLevel(ItemStorage item) {
        return remoteStockLevels.getOrDefault(item, 0L);
    }

    @Override
    public boolean isPageVisible() {
        return getBuildingView().getBuildingLevel() >= BuildingStockKeeper.getRestockPolicyRequiredLevel();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow() {
        return createWindow();
    }

    @OnlyIn(Dist.CLIENT)
    private BOWindow createWindow() {
        return new com.gr4v1ty.supplylines.colony.buildings.modulewindows.RestockPolicyModuleWindow(this);
    }

    @Override
    @SuppressWarnings("removal")
    public ResourceLocation getIconResourceLocation() {
        return new ResourceLocation("minecolonies", "textures/gui/modules/stock.png");
    }

    @Override
    @Nullable
    public Component getDesc() {
        return Component.translatable("com.supplylines.gui.stockkeeper.restockpolicy");
    }

    /**
     * Get the list of policies. Returns a mutable list for client-side UI updates.
     *
     * @return list of policy entries.
     */
    public List<RestockPolicyModule.PolicyEntry> getPolicies() {
        return policies;
    }

    /**
     * Add a policy entry to the local list. Used for optimistic UI updates before
     * server confirmation.
     *
     * @param entry
     *            the policy entry to add.
     */
    public void addPolicy(final RestockPolicyModule.PolicyEntry entry) {
        policies.add(entry);
    }

    /**
     * Check if the policy limit has been reached.
     *
     * @return true if at max capacity.
     */
    public boolean hasReachedLimit() {
        return reachedLimit;
    }
}
