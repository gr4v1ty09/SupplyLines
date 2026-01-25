package com.gr4v1ty.supplylines.network.messages;

import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * Message to remove a supplier network from the Stock Keeper building.
 */
public class RemoveSupplierMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The UUID of the Create stock network to remove. */
    private UUID networkId;

    /**
     * Empty constructor for deserialization.
     */
    public RemoveSupplierMessage() {
        super();
    }

    /**
     * Create a message to remove a supplier.
     *
     * @param buildingView
     *            the building view.
     * @param networkId
     *            the UUID of the Create stock network.
     */
    public RemoveSupplierMessage(final IBuildingView buildingView, final UUID networkId) {
        super(buildingView);
        this.networkId = networkId;
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final SuppliersModule module = building.getFirstModuleOccurance(SuppliersModule.class);
            if (module != null) {
                module.removeSupplier(networkId);
                building.markDirty();
            }
        }
    }
}
