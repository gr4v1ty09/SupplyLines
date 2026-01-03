package com.gr4v1ty.supplylines.registry;

import com.minecolonies.api.creativetab.ModCreativeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "supplylines", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCreativeTabsSetup {

    @SubscribeEvent
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModCreativeTabs.HUTS.get()) {
            event.accept(ModItems.STOCK_KEEPER_HUT_ITEM.get());
        }
    }

    private ModCreativeTabsSetup() {
    }
}
