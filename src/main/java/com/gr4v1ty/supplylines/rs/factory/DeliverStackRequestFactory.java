package com.gr4v1ty.supplylines.rs.factory;

import com.google.common.reflect.TypeToken;
import com.gr4v1ty.supplylines.rs.request.DeliverStackRequest;
import com.gr4v1ty.supplylines.rs.requestable.DeliverStack;
import com.minecolonies.api.colony.requestsystem.factory.IFactory;
import com.minecolonies.api.colony.requestsystem.factory.IFactoryController;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public final class DeliverStackRequestFactory implements IFactory<DeliverStack, DeliverStackRequest> {
    public static final short SERIALIZATION_ID = 31003;

    @NotNull
    public TypeToken<? extends DeliverStackRequest> getFactoryOutputType() {
        return TypeToken.of(DeliverStackRequest.class);
    }

    @NotNull
    public TypeToken<? extends DeliverStack> getFactoryInputType() {
        return TypeToken.of(DeliverStack.class);
    }

    @NotNull
    public DeliverStackRequest getNewInstance(@NotNull IFactoryController factoryController,
            @NotNull DeliverStack deliverStack, Object... context) throws IllegalArgumentException {
        if (context.length < 2) {
            throw new IllegalArgumentException("DeliverStackRequestFactory requires context: [IToken, IRequester]");
        }
        IToken<?> token = (IToken<?>) context[0];
        IRequester requester = (IRequester) context[1];
        return new DeliverStackRequest(requester, token, deliverStack);
    }

    @NotNull
    public CompoundTag serialize(@NotNull IFactoryController controller, @NotNull DeliverStackRequest request) {
        CompoundTag compound = new CompoundTag();
        compound.put("Token", (Tag) controller.serialize((Object) request.getId()));
        compound.put("Requester", (Tag) controller.serialize((Object) request.getRequester()));
        DeliverStackFactory dsFactory = new DeliverStackFactory();
        compound.put("Requested", dsFactory.serialize(controller, request.getRequest()));
        compound.putInt("State", request.getState().ordinal());
        return compound;
    }

    @NotNull
    public DeliverStackRequest deserialize(@NotNull IFactoryController controller, @NotNull CompoundTag nbt)
            throws Throwable {
        IToken<?> token = (IToken<?>) controller.deserialize(nbt.getCompound("Token"));
        IRequester requester = (IRequester) controller.deserialize(nbt.getCompound("Requester"));
        DeliverStackFactory dsFactory = new DeliverStackFactory();
        DeliverStack requested = dsFactory.deserialize(controller, nbt.getCompound("Requested"));
        RequestState state = RequestState.values()[nbt.getInt("State")];
        return new DeliverStackRequest(requester, token, state, requested);
    }

    public void serialize(@NotNull IFactoryController controller, @NotNull DeliverStackRequest request,
            @NotNull FriendlyByteBuf buffer) {
        controller.serialize(buffer, (Object) request.getId());
        controller.serialize(buffer, (Object) request.getRequester());
        DeliverStackFactory dsFactory = new DeliverStackFactory();
        dsFactory.serialize(controller, request.getRequest(), buffer);
        buffer.writeEnum(request.getState());
    }

    @NotNull
    public DeliverStackRequest deserialize(@NotNull IFactoryController controller, @NotNull FriendlyByteBuf buffer)
            throws Throwable {
        IToken<?> token = (IToken<?>) controller.deserialize(buffer);
        IRequester requester = (IRequester) controller.deserialize(buffer);
        DeliverStackFactory dsFactory = new DeliverStackFactory();
        DeliverStack requested = dsFactory.deserialize(controller, buffer);
        RequestState state = buffer.readEnum(RequestState.class);
        return new DeliverStackRequest(requester, token, state, requested);
    }

    public short getSerializationId() {
        return SERIALIZATION_ID;
    }
}
