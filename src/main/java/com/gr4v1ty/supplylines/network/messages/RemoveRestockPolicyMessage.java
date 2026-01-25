package com.gr4v1ty.supplylines.network.messages;

import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Message to remove a restock policy from the Stock Keeper building.
 */
public class RemoveRestockPolicyMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The item stack defining the policy to remove. */
    private ItemStack itemStack;

    /**
     * Empty constructor for deserialization.
     */
    public RemoveRestockPolicyMessage() {
        super();
    }

    /**
     * Create a message to remove a restock policy.
     *
     * @param buildingView
     *            the building view.
     * @param itemStack
     *            the item to remove.
     */
    public RemoveRestockPolicyMessage(final IBuildingView buildingView, final ItemStack itemStack) {
        super(buildingView);
        this.itemStack = itemStack;
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.itemStack = buf.readItem();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeItem(itemStack);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final RestockPolicyModule module = building.getFirstModuleOccurance(RestockPolicyModule.class);
            if (module != null) {
                module.removePolicy(itemStack);
                building.markDirty();
            }
        }
    }
}
