package com.gr4v1ty.supplylines.rs.verifier;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface FoodDeliveryVerifier {
    public boolean isDelivered(@NotNull IRequestManager var1, @NotNull ILocation var2,
            @NotNull IRequest<? extends Food> var3);
}
