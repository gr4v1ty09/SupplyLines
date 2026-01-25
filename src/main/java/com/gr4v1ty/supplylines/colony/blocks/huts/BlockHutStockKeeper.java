package com.gr4v1ty.supplylines.colony.blocks.huts;

import com.gr4v1ty.supplylines.registry.ModBuildings;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlockHutStockKeeper extends AbstractBlockHut<BlockHutStockKeeper> {
    public BlockHutStockKeeper(BlockBehaviour.Properties props) {
        super(props);
    }

    public BlockHutStockKeeper() {
    }

    public String getHutName() {
        return "Stock Keeper Hut";
    }

    public BuildingEntry getBuildingEntry() {
        return ModBuildings.STOCK_KEEPER_HUT.get();
    }
}
