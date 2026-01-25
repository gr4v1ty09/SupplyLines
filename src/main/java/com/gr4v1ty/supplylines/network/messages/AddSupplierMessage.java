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
 * Message to add a supplier network to the Stock Keeper building.
 */
public class AddSupplierMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The UUID of the Create stock network. */
    private UUID networkId;

    /** The destination address for package requests. */
    private String requestAddress;

    /**
     * Empty constructor for deserialization.
     */
    public AddSupplierMessage() {
        super();
    }

    /**
     * Create a message to add a supplier.
     *
     * @param buildingView
     *            the building view.
     * @param networkId
     *            the UUID of the Create stock network.
     * @param requestAddress
     *            the destination address for package requests.
     */
    public AddSupplierMessage(final IBuildingView buildingView, final UUID networkId, final String requestAddress) {
        super(buildingView);
        this.networkId = networkId;
        this.requestAddress = requestAddress != null ? requestAddress : "";
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
        this.requestAddress = buf.readUtf();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeUtf(requestAddress);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final SuppliersModule module = building.getFirstModuleOccurance(SuppliersModule.class);
            if (module != null) {
                module.addSupplier(networkId, requestAddress);
                building.markDirty();
            }
        }
    }
}
