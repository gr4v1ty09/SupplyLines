package com.gr4v1ty.supplylines.util.inventory;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

public final class DeliveryVerifier {
    private DeliveryVerifier() {
    }

    public static boolean verifyDelivery(Level level, ILocation dest, Stack wanted) {
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
        int needed = Math.max(wanted.getCount(), wanted.getMinimumCount());
        int have = 0;
        for (int i = 0; i < dst.getSlots(); ++i) {
            ItemStack s = dst.getStackInSlot(i);
            if (s.isEmpty() || !wanted.matches(s) || (have += s.getCount()) < needed)
                continue;
            return true;
        }
        return false;
    }

    public static boolean verifyToolDelivery(Level level, ILocation dest, Tool wanted) {
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
        for (int i = 0; i < dst.getSlots(); ++i) {
            ItemStack s = dst.getStackInSlot(i);
            if (s.isEmpty() || !wanted.matches(s))
                continue;
            return true;
        }
        return false;
    }

    public static boolean verifyTagDelivery(Level level, ILocation dest, RequestTag wanted) {
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
        int needed = Math.max(wanted.getCount(), wanted.getMinimumCount());
        int have = 0;
        for (int i = 0; i < dst.getSlots(); ++i) {
            ItemStack s = dst.getStackInSlot(i);
            if (s.isEmpty() || !wanted.matches(s) || (have += s.getCount()) < needed)
                continue;
            return true;
        }
        return false;
    }

    public static boolean verifyStackListDelivery(Level level, ILocation dest, StackList wanted) {
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
        int needed = Math.max(wanted.getCount(), wanted.getMinimumCount());
        int have = 0;
        for (int i = 0; i < dst.getSlots(); ++i) {
            ItemStack s = dst.getStackInSlot(i);
            if (s.isEmpty() || !wanted.matches(s) || (have += s.getCount()) < needed)
                continue;
            return true;
        }
        return false;
    }

    public static boolean verifyFoodDelivery(Level level, ILocation dest, Food wanted) {
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
        int needed = wanted.getCount();
        int have = 0;
        for (int i = 0; i < dst.getSlots(); ++i) {
            ItemStack s = dst.getStackInSlot(i);
            if (s.isEmpty() || !wanted.matches(s) || (have += s.getCount()) < needed)
                continue;
            return true;
        }
        return false;
    }
}
