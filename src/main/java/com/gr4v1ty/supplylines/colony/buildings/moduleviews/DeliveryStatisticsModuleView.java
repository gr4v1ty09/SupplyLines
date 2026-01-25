package com.gr4v1ty.supplylines.colony.buildings.moduleviews;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.colony.managers.StatisticsManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side view for the Stock Keeper statistics module. Displays building
 * activity metrics with time-period filtering.
 */
public class DeliveryStatisticsModuleView extends AbstractBuildingModuleView {

    /** The statistics manager instance for client display. */
    private IStatisticsManager statisticsManager = new StatisticsManager();

    @Override
    public void deserialize(@NotNull FriendlyByteBuf buf) {
        statisticsManager.deserialize(buf);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow() {
        return createWindow();
    }

    @OnlyIn(Dist.CLIENT)
    private BOWindow createWindow() {
        return new com.gr4v1ty.supplylines.colony.buildings.modulewindows.DeliveryStatisticsModuleWindow(this);
    }

    @Override
    @SuppressWarnings("removal")
    public ResourceLocation getIconResourceLocation() {
        return new ResourceLocation("minecolonies", "textures/gui/modules/stats.png");
    }

    @Override
    @Nullable
    public Component getDesc() {
        return Component.translatable("com.supplylines.gui.stockkeeper.statistics");
    }

    /**
     * Get the statistics manager for this building.
     *
     * @return the statistics manager.
     */
    public IStatisticsManager getBuildingStatisticsManager() {
        return statisticsManager;
    }
}
