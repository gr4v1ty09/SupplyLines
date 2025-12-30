package com.gr4v1ty.supplylines.colony.model;

import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.world.item.ItemStack;

public class StagingRequest {
    public ItemStack item = ItemStack.EMPTY;
    public int quantity = 0;
    public long requestedAtTick = 0L;
    public boolean broadcasted = false;
    public State state = State.QUEUED;
    public IToken<?> parentRequestId = null;
    public IToken<?> bundleLeaderId = null;

    public static enum State {
        QUEUED, BROADCASTED, COMPLETED, CANCELLED;

    }
}
