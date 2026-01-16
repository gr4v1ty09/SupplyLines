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
 * Message to change a supplier's priority in the Stock Keeper building.
 */
public class SetSupplierPriorityMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The UUID of the Create stock network. */
    private UUID networkId;

    /** The new priority index. */
    private int newPriority;

    /**
     * Empty constructor for deserialization.
     */
    public SetSupplierPriorityMessage() {
        super();
    }

    /**
     * Create a message to set supplier priority.
     *
     * @param buildingView
     *            the building view.
     * @param networkId
     *            the UUID of the Create stock network.
     * @param newPriority
     *            the new priority index.
     */
    public SetSupplierPriorityMessage(final IBuildingView buildingView, final UUID networkId, final int newPriority) {
        super(buildingView);
        this.networkId = networkId;
        this.newPriority = newPriority;
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
        this.newPriority = buf.readInt();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeInt(newPriority);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final SuppliersModule module = building.getFirstModuleOccurance(SuppliersModule.class);
            if (module != null) {
                module.setSupplierPriority(networkId, newPriority);
                building.markDirty();
            }
        }
    }
}
