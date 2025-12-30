package com.gr4v1ty.supplylines.compat.structurize;

import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CreateVaultPlacementHandler implements IPlacementHandler {
    public boolean canHandle(Level world, BlockPos pos, BlockState blockState) {
        return blockState.getBlock() instanceof ItemVaultBlock;
    }

    public List<ItemStack> getRequiredItems(Level world, BlockPos pos, BlockState blockState,
            @Nullable CompoundTag tileEntityData, boolean complete) {
        Item item = blockState.getBlock().asItem();
        if (item == Items.AIR) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ItemStack((ItemLike) item, 1));
    }

    public IPlacementHandler.ActionProcessingResult handle(Level world, BlockPos pos, BlockState blockState,
            @Nullable CompoundTag tileEntityData, boolean complete, BlockPos centerPos) {
        return IPlacementHandler.super.handle(world, pos, blockState, tileEntityData, complete, centerPos);
    }
}
