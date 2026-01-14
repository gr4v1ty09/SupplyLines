package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.simibubi.create.AllBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import com.gr4v1ty.supplylines.util.LogTags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BuildingBlockScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingBlockScanner.class);
    @SuppressWarnings("removal") // ResourceLocation constructor deprecated in Forge 47.x, will migrate in 1.21
    public static final ResourceLocation RACK_ID = new ResourceLocation("minecolonies", "blockminecoloniesrack");
    private static final int AUXILIARY_SCAN_RADIUS = 16;
    @Nullable
    private final Block rackBlock = ForgeRegistries.BLOCKS.getValue(RACK_ID);
    private final List<BlockPos> rackPositions = new ArrayList<BlockPos>();
    private BlockPos stockTickerPos;
    private BlockPos seatPos;
    private BlockPos displayBoardPos;
    private long lastScanGameTime = Long.MIN_VALUE;
    private final IBuilding building;

    public BuildingBlockScanner(IBuilding building) {
        this.building = building;
    }

    public boolean rescan(Level level, int buildingLevel) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        HashSet<BlockPos> prevRacks = new HashSet<BlockPos>(this.rackPositions);
        this.rackPositions.clear();
        this.rackPositions.addAll(this.scanRacks(level));
        this.lastScanGameTime = Math.max(1L, level.getGameTime());
        if (buildingLevel >= BuildingStockKeeper.STOCK_TICKER_REQUIRED_LEVEL) {
            this.stockTickerPos = this.findNearestBlock(level, this.building.getPosition(), AUXILIARY_SCAN_RADIUS,
                    (Block) AllBlocks.STOCK_TICKER.get());
            this.seatPos = this.findNearestSeat(level);
            this.displayBoardPos = this.findNearestBlock(level, this.building.getPosition(), AUXILIARY_SCAN_RADIUS,
                    (Block) AllBlocks.DISPLAY_BOARD.get());
        }
        boolean changed = !prevRacks.equals(new HashSet<BlockPos>(this.rackPositions));
        return changed;
    }

    public boolean isScanDue(Level level, int rescanIntervalTicks) {
        long t = level.getGameTime();
        return this.lastScanGameTime == Long.MIN_VALUE || t - this.lastScanGameTime >= (long) rescanIntervalTicks;
    }

    public boolean hasValidTargets(Level level) {
        return level != null && !this.rackPositions.isEmpty();
    }

    public List<BlockPos> getRackPositions() {
        return Collections.unmodifiableList(this.rackPositions);
    }

    @Nullable
    public BlockPos getStockTickerPos() {
        return this.stockTickerPos;
    }

    @Nullable
    public BlockPos getSeatPos() {
        return this.seatPos;
    }

    @Nullable
    public BlockPos getDisplayBoardPos() {
        return this.displayBoardPos;
    }

    /**
     * Gets racks belonging to this building using MineColonies' container registry.
     * This ensures we only use racks that are part of our building, not nearby
     * buildings.
     */
    private List<BlockPos> scanRacks(Level level) {
        if (level == null || this.rackBlock == null || this.building == null) {
            return List.of();
        }
        ArrayList<BlockPos> racks = new ArrayList<BlockPos>();
        // Use MineColonies' container list which tracks racks registered to this
        // building
        for (BlockPos pos : this.building.getContainers()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(this.rackBlock)) {
                racks.add(pos);
            }
        }
        return racks;
    }

    @Nullable
    private BlockPos findNearestBlock(Level level, BlockPos center, int radius, Block targetBlock) {
        if (level == null || targetBlock == null) {
            LOGGER.warn("{} findNearestBlock: level or targetBlock is null", LogTags.INVENTORY);
            return null;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int r = Math.max(1, radius);
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; ++dx) {
            for (int dy = -2; dy <= 3; ++dy) {
                for (int dz = -r; dz <= r; ++dz) {
                    double distSq;
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState((BlockPos) m);
                    if (!state.is(targetBlock) || !((distSq = center.distSqr((Vec3i) m)) < nearestDistSq))
                        continue;
                    nearest = m.immutable();
                    nearestDistSq = distSq;
                }
            }
        }
        return nearest;
    }

    @Nullable
    private BlockPos findNearestSeat(Level level) {
        if (level == null || this.building == null) {
            return null;
        }
        for (DyeColor color : DyeColor.values()) {
            try {
                Block seatBlock = (Block) AllBlocks.SEATS.get(color).get();
                BlockPos found = this.findNearestBlock(level, this.building.getPosition(), AUXILIARY_SCAN_RADIUS,
                        seatBlock);
                if (found == null)
                    continue;
                return found;
            } catch (Exception e) {
                // Seat block not available for this color - continue to next color
                LOGGER.debug("{} Seat block not found for color {}: {}", LogTags.INVENTORY, color, e.getMessage());
            }
        }
        return null;
    }
}
