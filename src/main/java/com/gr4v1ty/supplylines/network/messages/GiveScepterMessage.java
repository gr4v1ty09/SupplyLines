package com.gr4v1ty.supplylines.network.messages;

import com.gr4v1ty.supplylines.colony.items.ItemScepterStockKeeper;
import com.gr4v1ty.supplylines.registry.ModItems;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

/**
 * Message to give the Stock Keeper scepter to a player. Sent from client when
 * player clicks "Link Stock Network" button in the UI.
 */
public class GiveScepterMessage extends AbstractBuildingServerMessage<IBuilding> {

    /**
     * Empty constructor for deserialization.
     */
    public GiveScepterMessage() {
        super();
    }

    /**
     * Create a message to give the scepter.
     *
     * @param buildingView
     *            the building view.
     */
    public GiveScepterMessage(final IBuildingView buildingView) {
        super(buildingView);
    }

    @Override
    protected void toBytesOverride(final FriendlyByteBuf buf) {
        // No additional data needed - building info is handled by parent
    }

    @Override
    protected void fromBytesOverride(final FriendlyByteBuf buf) {
        // No additional data needed
    }

    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (!isLogicalServer) {
            return;
        }

        final Player player = ctx.getSender();
        if (player == null) {
            return;
        }

        // Create the configured scepter and add to player's hotbar
        InventoryUtils.getOrCreateItemAndPutToHotbarAndSelectOrDrop(
                ModItems.SCEPTER_STOCKKEEPER.get(), player,
                () -> ItemScepterStockKeeper.createConfiguredScepter(colony.getID(), building.getID()), true);

        player.getInventory().setChanged();
    }
}
