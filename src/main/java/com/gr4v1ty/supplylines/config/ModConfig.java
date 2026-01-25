package com.gr4v1ty.supplylines.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Main configuration holder for SupplyLines mod. Registers server
 * configuration.
 */
@SuppressWarnings("removal") // ModLoadingContext.get() deprecated in Forge 47.x, will migrate in 1.21
public final class ModConfig {
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<ServerConfig, ForgeConfigSpec> serverPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER = serverPair.getLeft();
        SERVER_SPEC = serverPair.getRight();
    }

    /**
     * Registers the configuration files with Forge. Call this from the mod
     * constructor before other registrations.
     */
    public static void register() {
        ModLoadingContext.get().registerConfig(Type.SERVER, SERVER_SPEC);
    }

    private ModConfig() {
    }
}
