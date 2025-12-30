package com.gr4v1ty.supplylines.rs.location;

import com.minecolonies.api.colony.requestsystem.location.ILocation;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public final class IntakeLocation implements ILocation {
    @NotNull
    private final ResourceKey<Level> dimension;
    @NotNull
    private final BlockPos pos;

    public IntakeLocation(@NotNull ResourceKey<Level> dimension, @NotNull BlockPos pos) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    @NotNull
    public BlockPos getInDimensionLocation() {
        return this.pos;
    }

    @NotNull
    public ResourceKey<Level> getDimension() {
        return this.dimension;
    }

    public boolean isReachableFromLocation(@NotNull ILocation other) {
        return this.dimension.equals((Object) other.getDimension());
    }

    public String toString() {
        return "IntakeLocation{dim=" + this.dimension.location() + ", pos=" + this.pos + "}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntakeLocation)) {
            return false;
        }
        IntakeLocation that = (IntakeLocation) o;
        return this.dimension.equals(that.dimension) && this.pos.equals((Object) that.pos);
    }

    public int hashCode() {
        return Objects.hash(this.dimension, this.pos);
    }

    public static final class Spec {
        @NotNull
        public final ResourceKey<Level> dimension;
        @NotNull
        public final BlockPos pos;

        public Spec(@NotNull ResourceKey<Level> dimension, @NotNull BlockPos pos) {
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            this.pos = Objects.requireNonNull(pos, "pos");
        }
    }
}
