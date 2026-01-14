package com.gr4v1ty.supplylines.network.messages;

import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Message to add or update a restock policy in the Stock Keeper building.
 */
public class AddRestockPolicyMessage extends AbstractBuildingServerMessage<IBuilding> {
    /** The item stack defining the policy. */
    private ItemStack itemStack;

    /** The target quantity to maintain. */
    private int quantity;

    /**
     * Empty constructor for deserialization.
     */
    public AddRestockPolicyMessage() {
        super();
    }

    /**
     * Create a message to add a restock policy.
     *
     * @param buildingView
     *            the building view.
     * @param itemStack
     *            the item to add.
     * @param quantity
     *            the target quantity.
     */
    public AddRestockPolicyMessage(final IBuildingView buildingView, final ItemStack itemStack, final int quantity) {
        super(buildingView);
        this.itemStack = itemStack;
        this.quantity = quantity;
    }

    @Override
    public void fromBytesOverride(final FriendlyByteBuf buf) {
        this.itemStack = buf.readItem();
        this.quantity = buf.readInt();
    }

    @Override
    public void toBytesOverride(final FriendlyByteBuf buf) {
        buf.writeItem(itemStack);
        buf.writeInt(quantity);
    }

    @Override
    protected void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer, final IColony colony,
            final IBuilding building) {
        if (isLogicalServer) {
            final RestockPolicyModule module = building.getFirstModuleOccurance(RestockPolicyModule.class);
            if (module != null) {
                module.addOrUpdatePolicy(new ItemStorage(itemStack), quantity);
                building.markDirty();
            }
        }
    }
}
