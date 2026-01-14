package com.gr4v1ty.supplylines;

import com.gr4v1ty.supplylines.compat.structurize.CreateMultiblockPlacementHandler;
import com.gr4v1ty.supplylines.compat.structurize.CreateTrainBlockPreservationHandler;
import com.gr4v1ty.supplylines.network.ModNetwork;
import com.gr4v1ty.supplylines.registry.ModBlocks;
import com.gr4v1ty.supplylines.registry.ModBuildings;
import com.gr4v1ty.supplylines.registry.ModItems;
import com.gr4v1ty.supplylines.registry.ModJobs;
import com.gr4v1ty.supplylines.rs.SupplyLinesRequestSystem;
import com.gr4v1ty.supplylines.util.ModVersion;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.minecolonies.api.sounds.ModSoundEvents;
import org.slf4j.LoggerFactory;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.gr4v1ty.supplylines.colony.manager.RequestHandler;
import org.slf4j.Logger;

@Mod(value = "supplylines")
@SuppressWarnings("removal") // ResourceLocation and FMLJavaModLoadingContext deprecated in Forge 47.x, will
                             // migrate in 1.21
public final class SupplyLines {
    public static final ResourceLocation JOB_ID = new ResourceLocation("supplylines", "stock_keeper");
    public static final ResourceLocation BUILDING_ID = new ResourceLocation("supplylines", "stock_keeper_hut");
    public static final String MC_ID = "minecolonies";
    public static final String MOD_ID = "supplylines";
    public static final String BUILDINGS = "buildings";
    public static final String JOBS = "jobs";
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplyLines.class);

    public SupplyLines() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModJobs.JOBS.register(modBus);
        ModBuildings.BUILDINGS.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        modBus.addListener((FMLCommonSetupEvent e) -> {
            LOGGER.info("[{}] Version {} loaded", MOD_ID, ModVersion.get().getDisplayVersion());
            if (ModVersion.get().isDevBuild()) {
                LOGGER.warn("[{}] This is a development build - not for production use", MOD_ID);
            }
            ModNetwork.init();
            try {
                SupplyLinesRequestSystem.registerFactories();
            } catch (Exception ex) {
                LOGGER.error("[{}] Failed to register Request System factories", (Object) MOD_ID, (Object) ex);
            }
            e.enqueueWork(() -> {
                PlacementHandlers.add(new CreateTrainBlockPreservationHandler());
                PlacementHandlers.add(new CreateMultiblockPlacementHandler());
                String[] fallbacks = new String[]{"deliveryman", "unemployed", "builder"};
                @SuppressWarnings("rawtypes")
                Map map = ModSoundEvents.CITIZEN_SOUND_EVENTS;
                if (map != null && !map.containsKey("stock_keeper")) {
                    for (String fb : fallbacks) {
                        Object bucket = map.get(fb);
                        if (bucket == null)
                            continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedMap = map;
                        typedMap.put("stock_keeper", bucket);
                        break;
                    }
                }
            });
        });
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.debug("[{}] Server stopping - clearing registration tracking", MOD_ID);
        RequestHandler.clearRegistrationTracking();
    }
}
