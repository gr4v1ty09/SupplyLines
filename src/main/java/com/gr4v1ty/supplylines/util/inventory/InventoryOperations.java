package com.gr4v1ty.supplylines.util.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class InventoryOperations {
    private InventoryOperations() {
    }

    public static IItemHandler handlerAt(Level lvl, BlockPos pos) {
        if (lvl == null || pos == null) {
            return null;
        }
        BlockEntity be = lvl.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        LazyOptional cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        return (IItemHandler) cap.orElse(null);
    }

    public static List<IItemHandler> handlersAt(Level lvl, List<BlockPos> poses) {
        ArrayList<IItemHandler> list = new ArrayList<IItemHandler>();
        if (poses == null) {
            return list;
        }
        for (BlockPos p : poses) {
            IItemHandler h = InventoryOperations.handlerAt(lvl, p);
            if (h == null)
                continue;
            list.add(h);
        }
        return list;
    }

    public static IItemHandler getItemHandlerWithFallback(BlockEntity be, Direction preferredFace) {
        IItemHandler handler;
        if (be == null) {
            return null;
        }
        if (preferredFace != null && (handler = (IItemHandler) be
                .getCapability(ForgeCapabilities.ITEM_HANDLER, preferredFace).orElse(null)) != null) {
            return handler;
        }
        handler = (IItemHandler) be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler != null) {
            return handler;
        }
        for (Direction d : Direction.values()) {
            handler = (IItemHandler) be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElse(null);
            if (handler == null)
                continue;
            return handler;
        }
        return null;
    }

    public static int countMatching(IItemHandler handler, BiPredicate<ItemStack, ItemStack> matches,
            ItemStack request) {
        if (handler == null || request == null || request.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty() || !matches.test(s, request))
                continue;
            total += s.getCount();
        }
        return total;
    }

    public static int countMatchingAcross(List<IItemHandler> handlers, BiPredicate<ItemStack, ItemStack> matches,
            ItemStack request) {
        if (handlers == null || handlers.isEmpty() || request == null || request.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (IItemHandler h : handlers) {
            total += InventoryOperations.countMatching(h, matches, request);
        }
        return total;
    }

    public static ItemStack extractMatching(List<IItemHandler> sources, BiPredicate<ItemStack, ItemStack> matches,
            ItemStack request, int want) {
        if (sources == null || sources.isEmpty() || request == null || request.isEmpty() || want <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack carried = ItemStack.EMPTY;
        int remaining = want;
        for (IItemHandler h : sources) {
            for (int slot = 0; slot < h.getSlots() && remaining > 0; ++slot) {
                ItemStack got;
                int toTake;
                ItemStack sim;
                ItemStack s = h.getStackInSlot(slot);
                if (s.isEmpty() || !matches.test(s, request)
                        || (sim = h.extractItem(slot, toTake = Math.min(remaining, s.getCount()), true)).isEmpty()
                        || (got = h.extractItem(slot, sim.getCount(), false)).isEmpty())
                    continue;
                if (carried.isEmpty()) {
                    carried = got.copy();
                } else {
                    carried.grow(got.getCount());
                }
                if ((remaining -= got.getCount()) <= 0)
                    break;
            }
            if (remaining > 0)
                continue;
            break;
        }
        return carried;
    }

    public static ItemStack insertAll(IItemHandler dst, ItemStack stack) {
        if (dst == null || stack == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < dst.getSlots() && !remaining.isEmpty(); ++i) {
            remaining = ItemHandlerHelper.insertItem((IItemHandler) dst, (ItemStack) remaining, (boolean) false);
        }
        return remaining;
    }

    public static boolean canAccept(IItemHandler handler, ItemStack exemplar) {
        if (handler == null) {
            return false;
        }
        if (exemplar == null || exemplar.isEmpty()) {
            return true;
        }
        ItemStack probe = exemplar.copy();
        probe.setCount(Math.min(probe.getCount(), probe.getMaxStackSize()));
        for (int i = 0; i < handler.getSlots() && !probe.isEmpty(); ++i) {
            probe = handler.insertItem(i, probe, true);
        }
        return probe.getCount() < exemplar.getCount();
    }

    public static boolean hasSpaceFor(IItemHandler handler, ItemStack item) {
        if (handler == null || item == null || item.isEmpty()) {
            return false;
        }
        ItemStack probe = item.copy();
        probe.setCount(item.getMaxStackSize());
        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack result = handler.insertItem(i, probe, true);
            if (result.getCount() >= probe.getCount())
                continue;
            return true;
        }
        return false;
    }
}
