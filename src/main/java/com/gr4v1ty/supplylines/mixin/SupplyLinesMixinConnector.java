package com.gr4v1ty.supplylines.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class SupplyLinesMixinConnector implements IMixinConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplyLinesMixinConnector.class);

    public void connect() {
        LOGGER.debug("Registering mixin config: supplylines.mixins.json");
        try {
            Mixins.addConfiguration((String) "supplylines.mixins.json");
        } catch (Exception e) {
            LOGGER.error("Failed to register mixin config: {}", e.getMessage(), e);
            throw e;
        }
    }
}
