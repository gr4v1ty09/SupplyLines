package com.gr4v1ty.supplylines.mixin;

import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {AbstractBuilding.class}, remap = false)
public abstract class AbstractBuildingMixin {
    @Shadow
    abstract Map<IToken<?>, Integer> getCitizensByRequest();

    @Inject(method = {"onRequestedRequestCancelled"}, at = {@At(value = "HEAD")}, cancellable = true, remap = false)
    private void supplylines$handleDeliveryChildCancellation(@NotNull IRequestManager manager,
            @NotNull IRequest<?> request, CallbackInfo ci) {
        if (!this.getCitizensByRequest().containsKey(request.getId())) {
            ci.cancel();
        }
    }
}
