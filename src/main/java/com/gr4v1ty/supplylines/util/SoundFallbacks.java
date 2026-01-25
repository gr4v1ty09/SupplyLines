package com.gr4v1ty.supplylines.util;

import com.minecolonies.api.sounds.ModSoundEvents;
import java.util.Map;

/**
 * Handles sound event fallbacks for custom jobs that don't have built-in
 * MineColonies sound definitions.
 */
public final class SoundFallbacks {
    private static final String[] FALLBACK_JOBS = {"deliveryman", "unemployed", "builder"};

    private SoundFallbacks() {
    }

    /**
     * Registers fallback sounds for the stock_keeper job by borrowing from existing
     * similar MineColonies jobs.
     */
    @SuppressWarnings("rawtypes")
    public static void registerStockKeeperSounds() {
        Map map = ModSoundEvents.CITIZEN_SOUND_EVENTS;
        if (map != null && !map.containsKey("stock_keeper")) {
            for (String fb : FALLBACK_JOBS) {
                Object bucket = map.get(fb);
                if (bucket == null)
                    continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = map;
                typedMap.put("stock_keeper", bucket);
                break;
            }
        }
    }
}
