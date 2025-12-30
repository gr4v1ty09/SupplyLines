package com.gr4v1ty.supplylines.rs.util;

import com.google.common.collect.ImmutableList;
import com.gr4v1ty.supplylines.rs.requestable.DeliverStack;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeliveryPlanning {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryPlanning.class);

    private DeliveryPlanning() {
    }

    @NotNull
    public static List<IToken<?>> emitChildren(@NotNull IRequestManager manager,
            @NotNull IRequest<? extends Stack> parent, @NotNull ILocation dest, @NotNull List<Pick> picks) {
        ArrayList<IToken<?>> out = new ArrayList<>(picks.size());
        for (Pick p : picks) {
            try {
                DeliverStack leg = new DeliverStack(p.source, dest, p.payload, p.count, p.reservationId);
                IToken<?> childToken = manager.createRequest(parent.getRequester(), (IRequestable) leg);
                out.add(childToken);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to create DeliverStack child request: {}", (Object) e.getMessage());
            }
        }
        return ImmutableList.copyOf(out);
    }

    public static final class Pick {
        @NotNull
        public final ILocation source;
        @NotNull
        public final Stack payload;
        public final int count;
        public final UUID reservationId;

        public Pick(@NotNull ILocation source, @NotNull Stack payload, int count, UUID reservationId) {
            this.source = source;
            this.payload = payload;
            this.count = count;
            this.reservationId = reservationId;
        }
    }
}
