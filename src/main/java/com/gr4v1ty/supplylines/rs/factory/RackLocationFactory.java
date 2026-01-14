package com.gr4v1ty.supplylines.rs.factory;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.location.RackLocation;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocationFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public final class RackLocationFactory implements ILocationFactory<RackLocation.Spec, RackLocation> {
    private static final String TAG_DIM = "dim";
    private static final String TAG_POS = "pos";
    private static final String TAG_FACE = "face";
    private static final String TAG_SLOT = "slot";
    public static final short SERIALIZATION_ID = 31012;

    @NotNull
    public TypeToken<? extends RackLocation> getFactoryOutputType() {
        return TypeToken.of(RackLocation.class);
    }

    @NotNull
    public TypeToken<? extends RackLocation.Spec> getFactoryInputType() {
        return TypeToken.of(RackLocation.Spec.class);
    }

    @NotNull
    public RackLocation getNewInstance(@NotNull IFactoryController controller, @NotNull RackLocation.Spec input) {
        return new RackLocation(input.dimension, input.pos, input.face, input.slotIndex);
    }

    @NotNull
    public CompoundTag serialize(@NotNull IFactoryController controller, @NotNull RackLocation output) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIM, output.getDimension().location().toString());
        CompoundTag pos = new CompoundTag();
        pos.putInt("x", output.getInDimensionLocation().getX());
        pos.putInt("y", output.getInDimensionLocation().getY());
        pos.putInt("z", output.getInDimensionLocation().getZ());
        tag.put(TAG_POS, pos);
        if (output.getFace() != null) {
            tag.putString(TAG_FACE, output.getFace().getName());
        }
        tag.putInt(TAG_SLOT, output.getSlotIndex());
        return tag;
    }

    @NotNull
    @SuppressWarnings("removal") // ResourceLocation constructor deprecated in Forge 47.x, will migrate in 1.21
    public RackLocation deserialize(@NotNull IFactoryController controller, @NotNull CompoundTag compound) {
        ResourceLocation dimRL = new ResourceLocation(compound.getString(TAG_DIM));
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimRL);
        CompoundTag p = compound.getCompound(TAG_POS);
        BlockPos pos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
        Direction face = compound.contains(TAG_FACE) ? Direction.byName(compound.getString(TAG_FACE)) : null;
        int slotIndex = compound.contains(TAG_SLOT) ? compound.getInt(TAG_SLOT) : -1;
        return new RackLocation(dim, pos, face, slotIndex);
    }

    public void serialize(@NotNull IFactoryController controller, @NotNull RackLocation output,
            @NotNull FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(output.getDimension().location());
        BlockPos pos = output.getInDimensionLocation();
        buffer.writeInt(pos.getX());
        buffer.writeInt(pos.getY());
        buffer.writeInt(pos.getZ());
        Direction face = output.getFace();
        buffer.writeBoolean(face != null);
        if (face != null) {
            buffer.writeVarInt(face.get3DDataValue());
        }
        buffer.writeVarInt(output.getSlotIndex());
    }

    @NotNull
    public RackLocation deserialize(@NotNull IFactoryController controller, @NotNull FriendlyByteBuf buffer) {
        ResourceLocation dimRL = buffer.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimRL);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        BlockPos pos = new BlockPos(x, y, z);
        boolean hasFace = buffer.readBoolean();
        Direction face = hasFace ? Direction.from3DDataValue(buffer.readVarInt()) : null;
        int slotIndex = buffer.readVarInt();
        return new RackLocation(dim, pos, face, slotIndex);
    }

    public short getSerializationId() {
        return SERIALIZATION_ID;
    }
}
