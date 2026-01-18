package com.gr4v1ty.supplylines.network.messages;

import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;

/**
 * Message to update a supplier's speculative ordering setting in the Stock
 * Keeper building.
 */
public class SetSupplierSpeculativeMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The UUID of the Create stock network. */
    private UUID networkId;

    /** Whether speculative ordering is allowed. */
    private boolean allowSpeculative;

    /**
     * Empty constructor for deserialization.
     */
    public SetSupplierSpeculativeMessage() {
        super();
    }

    /**
     * Create a message to set supplier speculative ordering.
     *
     * @param buildingView
     *            the building view.
     * @param networkId
     *            the UUID of the Create stock network.
     * @param allowSpeculative
     *            whether speculative ordering is allowed.
     */
    public SetSupplierSpeculativeMessage(final IBuildingView buildingView, final UUID networkId,
            final boolean allowSpeculative) {
        super(buildingView);
        this.networkId = networkId;
        this.allowSpeculative = allowSpeculative;
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
        this.allowSpeculative = buf.readBoolean();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeBoolean(allowSpeculative);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final SuppliersModule module = building.getFirstModuleOccurance(SuppliersModule.class);
            if (module != null) {
                module.setSupplierSpeculativeOrdering(networkId, allowSpeculative);
                building.markDirty();

                // Send warning to colony managers when enabling speculative ordering
                if (allowSpeculative) {
                    final SuppliersModule.SupplierEntry entry = module.getSuppliers().stream()
                            .filter(e -> e.getNetworkId().equals(networkId)).findFirst().orElse(null);
                    if (entry != null) {
                        String supplierLabel = entry.getLabel().isEmpty()
                                ? entry.getNetworkId().toString().substring(0, 8)
                                : entry.getLabel();
                        MessageUtils
                                .format("com.supplylines.gui.stockkeeper.suppliers.speculative.warning", supplierLabel)
                                .sendTo(colony.getImportantMessageEntityPlayers());
                    }
                }
            }
        }
    }
}
