package com.gr4v1ty.supplylines.colony.buildings.moduleviews;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side view for the restock policy module. Displays and manages item
 * restock policies.
 */
public class RestockPolicyModuleView extends AbstractBuildingModuleView {
    /** Local copy of policy entries from server. */
    private final List<RestockPolicyModule.PolicyEntry> policies = new ArrayList<>();

    /** Whether the policy limit has been reached. */
    private boolean reachedLimit = false;

    @Override
    public void deserialize(@NotNull FriendlyByteBuf buf) {
        policies.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            policies.add(RestockPolicyModule.PolicyEntry.fromBuf(buf));
        }
        reachedLimit = buf.readBoolean();
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
