package com.gr4v1ty.supplylines.registry;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModBuildings {
    @SuppressWarnings("removal") // ResourceLocation constructor deprecated in Forge 47.x, will migrate in 1.21
    public static final DeferredRegister<BuildingEntry> BUILDINGS = DeferredRegister
            .create(new ResourceLocation("minecolonies", "buildings"), "supplylines");
    public static final RegistryObject<BuildingEntry> STOCK_KEEPER_HUT = BUILDINGS.register("stock_keeper_hut",
            () -> new BuildingEntry.Builder().setRegistryName(SupplyLines.BUILDING_ID)
                    .setBuildingBlock((AbstractBlockHut<?>) ModBlocks.STOCK_KEEPER_HUT.get())
                    .setBuildingProducer(BuildingStockKeeper::new).setBuildingViewProducer(() -> EmptyView::new)
                    .addBuildingModuleProducer(ModBuildingModules.STOCK_KEEPER_WORK)
                    .addBuildingModuleProducer(ModBuildingModules.SUPPLIERS)
                    .addBuildingModuleProducer(ModBuildingModules.RESTOCK_POLICY)
                    .addBuildingModuleProducer(ModBuildingModules.DELIVERY_SETTINGS)
                    // Statistics module registered last to appear as bottom tab (MineColonies
                    // convention)
                    .addBuildingModuleProducer(ModBuildingModules.DELIVERY_STATISTICS).createBuildingEntry());

    private ModBuildings() {
    }
}
