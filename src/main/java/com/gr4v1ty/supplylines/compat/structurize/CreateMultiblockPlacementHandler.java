package com.gr4v1ty.supplylines.compat.structurize;

import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.util.PlacementSettings;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.trains.display.FlapDisplayBlock;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import static com.ldtteam.structurize.api.util.constant.Constants.UPDATE_FLAG;

/**
 * Placement handler for Create's multiblock structures.
 *
 * Create's multiblocks (vaults, belts, display boards, fluid tanks) can cause
 * crashes during Structurize's preview rendering because they try to access
 * block entity data that doesn't exist yet. This handler intercepts placement
 * and handles it safely.
 *
 * Each block type handles its own multiblock connectivity automatically via
 * onPlace() or tick() methods after placement.
 */
public class CreateMultiblockPlacementHandler implements IPlacementHandler {

    @Override
    public boolean canHandle(Level world, BlockPos pos, BlockState blockState) {
        Block block = blockState.getBlock();
        return block instanceof ItemVaultBlock || block instanceof BeltBlock || block instanceof FlapDisplayBlock
                || block instanceof FluidTankBlock || block instanceof GirderBlock
                || block instanceof GirderEncasedShaftBlock;
    }

    @Override
    public List<ItemStack> getRequiredItems(Level world, BlockPos pos, BlockState blockState,
            @Nullable CompoundTag tileEntityData, boolean complete) {
        Item item = blockState.getBlock().asItem();
        if (item == Items.AIR) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ItemStack(item, 1));
    }

    @Override
    @SuppressWarnings("removal") // PlacementSettings deprecated in Structurize, required by interface
    public ActionProcessingResult handle(Level world, BlockPos pos, BlockState blockState,
            @Nullable CompoundTag tileEntityData, boolean complete, BlockPos centerPos, PlacementSettings settings) {
        // If the block is already the same, nothing to do
        if (world.getBlockState(pos).equals(blockState)) {
            return ActionProcessingResult.PASS;
        }

        // Place the block in the world
        if (!world.setBlock(pos, blockState, UPDATE_FLAG)) {
            return ActionProcessingResult.DENY;
        }

        // For girders, recalculate state based on actual neighbors to fix visual
        // connections.
        // The blueprint saves exact state but neighbors may differ at the target
        // location.
        if (blockState.getBlock() instanceof GirderBlock || blockState.getBlock() instanceof GirderEncasedShaftBlock) {
            BlockState updatedState = Block.updateFromNeighbourShapes(blockState, world, pos);
            if (!updatedState.equals(blockState)) {
                world.setBlock(pos, updatedState, UPDATE_FLAG);
            }
        }

        // Handle tile entity data if present
        // Create's vault will automatically form the multiblock via onPlace() ->
        // updateConnectivity()
        if (tileEntityData != null) {
            PlacementHandlers.handleTileEntityPlacement(tileEntityData, world, pos, settings);
        }

        return ActionProcessingResult.SUCCESS;
    }
}
