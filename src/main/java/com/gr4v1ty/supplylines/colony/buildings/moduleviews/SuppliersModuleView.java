package com.gr4v1ty.supplylines.colony.buildings.moduleviews;

import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side view for the suppliers module. Displays and manages remote Create
 * stock network suppliers.
 */
public class SuppliersModuleView extends AbstractBuildingModuleView {
    /** Required building level for this tab to be visible. */
    private static final int REQUIRED_LEVEL = 5;

    /** Local copy of supplier entries from server. */
    private final List<SuppliersModule.SupplierEntry> suppliers = new ArrayList<>();

    @Override
    public void deserialize(@NotNull FriendlyByteBuf buf) {
        suppliers.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            suppliers.add(SuppliersModule.SupplierEntry.fromBuf(buf));
        }
    }

    @Override
    public boolean isPageVisible() {
        return getBuildingView().getBuildingLevel() >= REQUIRED_LEVEL;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow() {
        // Lazy import to avoid client class loading on server
        return createWindow();
    }

    @OnlyIn(Dist.CLIENT)
    private BOWindow createWindow() {
        return new com.gr4v1ty.supplylines.colony.buildings.modulewindows.SuppliersModuleWindow(this);
    }

    @Override
    @SuppressWarnings("removal")
    public ResourceLocation getIconResourceLocation() {
        return new ResourceLocation("minecolonies", "textures/gui/modules/connection.png");
    }

    @Override
    public String getDesc() {
        return "com.supplylines.gui.stockkeeper.suppliers";
    }

    /**
     * Get the list of suppliers. Note: Returns mutable list for local GUI updates.
     *
     * @return list of supplier entries.
     */
    public List<SuppliersModule.SupplierEntry> getSuppliers() {
        return suppliers;
    }
}
