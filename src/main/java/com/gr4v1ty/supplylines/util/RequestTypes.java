package com.gr4v1ty.supplylines.util;

import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.RequestTag;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;

/**
 * Utility class for checking request types that SupplyLines can handle.
 * Centralizes the instanceof checks used across multiple classes.
 */
public final class RequestTypes {
    private RequestTypes() {
    }

    /**
     * Checks if the given requestable is a type that SupplyLines resolvers can
     * handle. Currently supports: Stack, Tool, RequestTag, StackList, Food, and
     * Burnable requests.
     *
     * @param requestable
     *            The requestable to check
     * @return true if SupplyLines can handle this request type
     */
    public static boolean isSupplyLinesType(IRequestable requestable) {
        return requestable instanceof Stack || requestable instanceof Tool || requestable instanceof RequestTag
                || requestable instanceof StackList || requestable instanceof Food || requestable instanceof Burnable;
    }
}
