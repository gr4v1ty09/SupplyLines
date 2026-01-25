package com.gr4v1ty.supplylines.colony.items;

import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.colony.buildings.moduleviews.SuppliersModuleView;
import com.gr4v1ty.supplylines.compat.create.CreateNetworkHelper;
import com.gr4v1ty.supplylines.network.ModNetwork;
import com.gr4v1ty.supplylines.network.messages.AddSupplierMessage;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_POS;

/**
 * Stock Keeper Scepter item for linking Create logistics networks to the Stock
 * Keeper building. Players receive this item from the Stock Keeper UI and
 * right-click on Stock Tickers, Stock Links, or Factory Gauges to link their
 * networks.
 */
public class ItemScepterStockKeeper extends Item {
    /**
     * Constructor.
     *
     * @param properties
     *            the item properties.
     */
    public ItemScepterStockKeeper(final Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @NotNull
    @Override
    public InteractionResult useOn(final UseOnContext ctx) {
        final Level level = ctx.getLevel();
        final Player player = ctx.getPlayer();
        final BlockPos clickedPos = ctx.getClickedPos();

        if (player == null) {
            return InteractionResult.FAIL;
        }

        final ItemStack scepter = player.getItemInHand(ctx.getHand());
        final CompoundTag compound = scepter.getOrCreateTag();

        if (!compound.contains(TAG_ID) || !compound.contains(TAG_POS)) {
            MessageUtils.format("com.supplylines.scepter.invalidconfig").sendTo(player);
            return InteractionResult.FAIL;
        }

        // Extract network UUID from the clicked block
        final UUID networkId = CreateNetworkHelper.getNetworkIdFromBlock(level, clickedPos);

        if (networkId == null) {
            MessageUtils.format("com.supplylines.scepter.invalidblock").sendTo(player);
            return InteractionResult.FAIL;
        }

        // Handle on client side - send message to server
        if (level.isClientSide) {
            final int colonyId = compound.getInt(TAG_ID);
            final BlockPos buildingPos = BlockPosUtil.read(compound, TAG_POS);

            final IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, level.dimension());
            if (colony == null) {
                MessageUtils.format("com.supplylines.scepter.nocolony").sendTo(player);
                return InteractionResult.FAIL;
            }

            final IBuildingView buildingView = colony.getBuilding(buildingPos);
            if (buildingView == null) {
                MessageUtils.format("com.supplylines.scepter.nobuilding").sendTo(player);
                return InteractionResult.FAIL;
            }

            // Check if already linked via the module view
            final SuppliersModuleView moduleView = buildingView.getModuleViewByType(SuppliersModuleView.class);
            if (moduleView != null) {
                for (SuppliersModule.SupplierEntry entry : moduleView.getSuppliers()) {
                    if (entry.getNetworkId().equals(networkId)) {
                        MessageUtils.format("com.supplylines.scepter.alreadylinked").sendTo(player);
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            // Send the message to link the network
            ModNetwork.sendToServer(new AddSupplierMessage(buildingView, networkId, ""));
            MessageUtils.format("com.supplylines.scepter.networked", CreateNetworkHelper.formatNetworkId(networkId))
                    .sendTo(player);

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * Create a scepter item configured for a specific building.
     *
     * @param colonyId
     *            the colony ID.
     * @param buildingPos
     *            the building position.
     * @return the configured scepter item stack.
     */
    public static ItemStack createConfiguredScepter(final int colonyId, final BlockPos buildingPos) {
        final ItemStack stack = new ItemStack(com.gr4v1ty.supplylines.registry.ModItems.SCEPTER_STOCKKEEPER.get());
        final CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_ID, colonyId);
        BlockPosUtil.write(tag, TAG_POS, buildingPos);
        return stack;
    }
}
