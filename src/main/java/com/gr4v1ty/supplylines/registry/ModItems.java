package com.gr4v1ty.supplylines.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = "supplylines", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry) ForgeRegistries.ITEMS,
            (String) "supplylines");
    public static final RegistryObject<Item> STOCK_KEEPER_HUT_ITEM = ITEMS.register("stock_keeper_hut",
            () -> new BlockItem((Block) ModBlocks.STOCK_KEEPER_HUT.get(), new Item.Properties()));

    private ModItems() {
    }
}
