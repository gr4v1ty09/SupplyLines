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
 * Message to update a supplier's label in the Stock Keeper building.
 */
public class SetSupplierLabelMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The UUID of the Create stock network. */
    private UUID networkId;

    /** The new label. */
    private String label;

    /**
     * Empty constructor for deserialization.
     */
    public SetSupplierLabelMessage() {
        super();
    }

    /**
     * Create a message to set supplier label.
     *
     * @param buildingView
     *            the building view.
     * @param networkId
     *            the UUID of the Create stock network.
     * @param label
     *            the new label.
     */
    public SetSupplierLabelMessage(final IBuildingView buildingView, final UUID networkId, final String label) {
        super(buildingView);
        this.networkId = networkId;
        this.label = label != null ? label : "";
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
        this.label = buf.readUtf();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeUtf(label);
    }

    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final SuppliersModule module = building.getFirstModuleOccurance(SuppliersModule.class);
            if (module != null) {
                module.setSupplierLabel(networkId, label);
                building.markDirty();
            }
        }
    }
}
