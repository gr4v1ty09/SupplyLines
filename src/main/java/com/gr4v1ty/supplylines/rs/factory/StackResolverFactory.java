package com.gr4v1ty.supplylines.rs.factory;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.resolver.StackResolver;
import com.minecolonies.api.colony.requestsystem.factory.FactoryVoidInput;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public final class StackResolverFactory implements IFactory<FactoryVoidInput, StackResolver> {
    private static final String TAG_ID = "id";
    private static final String TAG_LOC = "loc";
    public static final short SERIALIZATION_ID = 31025;

    @NotNull
    public TypeToken<? extends StackResolver> getFactoryOutputType() {
        return TypeToken.of(StackResolver.class);
    }

    @NotNull
    public TypeToken<? extends FactoryVoidInput> getFactoryInputType() {
        return TypeToken.of(FactoryVoidInput.class);
    }

    @NotNull
    public StackResolver getNewInstance(@NotNull IFactoryController c, @NotNull FactoryVoidInput in, Object... o) {
        throw new IllegalArgumentException("Use deserialize to create StackResolver.");
    }

    @NotNull
    public CompoundTag serialize(@NotNull IFactoryController c, @NotNull StackResolver out) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, (UUID) out.getId().getIdentifier());
        tag.put(TAG_LOC, (Tag) c.serialize((Object) out.getLocation()));
        return tag;
    }

    @NotNull
    public StackResolver deserialize(@NotNull IFactoryController c, @NotNull CompoundTag nbt) throws Throwable {
        UUID id = nbt.getUUID(TAG_ID);
        ILocation loc = (ILocation) c.deserialize(nbt.getCompound(TAG_LOC));
        return new StackResolver(id, loc);
    }

    public void serialize(@NotNull IFactoryController c, @NotNull StackResolver out, FriendlyByteBuf buf) {
        buf.writeUUID((UUID) out.getId().getIdentifier());
        c.serialize(buf, (Object) out.getLocation());
    }

    @NotNull
    public StackResolver deserialize(@NotNull IFactoryController c, FriendlyByteBuf buf) throws Throwable {
        UUID id = buf.readUUID();
        ILocation loc = (ILocation) c.deserialize(buf);
        return new StackResolver(id, loc);
    }

    public short getSerializationId() {
        return 31025;
    }
}
