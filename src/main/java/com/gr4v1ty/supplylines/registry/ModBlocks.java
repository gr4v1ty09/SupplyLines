package com.gr4v1ty.supplylines.registry;

import com.gr4v1ty.supplylines.colony.blocks.huts.BlockHutStockKeeper;
import com.minecolonies.api.blocks.AbstractBlockHut;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister
            .create((IForgeRegistry) ForgeRegistries.BLOCKS, (String) "supplylines");
    public static final RegistryObject<AbstractBlockHut<BlockHutStockKeeper>> STOCK_KEEPER_HUT = BLOCKS
            .register("stock_keeper_hut", () -> new BlockHutStockKeeper(
                    BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f).sound(SoundType.WOOD)));

    private ModBlocks() {
    }
}
