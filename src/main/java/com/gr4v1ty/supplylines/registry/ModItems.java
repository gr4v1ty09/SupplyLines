package com.gr4v1ty.supplylines.registry;

import com.gr4v1ty.supplylines.colony.items.ItemScepterStockKeeper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = "supplylines", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "supplylines");

    public static final RegistryObject<Item> STOCK_KEEPER_HUT_ITEM = ITEMS.register("stock_keeper_hut",
            () -> new BlockItem(ModBlocks.STOCK_KEEPER_HUT.get(), new Item.Properties()));

    public static final RegistryObject<Item> SCEPTER_STOCKKEEPER = ITEMS.register("scepterstockkeeper",
            () -> new ItemScepterStockKeeper(new Item.Properties()));

    private ModItems() {
    }
}
