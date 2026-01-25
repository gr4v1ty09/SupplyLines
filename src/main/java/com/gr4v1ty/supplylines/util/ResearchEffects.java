package com.gr4v1ty.supplylines.util;

import com.gr4v1ty.supplylines.SupplyLines;
import net.minecraft.resources.ResourceLocation;

/**
 * Constants for SupplyLines research effect IDs.
 */
public final class ResearchEffects {
    /**
     * Effect that increases the restock policy limit. Base is 20, with research
     * multiplier of 1.5 it becomes 50.
     */
    @SuppressWarnings("removal")
    public static final ResourceLocation RESTOCK_POLICY_LIMIT = new ResourceLocation(SupplyLines.MOD_ID,
            "effects/restockpolicylimit");

    /**
     * Effect that unlocks speculative ordering from remote supplier networks.
     * Binary unlock: effect strength > 0 means unlocked.
     */
    @SuppressWarnings("removal")
    public static final ResourceLocation SPECULATIVE_ORDERING = new ResourceLocation(SupplyLines.MOD_ID,
            "effects/speculativeordering");

    private ResearchEffects() {
    }
}
