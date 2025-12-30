package com.gr4v1ty.supplylines.rs.requestable;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public final class DeliverStack implements IRequestable {
    private static final Set<TypeToken<?>> TYPE_TOKENS = ImmutableSet.<TypeToken<?>>of(TypeToken.of(DeliverStack.class),
            new TypeToken<IRequestable>() {
            });
    @NotNull
    private final ILocation source;
    @NotNull
    private final ILocation dest;
    @NotNull
    private final Stack payload;
    private final int count;
    private final UUID reservationId;

    public DeliverStack(@NotNull ILocation source, @NotNull ILocation dest, @NotNull Stack payload, int count,
            UUID reservationId) {
        if (count <= 0) {
            throw new IllegalArgumentException("DeliverStack count must be > 0");
        }
        this.source = Objects.requireNonNull(source, "source");
        this.dest = Objects.requireNonNull(dest, "dest");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.count = count;
        this.reservationId = reservationId;
    }

    @NotNull
    public ILocation getSource() {
        return this.source;
    }

    @NotNull
    public ILocation getDest() {
        return this.dest;
    }

    @NotNull
    public Stack getPayload() {
        return this.payload;
    }

    public int getCount() {
        return this.count;
    }

    public UUID getReservationId() {
        return this.reservationId;
    }

    public Set<TypeToken<?>> getSuperClasses() {
        return TYPE_TOKENS;
    }

    public String toString() {
        return "DeliverStack{source=" + this.source + ", dest=" + this.dest + ", payload=" + this.payload + ", count="
                + this.count + ", reservationId=" + this.reservationId + "}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeliverStack)) {
            return false;
        }
        DeliverStack that = (DeliverStack) o;
        return this.count == that.count && Objects.equals(this.source, that.source)
                && Objects.equals(this.dest, that.dest) && Objects.equals(this.payload, that.payload)
                && Objects.equals(this.reservationId, that.reservationId);
    }

    public int hashCode() {
        return Objects.hash(this.source, this.dest, this.payload, this.count, this.reservationId);
    }
}
