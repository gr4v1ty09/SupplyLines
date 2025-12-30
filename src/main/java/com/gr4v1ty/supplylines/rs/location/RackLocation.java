package com.gr4v1ty.supplylines.rs.location;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RackLocation implements ILocation {
    @NotNull
    private final ResourceKey<Level> dimension;
    @NotNull
    private final BlockPos pos;
    @Nullable
    private final Direction face;
    private final int slotIndex;

    public RackLocation(@NotNull ResourceKey<Level> dimension, @NotNull BlockPos pos, @Nullable Direction face,
            int slotIndex) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos");
        this.face = face;
        this.slotIndex = slotIndex;
    }

    @NotNull
    public BlockPos getInDimensionLocation() {
        return this.pos;
    }

    @NotNull
    public ResourceKey<Level> getDimension() {
        return this.dimension;
    }

    @Nullable
    public Direction getFace() {
        return this.face;
    }

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public boolean isReachableFromLocation(@NotNull ILocation other) {
        return this.dimension.equals((Object) other.getDimension());
    }

    public String toString() {
        return "RackLocation{dim=" + this.dimension.location() + ", pos=" + this.pos + ", face=" + this.face
                + ", slotIndex=" + this.slotIndex + "}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RackLocation)) {
            return false;
        }
        RackLocation that = (RackLocation) o;
        return this.slotIndex == that.slotIndex && this.dimension.equals(that.dimension)
                && this.pos.equals((Object) that.pos) && Objects.equals(this.face, that.face);
    }

    public int hashCode() {
        return Objects.hash(this.dimension, this.pos, this.face, this.slotIndex);
    }

    public static final class Spec {
        @NotNull
        public final ResourceKey<Level> dimension;
        @NotNull
        public final BlockPos pos;
        @Nullable
        public final Direction face;
        public final int slotIndex;

        public Spec(@NotNull ResourceKey<Level> dimension, @NotNull BlockPos pos, @Nullable Direction face,
                int slotIndex) {
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            this.pos = Objects.requireNonNull(pos, "pos");
            this.face = face;
            this.slotIndex = slotIndex;
        }
    }
}
