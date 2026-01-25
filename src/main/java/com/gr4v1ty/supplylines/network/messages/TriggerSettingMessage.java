package com.gr4v1ty.supplylines.network.messages;

import com.gr4v1ty.supplylines.colony.buildings.modules.DeliverySettingsModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.network.IMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Message to change a Stock Keeper building setting.
 */
public class TriggerSettingMessage implements IMessage {

    /** The colony ID. */
    private int colonyId;

    /** The building position. */
    private BlockPos buildingPos;

    /** The setting key. */
    private String settingKey;

    /** The new value (-1 = use global, 0/1 for booleans, actual value for ints). */
    private int value;

    /**
     * Empty constructor for deserialization.
     */
    public TriggerSettingMessage() {
    }

    /**
     * Create a message to change a setting.
     *
     * @param colonyId
     *            the colony ID.
     * @param buildingPos
     *            the building position.
     * @param settingKey
     *            the setting key.
     * @param value
     *            the new value.
     */
    public TriggerSettingMessage(final int colonyId, final BlockPos buildingPos, final String settingKey,
            final int value) {
        this.colonyId = colonyId;
        this.buildingPos = buildingPos;
        this.settingKey = settingKey;
        this.value = value;
    }

    @Override
    public void fromBytes(final FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        this.buildingPos = buf.readBlockPos();
        this.settingKey = buf.readUtf(64);
        this.value = buf.readInt();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeBlockPos(buildingPos);
        buf.writeUtf(settingKey, 64);
        buf.writeInt(value);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onExecute(final NetworkEvent.Context ctx, final boolean isLogicalServer) {
        if (!isLogicalServer) {
            return;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId,
                ctx.getSender().level().dimension());
        if (colony == null) {
            return;
        }

        final IBuilding building = colony.getBuildingManager().getBuilding(buildingPos);
        if (building == null) {
            return;
        }

        final DeliverySettingsModule module = building.getFirstModuleOccurance(DeliverySettingsModule.class);
        if (module != null) {
            module.setSetting(settingKey, value);
            building.markDirty();
        }
    }
}
