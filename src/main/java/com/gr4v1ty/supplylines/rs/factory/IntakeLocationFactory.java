package com.gr4v1ty.supplylines.rs.factory;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.location.IntakeLocation;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocationFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public final class IntakeLocationFactory implements ILocationFactory<IntakeLocation.Spec, IntakeLocation> {
    private static final String TAG_DIM = "dim";
    private static final String TAG_POS = "pos";
    public static final short SERIALIZATION_ID = 31011;

    @NotNull
    public TypeToken<? extends IntakeLocation> getFactoryOutputType() {
        return TypeToken.of(IntakeLocation.class);
    }

    @NotNull
    public TypeToken<? extends IntakeLocation.Spec> getFactoryInputType() {
        return TypeToken.of(IntakeLocation.Spec.class);
    }

    @NotNull
    public IntakeLocation getNewInstance(@NotNull IFactoryController controller, @NotNull IntakeLocation.Spec input) {
        return new IntakeLocation(input.dimension, input.pos);
    }

    @NotNull
    public CompoundTag serialize(@NotNull IFactoryController controller, @NotNull IntakeLocation output) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIM, output.getDimension().location().toString());
        CompoundTag pos = new CompoundTag();
        pos.putInt("x", output.getInDimensionLocation().getX());
        pos.putInt("y", output.getInDimensionLocation().getY());
        pos.putInt("z", output.getInDimensionLocation().getZ());
        tag.put(TAG_POS, pos);
        return tag;
    }

    @NotNull
    @SuppressWarnings("removal") // ResourceLocation constructor deprecated in Forge 47.x, will migrate in 1.21
    public IntakeLocation deserialize(@NotNull IFactoryController controller, @NotNull CompoundTag compound) {
        ResourceLocation dimRL = new ResourceLocation(compound.getString(TAG_DIM));
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimRL);
        CompoundTag p = compound.getCompound(TAG_POS);
        BlockPos pos = new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z"));
        return new IntakeLocation(dim, pos);
    }

    public void serialize(@NotNull IFactoryController controller, @NotNull IntakeLocation output,
            @NotNull FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(output.getDimension().location());
        BlockPos pos = output.getInDimensionLocation();
        buffer.writeInt(pos.getX());
        buffer.writeInt(pos.getY());
        buffer.writeInt(pos.getZ());
    }

    @NotNull
    public IntakeLocation deserialize(@NotNull IFactoryController controller, @NotNull FriendlyByteBuf buffer) {
        ResourceLocation dimRL = buffer.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimRL);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        return new IntakeLocation(dim, new BlockPos(x, y, z));
    }

    public short getSerializationId() {
        return SERIALIZATION_ID;
    }
}
