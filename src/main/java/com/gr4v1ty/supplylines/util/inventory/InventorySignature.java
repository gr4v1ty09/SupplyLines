package com.gr4v1ty.supplylines.util.inventory;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

public final class InventorySignature {

    private InventorySignature() {
    }

    public static long computeInventorySignature(Level level, List<BlockPos> rackPositions, IItemHandler stagingHandler,
            int pendingRequestCount) {
        long h = -3750763034362895579L;
        List<IItemHandler> handlers = InventoryOperations.handlersAt(level, rackPositions);
        for (IItemHandler hnd : handlers) {
            h = InventorySignature.hashItemHandler(h, hnd);
        }
        if (stagingHandler != null) {
            int total = 0;
            for (int i = 0; i < stagingHandler.getSlots(); ++i) {
                ItemStack s = stagingHandler.getStackInSlot(i);
                if (s.isEmpty())
                    continue;
                total += s.getCount();
            }
            h = InventorySignature.hashLong(h, 0xFF00FF00L);
            h = InventorySignature.hashLong(h, (long) total & 0xFFFFFFFFL);
        }
        h = InventorySignature.hashLong(h, rackPositions != null ? (long) rackPositions.size() : 0L);
        h = InventorySignature.hashLong(h, pendingRequestCount);
        return h;
    }

    private static long hashItemHandler(long h, IItemHandler hnd) {
        int n = hnd.getSlots();
        for (int i = 0; i < n; ++i) {
            ItemStack s = hnd.getStackInSlot(i);
            if (s.isEmpty())
                continue;
            ResourceLocation rkey = ForgeRegistries.ITEMS.getKey(s.getItem());
            String key = "";
            if (rkey != null) {
                key = rkey.toString();
            }
            for (int c = 0; c < key.length(); ++c) {
                h ^= (long) key.charAt(c);
                h *= 1099511628211L;
            }
            int cnt = s.getCount();
            h ^= (long) (cnt & 0xFF);
            h *= 1099511628211L;
            h ^= (long) (cnt >>> 8 & 0xFF);
            h *= 1099511628211L;
            if (!s.hasTag())
                continue;
            h ^= 0xA5L;
            h *= 1099511628211L;
        }
        return h;
    }

    private static long hashLong(long h, long value) {
        h ^= value;
        return h *= 1099511628211L;
    }
}
