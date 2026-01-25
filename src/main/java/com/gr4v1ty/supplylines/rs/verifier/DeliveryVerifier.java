package com.gr4v1ty.supplylines.rs.verifier;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import org.jetbrains.annotations.NotNull;

/**
 * Generic delivery verifier interface that replaces the 6 type-specific
 * verifier interfaces.
 *
 * @param <T>
 *            The requestable type this verifier handles
 */
@FunctionalInterface
public interface DeliveryVerifier<T extends IRequestable> {
    boolean isDelivered(@NotNull IRequestManager manager, @NotNull ILocation dest,
            @NotNull IRequest<? extends T> request);
}
