package com.gr4v1ty.supplylines.compat.structurize;

import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;

public final class ModPlacementHandlers {
    private ModPlacementHandlers() {
    }

    public static void register() {
        PlacementHandlers.add(new CreateTrainBlockPreservationHandler());
        PlacementHandlers.add(new CreateMultiblockPlacementHandler());
    }
}
