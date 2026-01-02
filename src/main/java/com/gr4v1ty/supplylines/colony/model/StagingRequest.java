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

    /**
     * Creates a new StagingRequest in QUEUED state.
     *
     * @param item
     *            the item to stage (will be copied)
     * @param quantity
     *            the quantity to request
     * @param gameTick
     *            the current game tick
     * @param parentRequestId
     *            the parent request token
     * @return a new StagingRequest
     */
    public static StagingRequest create(ItemStack item, int quantity, long gameTick, IToken<?> parentRequestId) {
        StagingRequest staging = new StagingRequest();
        staging.item = item.copy();
        staging.quantity = quantity;
        staging.requestedAtTick = gameTick;
        staging.broadcasted = false;
        staging.state = State.QUEUED;
        staging.parentRequestId = parentRequestId;
        return staging;
    }

    public static enum State {
        QUEUED, BROADCASTED, COMPLETED, CANCELLED;
    }
}
