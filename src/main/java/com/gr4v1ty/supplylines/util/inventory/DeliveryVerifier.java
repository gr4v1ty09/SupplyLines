package com.gr4v1ty.supplylines.util.inventory;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.items.IItemHandler;

/**
 * Utility methods for verifying deliveries have arrived at their destination.
 */
public final class DeliveryVerifier {
    private DeliveryVerifier() {
    }

    /**
     * Generic delivery verification method.
     *
     * @param level
     *            The world level
     * @param dest
     *            The destination location
     * @param neededCount
     *            The minimum count required
     * @param matcher
     *            A predicate to match valid items
     * @return true if enough matching items are found at the destination
     */
    public static boolean verify(Level level, ILocation dest, int neededCount, Predicate<ItemStack> matcher) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(dest.getInDimensionLocation());
        if (be == null) {
            return false;
        }
        IItemHandler dst = InventoryOperations.getItemHandlerWithFallback(be, null);
        if (dst == null) {
            return false;
        }
        int have = 0;
        for (int i = 0; i < dst.getSlots(); ++i) {
            ItemStack s = dst.getStackInSlot(i);
            if (!s.isEmpty() && matcher.test(s)) {
                have += s.getCount();
                if (have >= neededCount) {
                    return true;
                }
            }
        }
        return false;
    }

    // Convenience methods for specific request types

    public static boolean verifyDelivery(Level level, ILocation dest, Stack wanted) {
        int needed = Math.max(wanted.getCount(), wanted.getMinimumCount());
        return verify(level, dest, needed, wanted::matches);
    }

    public static boolean verifyToolDelivery(Level level, ILocation dest, Tool wanted) {
        return verify(level, dest, 1, wanted::matches);
    }

    public static boolean verifyTagDelivery(Level level, ILocation dest, RequestTag wanted) {
        int needed = Math.max(wanted.getCount(), wanted.getMinimumCount());
        return verify(level, dest, needed, wanted::matches);
    }

    public static boolean verifyStackListDelivery(Level level, ILocation dest, StackList wanted) {
        int needed = Math.max(wanted.getCount(), wanted.getMinimumCount());
        return verify(level, dest, needed, wanted::matches);
    }

    public static boolean verifyFoodDelivery(Level level, ILocation dest, Food wanted) {
        return verify(level, dest, wanted.getCount(), wanted::matches);
    }

    public static boolean verifyBurnableDelivery(Level level, ILocation dest, Burnable wanted) {
        return verify(level, dest, wanted.getCount(), FurnaceBlockEntity::isFuel);
    }
}
