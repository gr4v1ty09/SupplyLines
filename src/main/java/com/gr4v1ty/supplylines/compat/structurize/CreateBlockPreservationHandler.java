package com.gr4v1ty.supplylines.compat.structurize;

import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.PlacementSettings;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlock;
import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static com.ldtteam.structurize.api.util.constant.Constants.UPDATE_FLAG;

/**
 * Preserves Create blocks with complex internal state during Structurize
 * building upgrades. These blocks have state that cannot be easily restored
 * after replacement: - Train stations/tracks: track graph connections, station
 * names - Postboxes: addresses and package routing - Stock Tickers/Links:
 * logistics network freqId (UUID)
 *
 * When the same block type already exists at a position, this handler: - Skips
 * removal of the existing block (handleRemoval does nothing) - Skips placement
 * of the new block (handle returns SUCCESS without changing) - Reports no
 * required items (getRequiredItems returns empty)
 *
 * This ensures user-configured blocks survive building upgrades regardless of
 * what tile entity data the blueprint contains.
 */
public class CreateBlockPreservationHandler implements IPlacementHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateBlockPreservationHandler.class);

    @Override
    public boolean canHandle(Level world, BlockPos pos, BlockState blockState) {
        Block block = blockState.getBlock();
        return block instanceof StationBlock || block instanceof TrackBlock || block instanceof PostboxBlock
                || block instanceof StockTickerBlock || block instanceof PackagerLinkBlock;
    }

    @Override
    @SuppressWarnings("removal") // PlacementSettings deprecated in Structurize, required by interface
    public ActionProcessingResult handle(Level world, BlockPos pos, BlockState blockState,
            @Nullable CompoundTag tileEntityData, boolean complete, BlockPos centerPos, PlacementSettings settings) {

        BlockState existingState = world.getBlockState(pos);

        // Force preservation if same block type exists, REGARDLESS of tileEntityData
        if (existingState.getBlock().getClass() == blockState.getBlock().getClass()) {
            LOGGER.debug("Preserving existing {} at {}", existingState.getBlock().getClass().getSimpleName(), pos);
            return ActionProcessingResult.SUCCESS; // Claim we handled it, but don't change anything
        }

        // No existing block of same type - place normally
        if (!world.setBlock(pos, blockState, UPDATE_FLAG)) {
            return ActionProcessingResult.DENY;
        }
        if (tileEntityData != null) {
            PlacementHandlers.handleTileEntityPlacement(tileEntityData, world, pos, settings);
        }
        return ActionProcessingResult.SUCCESS;
    }

    @Override
    public void handleRemoval(IStructureHandler handler, Level world, BlockPos pos, CompoundTag tileEntityData) {
        BlockState existingState = world.getBlockState(pos);
        Block block = existingState.getBlock();

        // Never remove these block types - preserve user configuration
        if (block instanceof StationBlock || block instanceof TrackBlock || block instanceof PostboxBlock
                || block instanceof StockTickerBlock || block instanceof PackagerLinkBlock) {
            LOGGER.debug("Skipping removal of {} at {}", block.getClass().getSimpleName(), pos);
            return; // Skip removal
        }

        // For other blocks, use default removal behavior
        IPlacementHandler.super.handleRemoval(handler, world, pos, tileEntityData);
    }

    @Override
    public List<ItemStack> getRequiredItems(Level world, BlockPos pos, BlockState blockState,
            @Nullable CompoundTag tileEntityData, boolean complete) {
        BlockState existingState = world.getBlockState(pos);

        // If same block type exists, no items needed (we're preserving it)
        if (existingState.getBlock().getClass() == blockState.getBlock().getClass()) {
            return Collections.emptyList();
        }

        // Otherwise, require the normal item
        return Collections.singletonList(new ItemStack(blockState.getBlock().asItem()));
    }
}
