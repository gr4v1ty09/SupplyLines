package com.gr4v1ty.supplylines.rs.factory;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.requestable.DeliverStack;
import com.minecolonies.api.colony.requestsystem.factory.FactoryVoidInput;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class DeliverStackFactory implements IFactory<FactoryVoidInput, DeliverStack> {
    private static final String TAG_SOURCE = "source";
    private static final String TAG_DEST = "dest";
    private static final String TAG_STACK = "payload";
    private static final String TAG_COUNT = "count";
    private static final String TAG_RESV = "reservationId";
    public static final short SERIALIZATION_ID = 31001;

    @NotNull
    public CompoundTag serialize(@NotNull IFactoryController controller, @NotNull DeliverStack output) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_SOURCE, (Tag) controller.serialize((Object) output.getSource()));
        tag.put(TAG_DEST, (Tag) controller.serialize((Object) output.getDest()));
        tag.put(TAG_STACK, (Tag) output.getPayload().getStack().save(new CompoundTag()));
        tag.putInt(TAG_COUNT, output.getCount());
        UUID id = output.getReservationId();
        if (id != null) {
            tag.putString(TAG_RESV, id.toString());
        }
        return tag;
    }

    @NotNull
    public DeliverStack deserialize(@NotNull IFactoryController controller, @NotNull CompoundTag compound)
            throws Throwable {
        ILocation source = (ILocation) controller.deserialize(compound.getCompound(TAG_SOURCE));
        ILocation dest = (ILocation) controller.deserialize(compound.getCompound(TAG_DEST));
        ItemStack itemStack = ItemStack.of((CompoundTag) compound.getCompound(TAG_STACK));
        Stack payload = new Stack(itemStack);
        int count = compound.getInt(TAG_COUNT);
        UUID reservation = compound.contains(TAG_RESV) ? UUID.fromString(compound.getString(TAG_RESV)) : null;
        return new DeliverStack(source, dest, payload, count, reservation);
    }

    public void serialize(@NotNull IFactoryController controller, @NotNull DeliverStack output,
            FriendlyByteBuf buffer) {
        controller.serialize(buffer, (Object) output.getSource());
        controller.serialize(buffer, (Object) output.getDest());
        buffer.writeItem(output.getPayload().getStack());
        buffer.writeVarInt(output.getCount());
        UUID id = output.getReservationId();
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeUUID(id);
        }
    }

    @NotNull
    public DeliverStack deserialize(@NotNull IFactoryController controller, @NotNull FriendlyByteBuf buffer)
            throws Throwable {
        ILocation source = (ILocation) controller.deserialize(buffer);
        ILocation dest = (ILocation) controller.deserialize(buffer);
        ItemStack itemStack = buffer.readItem();
        Stack payload = new Stack(itemStack);
        int count = buffer.readVarInt();
        boolean hasId = buffer.readBoolean();
        UUID reservation = hasId ? buffer.readUUID() : null;
        return new DeliverStack(source, dest, payload, count, reservation);
    }

    @NotNull
    public TypeToken<? extends DeliverStack> getFactoryOutputType() {
        return TypeToken.of(DeliverStack.class);
    }

    @NotNull
    public TypeToken<? extends FactoryVoidInput> getFactoryInputType() {
        return TypeToken.of(FactoryVoidInput.class);
    }

    @NotNull
    public DeliverStack getNewInstance(@NotNull IFactoryController controller,
            @NotNull FactoryVoidInput factoryVoidInput, Object... objects) throws IllegalArgumentException {
        if (objects.length < 4) {
            throw new IllegalArgumentException(
                    "DeliverStackFactory.getNewInstance requires at least 4 arguments: source, dest, payload, count");
        }
        Object o0 = objects[0];
        Object o1 = objects[1];
        Object o2 = objects[2];
        Object o3 = objects[3];
        if (!(o0 instanceof ILocation && o1 instanceof ILocation && o2 instanceof Stack && o3 instanceof Integer)) {
            throw new IllegalArgumentException(
                    "DeliverStackFactory.getNewInstance arg types must be: ILocation, ILocation, Stack, Integer[, UUID]");
        }
        ILocation source = (ILocation) o0;
        ILocation dest = (ILocation) o1;
        Stack payload = (Stack) o2;
        int count = (Integer) o3;
        UUID reservationId = objects.length >= 5 && objects[4] != null ? (UUID) objects[4] : null;
        return new DeliverStack(source, dest, payload, count, reservationId);
    }

    public short getSerializationId() {
        return 31001;
    }
}
