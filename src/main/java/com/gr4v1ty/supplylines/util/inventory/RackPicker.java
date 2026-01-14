package com.gr4v1ty.supplylines.util.inventory;

import com.gr4v1ty.supplylines.rs.location.RackLocation;
import com.gr4v1ty.supplylines.rs.util.DeliveryPlanning;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * Utility class for picking items from MineColonies racks to fulfill requests.
 * Uses a generic template method pattern to avoid code duplication across
 * different requestable types (Stack, Tool, RequestTag, StackList, Food).
 */
public final class RackPicker {
    private static final Direction[] FACES = new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST};

    private RackPicker() {
    }

    // -------------------------------------------------------------------------
    // Public API - Type-specific convenience methods
    // -------------------------------------------------------------------------

    public static List<DeliveryPlanning.Pick> pickFromRacks(Level level, IColony colony, List<BlockPos> rackPositions,
            Stack wanted) {
        return pickGeneric(level, colony, rackPositions, Math.max(wanted.getCount(), wanted.getMinimumCount()),
                wanted::matches, (inSlot, take) -> wanted, // Use the original Stack requestable
                false);
    }

    public static List<DeliveryPlanning.Pick> pickToolFromRacks(Level level, IColony colony,
            List<BlockPos> rackPositions, Tool wanted) {
        return pickGeneric(level, colony, rackPositions, 1, // Tools need exactly 1
                wanted::matches, (inSlot, take) -> new Stack(inSlot, 1, 1), true // Single item mode - return on first
                                                                                 // match
        );
    }

    public static List<DeliveryPlanning.Pick> pickFromRacksByTag(Level level, IColony colony,
            List<BlockPos> rackPositions, RequestTag wanted) {
        return pickGeneric(level, colony, rackPositions, Math.max(wanted.getCount(), wanted.getMinimumCount()),
                wanted::matches, (inSlot, take) -> new Stack(inSlot, take, 1), false);
    }

    public static List<DeliveryPlanning.Pick> pickFromRacksByStackList(Level level, IColony colony,
            List<BlockPos> rackPositions, StackList wanted) {
        return pickGeneric(level, colony, rackPositions, Math.max(wanted.getCount(), wanted.getMinimumCount()),
                wanted::matches, (inSlot, take) -> new Stack(inSlot, take, 1), false);
    }

    public static List<DeliveryPlanning.Pick> pickFoodFromRacks(Level level, IColony colony,
            List<BlockPos> rackPositions, Food wanted) {
        return pickGeneric(level, colony, rackPositions, wanted.getCount(), wanted::matches,
                (inSlot, take) -> new Stack(inSlot, take, 1), false);
    }

    public static List<DeliveryPlanning.Pick> pickBurnableFromRacks(Level level, IColony colony,
            List<BlockPos> rackPositions, Burnable wanted) {
        return pickGeneric(level, colony, rackPositions, wanted.getCount(), FurnaceBlockEntity::isFuel,
                (inSlot, take) -> new Stack(inSlot, take, 1), false);
    }

    // -------------------------------------------------------------------------
    // Generic implementation
    // -------------------------------------------------------------------------

    /**
     * Generic item picking from racks with pluggable matching and wrapping logic.
     *
     * @param level
     *            The world level
     * @param colony
     *            The colony instance
     * @param rackPositions
     *            List of rack block positions to search
     * @param totalNeeded
     *            Total number of items needed
     * @param matcher
     *            Predicate to test if an ItemStack matches the request
     * @param wrapperFactory
     *            Factory to create the Stack payload for picks
     * @param singleItemMode
     *            If true, return immediately after first match (for tools)
     * @return List of picks, or empty list if nothing found
     */
    private static List<DeliveryPlanning.Pick> pickGeneric(Level level, IColony colony, List<BlockPos> rackPositions,
            int totalNeeded, Predicate<ItemStack> matcher, BiFunction<ItemStack, Integer, Stack> wrapperFactory,
            boolean singleItemMode) {
        // Validation
        if (level == null || level.isClientSide() || colony == null) {
            return List.of();
        }
        if (rackPositions == null || rackPositions.isEmpty()) {
            return List.of();
        }
        if (totalNeeded <= 0) {
            return List.of();
        }

        ArrayList<DeliveryPlanning.Pick> picks = new ArrayList<>();
        Set<Long> pickedSlots = new HashSet<>();
        int remaining = totalNeeded;

        rackLoop : for (BlockPos rackPos : rackPositions) {
            BlockEntity be = level.getBlockEntity(rackPos);
            if (be == null) {
                continue;
            }

            for (Direction face : FACES) {
                IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).orElse(null);
                if (handler == null) {
                    continue;
                }

                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    // Generate unique key for this slot to avoid double-picking
                    // Uses XOR of block position and slot shifted to high bits
                    long slotKey = rackPos.asLong() ^ ((long) slot << 48);
                    if (pickedSlots.contains(slotKey)) {
                        continue;
                    }

                    ItemStack inSlot = handler.getStackInSlot(slot);
                    if (inSlot.isEmpty() || !matcher.test(inSlot)) {
                        continue;
                    }

                    int take = Math.min(remaining, inSlot.getCount());
                    if (take <= 0) {
                        continue;
                    }

                    pickedSlots.add(slotKey);

                    RackLocation sourceLoc = new RackLocation(colony.getDimension(), rackPos, face, slot);

                    Stack payload = wrapperFactory.apply(inSlot, take);
                    picks.add(new DeliveryPlanning.Pick(sourceLoc, payload, take, null));

                    if (singleItemMode) {
                        // Tool mode: return immediately with single item
                        return List.copyOf(picks);
                    }

                    remaining -= take;
                    if (remaining <= 0) {
                        break rackLoop;
                    }
                }
            }
        }

        return picks.isEmpty() ? List.of() : List.copyOf(picks);
    }
}
