package com.gr4v1ty.supplylines.rs.factory;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.resolver.ToolResolver;
import com.minecolonies.api.colony.requestsystem.factory.FactoryVoidInput;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public final class ToolResolverFactory implements IFactory<FactoryVoidInput, ToolResolver> {
    private static final String TAG_ID = "id";
    private static final String TAG_LOC = "loc";
    public static final short SERIALIZATION_ID = 31026;

    @NotNull
    public TypeToken<? extends ToolResolver> getFactoryOutputType() {
        return TypeToken.of(ToolResolver.class);
    }

    @NotNull
    public TypeToken<? extends FactoryVoidInput> getFactoryInputType() {
        return TypeToken.of(FactoryVoidInput.class);
    }

    @NotNull
    public ToolResolver getNewInstance(@NotNull IFactoryController c, @NotNull FactoryVoidInput in, Object... o) {
        throw new IllegalArgumentException("Use deserialize to create ToolResolver.");
    }

    @NotNull
    public CompoundTag serialize(@NotNull IFactoryController c, @NotNull ToolResolver out) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, (UUID) out.getId().getIdentifier());
        tag.put(TAG_LOC, (Tag) c.serialize((Object) out.getLocation()));
        return tag;
    }

    @NotNull
    public ToolResolver deserialize(@NotNull IFactoryController c, @NotNull CompoundTag nbt) throws Throwable {
        UUID id = nbt.getUUID(TAG_ID);
        ILocation loc = (ILocation) c.deserialize(nbt.getCompound(TAG_LOC));
        return new ToolResolver(id, loc);
    }

    public void serialize(@NotNull IFactoryController c, @NotNull ToolResolver out, FriendlyByteBuf buf) {
        buf.writeUUID((UUID) out.getId().getIdentifier());
        c.serialize(buf, (Object) out.getLocation());
    }

    @NotNull
    public ToolResolver deserialize(@NotNull IFactoryController c, FriendlyByteBuf buf) throws Throwable {
        UUID id = buf.readUUID();
        ILocation loc = (ILocation) c.deserialize(buf);
        return new ToolResolver(id, loc);
    }

    public short getSerializationId() {
        return SERIALIZATION_ID;
    }
}
