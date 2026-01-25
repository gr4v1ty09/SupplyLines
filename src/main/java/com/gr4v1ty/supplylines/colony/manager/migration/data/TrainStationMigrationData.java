package com.gr4v1ty.supplylines.colony.manager.migration.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds extracted Train Station and Postbox data for migration during building
 * upgrades. Uses identity-based matching (station name, postbox address) rather
 * than position-based, since blocks may move between building levels.
 */
public class TrainStationMigrationData {

    private static final String TAG_FROM_LEVEL = "FromLevel";
    private static final String TAG_TO_LEVEL = "ToLevel";
    private static final String TAG_STATIONS = "Stations";
    private static final String TAG_POSTBOXES = "Postboxes";

    private final List<StationConfig> stations = new ArrayList<>();
    private final List<PostboxConfig> postboxes = new ArrayList<>();
    private final int fromLevel;
    private final int toLevel;

    /**
     * Train Station configuration - matched by name after upgrade. Create enforces
     * uniqueness of station names across connected rail networks.
     *
     * Also stores track targeting data (absolute track position and direction) so
     * the station can be reconnected to the track after the building is rebuilt at
     * a new location.
     */
    public static class StationConfig {
        private static final String TAG_NAME = "Name";
        private static final String TAG_TRACK_POS = "TrackPos";
        private static final String TAG_DIRECTION_POSITIVE = "DirectionPositive";

        private final String name;
        @Nullable
        private final BlockPos absoluteTrackPos;
        private final boolean directionPositive;

        public StationConfig(String name) {
            this(name, null, true);
        }

        public StationConfig(String name, @Nullable BlockPos absoluteTrackPos, boolean directionPositive) {
            this.name = name != null ? name : "";
            this.absoluteTrackPos = absoluteTrackPos;
            this.directionPositive = directionPositive;
        }

        public String getName() {
            return name;
        }

        @Nullable
        public BlockPos getAbsoluteTrackPos() {
            return absoluteTrackPos;
        }

        public boolean isDirectionPositive() {
            return directionPositive;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_NAME, name);
            if (absoluteTrackPos != null) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", absoluteTrackPos.getX());
                posTag.putInt("Y", absoluteTrackPos.getY());
                posTag.putInt("Z", absoluteTrackPos.getZ());
                tag.put(TAG_TRACK_POS, posTag);
            }
            tag.putBoolean(TAG_DIRECTION_POSITIVE, directionPositive);
            return tag;
        }

        public static StationConfig fromNBT(CompoundTag tag) {
            String name = tag.getString(TAG_NAME);
            BlockPos trackPos = null;
            if (tag.contains(TAG_TRACK_POS)) {
                CompoundTag posTag = tag.getCompound(TAG_TRACK_POS);
                trackPos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
            }
            boolean directionPositive = !tag.contains(TAG_DIRECTION_POSITIVE) || tag.getBoolean(TAG_DIRECTION_POSITIVE);
            return new StationConfig(name, trackPos, directionPositive);
        }
    }

    /**
     * Postbox configuration - matched by addressFilter after upgrade. Player is
     * responsible for unique addresses; duplicates get first-match behavior.
     */
    public static class PostboxConfig {
        private static final String TAG_ADDRESS_FILTER = "AddressFilter";
        private static final String TAG_TARGET = "Target";
        private static final String TAG_ACCEPTS_PACKAGES = "AcceptsPackages";
        private static final String TAG_LINKED_STATION_NAME = "LinkedStationName";

        private final String addressFilter;
        @Nullable
        private final CompoundTag target;
        private final boolean acceptsPackages;
        @Nullable
        private final String linkedStationName;

        public PostboxConfig(String addressFilter, @Nullable CompoundTag target, boolean acceptsPackages,
                @Nullable String linkedStationName) {
            this.addressFilter = addressFilter != null ? addressFilter : "";
            this.target = target;
            this.acceptsPackages = acceptsPackages;
            this.linkedStationName = linkedStationName;
        }

        public String getAddressFilter() {
            return addressFilter;
        }

        @Nullable
        public CompoundTag getTarget() {
            return target;
        }

        public boolean isAcceptsPackages() {
            return acceptsPackages;
        }

        @Nullable
        public String getLinkedStationName() {
            return linkedStationName;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_ADDRESS_FILTER, addressFilter);
            if (target != null) {
                tag.put(TAG_TARGET, target.copy());
            }
            tag.putBoolean(TAG_ACCEPTS_PACKAGES, acceptsPackages);
            if (linkedStationName != null) {
                tag.putString(TAG_LINKED_STATION_NAME, linkedStationName);
            }
            return tag;
        }

        public static PostboxConfig fromNBT(CompoundTag tag) {
            String addressFilter = tag.getString(TAG_ADDRESS_FILTER);
            CompoundTag target = tag.contains(TAG_TARGET) ? tag.getCompound(TAG_TARGET).copy() : null;
            boolean acceptsPackages = tag.getBoolean(TAG_ACCEPTS_PACKAGES);
            String linkedStationName = tag.contains(TAG_LINKED_STATION_NAME)
                    ? tag.getString(TAG_LINKED_STATION_NAME)
                    : null;

            return new PostboxConfig(addressFilter, target, acceptsPackages, linkedStationName);
        }
    }

    public TrainStationMigrationData(int fromLevel, int toLevel) {
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
    }

    public void addStation(StationConfig config) {
        if (config != null && !config.getName().isEmpty()) {
            stations.add(config);
        }
    }

    public void addPostbox(PostboxConfig config) {
        if (config != null) {
            postboxes.add(config);
        }
    }

    public List<StationConfig> getStations() {
        return Collections.unmodifiableList(stations);
    }

    public List<PostboxConfig> getPostboxes() {
        return Collections.unmodifiableList(postboxes);
    }

    public int getFromLevel() {
        return fromLevel;
    }

    public int getToLevel() {
        return toLevel;
    }

    public boolean isEmpty() {
        return stations.isEmpty() && postboxes.isEmpty();
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_FROM_LEVEL, fromLevel);
        tag.putInt(TAG_TO_LEVEL, toLevel);

        ListTag stationsList = new ListTag();
        for (StationConfig station : stations) {
            stationsList.add(station.toNBT());
        }
        tag.put(TAG_STATIONS, stationsList);

        ListTag postboxesList = new ListTag();
        for (PostboxConfig postbox : postboxes) {
            postboxesList.add(postbox.toNBT());
        }
        tag.put(TAG_POSTBOXES, postboxesList);

        return tag;
    }

    public static TrainStationMigrationData fromNBT(CompoundTag tag) {
        int fromLevel = tag.getInt(TAG_FROM_LEVEL);
        int toLevel = tag.getInt(TAG_TO_LEVEL);

        TrainStationMigrationData data = new TrainStationMigrationData(fromLevel, toLevel);

        ListTag stationsList = tag.getList(TAG_STATIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < stationsList.size(); i++) {
            data.stations.add(StationConfig.fromNBT(stationsList.getCompound(i)));
        }

        ListTag postboxesList = tag.getList(TAG_POSTBOXES, Tag.TAG_COMPOUND);
        for (int i = 0; i < postboxesList.size(); i++) {
            data.postboxes.add(PostboxConfig.fromNBT(postboxesList.getCompound(i)));
        }

        return data;
    }
}
