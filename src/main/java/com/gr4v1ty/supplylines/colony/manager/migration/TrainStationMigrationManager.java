package com.gr4v1ty.supplylines.colony.manager.migration;

import com.gr4v1ty.supplylines.colony.manager.migration.data.TrainStationMigrationData;
import com.gr4v1ty.supplylines.util.LogTags;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.GlobalStation.GlobalPackagePort;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Manages migration of Create Train Station and Postbox configurations during
 * building upgrades. Uses identity-based matching (station name, postbox
 * address) rather than position-based restoration.
 */
public class TrainStationMigrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrainStationMigrationManager.class);

    private TrainStationMigrationManager() {
        // Utility class
    }

    /**
     * Scans building bounds for StationBlockEntity and PostboxBlockEntity instances
     * and extracts their configurations for identity-based restoration after
     * upgrade.
     *
     * @param level
     *            The world
     * @param corners
     *            Building bounds (min, max)
     * @param fromLevel
     *            Current building level
     * @param toLevel
     *            Target building level
     * @return Migration data containing all station/postbox configurations, or null
     *         if none found
     */
    @Nullable
    public static TrainStationMigrationData scanAndExtractData(Level level, Tuple<BlockPos, BlockPos> corners,
            int fromLevel, int toLevel) {

        if (level == null || corners == null) {
            return null;
        }

        BlockPos min = corners.getA();
        BlockPos max = corners.getB();

        TrainStationMigrationData data = new TrainStationMigrationData(fromLevel, toLevel);

        // Collect station names for postbox linking
        Map<GlobalStation, String> stationNames = new HashMap<>();

        LOGGER.info("{} Scanning for Train Stations and Postboxes in bounds {} to {}", LogTags.MIGRATION, min, max);

        // Scan for stations
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);

                    if (be instanceof StationBlockEntity stationBE) {
                        GlobalStation station = stationBE.getStation();
                        if (station != null && station.name != null && !station.name.isEmpty()) {
                            // Extract track targeting data from edgePoint
                            BlockPos absoluteTrackPos = null;
                            boolean directionPositive = true;

                            TrackTargetingBehaviour<?> edgePoint = stationBE.edgePoint;
                            if (edgePoint != null && edgePoint.hasValidTrack()) {
                                // getGlobalPosition() returns the absolute track position
                                absoluteTrackPos = edgePoint.getGlobalPosition();
                                directionPositive = edgePoint.getTargetDirection() == AxisDirection.POSITIVE;
                            }

                            data.addStation(new TrainStationMigrationData.StationConfig(station.name, absoluteTrackPos,
                                    directionPositive));
                            stationNames.put(station, station.name);

                            LOGGER.debug("{} Found station '{}' at {}, trackPos={}, direction={}", LogTags.MIGRATION,
                                    station.name, pos, absoluteTrackPos, directionPositive ? "POSITIVE" : "NEGATIVE");
                        }
                    }
                }
            }
        }

        // Scan for postboxes
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);

                    if (be instanceof PostboxBlockEntity postboxBE) {
                        TrainStationMigrationData.PostboxConfig config = extractPostboxConfig(postboxBE, stationNames);
                        if (config != null) {
                            data.addPostbox(config);

                            LOGGER.debug("{} Found postbox at {}, addressFilter='{}', linkedStation='{}'",
                                    LogTags.MIGRATION, pos, config.getAddressFilter(), config.getLinkedStationName());
                        }
                    }
                }
            }
        }

        LOGGER.info("{} Extracted {} station(s), {} postbox(es) for migration", LogTags.MIGRATION,
                data.getStations().size(), data.getPostboxes().size());

        return data.isEmpty() ? null : data;
    }

    @Nullable
    private static TrainStationMigrationData.PostboxConfig extractPostboxConfig(PostboxBlockEntity postboxBE,
            Map<GlobalStation, String> stationNames) {

        String addressFilter = postboxBE.addressFilter;
        boolean acceptsPackages = postboxBE.acceptsPackages;

        // Serialize PackagePortTarget
        CompoundTag targetTag = null;
        PackagePortTarget target = postboxBE.target;
        if (target != null) {
            try {
                targetTag = target.write();
            } catch (Exception e) {
                LOGGER.warn("{} Failed to serialize PackagePortTarget: {}", LogTags.MIGRATION, e.getMessage());
            }
        }

        // Find linked station name
        String linkedStationName = null;
        WeakReference<GlobalStation> stationRef = postboxBE.trackedGlobalStation;
        if (stationRef != null) {
            GlobalStation station = stationRef.get();
            if (station != null) {
                linkedStationName = stationNames.get(station);
                if (linkedStationName == null && station.name != null) {
                    linkedStationName = station.name;
                }
            }
        }

        return new TrainStationMigrationData.PostboxConfig(addressFilter, targetTag, acceptsPackages,
                linkedStationName);
    }

    /**
     * Applies cached station/postbox configurations after upgrade. Scans the new
     * building for blocks and matches by identity (name/address).
     *
     * @param level
     *            The world
     * @param newCorners
     *            New building bounds after upgrade
     * @param data
     *            The cached migration data
     * @return Result containing counts and warnings
     */
    public static MigrationResult applyMigrationData(Level level, Tuple<BlockPos, BlockPos> newCorners,
            TrainStationMigrationData data) {

        if (level == null || newCorners == null || data == null || data.isEmpty()) {
            return new MigrationResult(0, 0, 0, 0, Collections.emptyList());
        }

        BlockPos min = newCorners.getA();
        BlockPos max = newCorners.getB();

        int stationsRestored = 0;
        int postboxesRestored = 0;
        int stationsFailed = 0;
        int postboxesFailed = 0;
        List<String> warnings = new ArrayList<>();

        // Scan new building for stations and postboxes
        // Note: Station may not be connected yet, so we collect ALL station block
        // entities
        List<StationBlockEntity> allStationBEs = new ArrayList<>();
        Map<String, List<PostboxBlockEntity>> postboxesByAddress = new HashMap<>();

        LOGGER.info("{} Scanning new building bounds {} to {} for migration targets", LogTags.MIGRATION, min, max);

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);

                    if (be instanceof StationBlockEntity stationBE) {
                        allStationBEs.add(stationBE);
                    } else if (be instanceof PostboxBlockEntity postboxBE) {
                        String addr = postboxBE.addressFilter;
                        postboxesByAddress.computeIfAbsent(addr, k -> new ArrayList<>()).add(postboxBE);
                    }
                }
            }
        }

        Map<String, GlobalStation> restoredStations = new HashMap<>();
        Set<StationBlockEntity> usedStations = new HashSet<>();

        // Phase 1: Restore station configurations including track targeting
        for (TrainStationMigrationData.StationConfig config : data.getStations()) {
            String targetName = config.getName();

            // Find an available station block entity
            StationBlockEntity stationBE = null;
            for (StationBlockEntity candidate : allStationBEs) {
                if (!usedStations.contains(candidate)) {
                    stationBE = candidate;
                    usedStations.add(candidate);
                    break;
                }
            }

            if (stationBE == null) {
                stationsFailed++;
                warnings.add("Station '" + targetName + "' - no station block found in new building");
                continue;
            }

            // Check if track targeting needs restoration
            // Note: getStation() may return non-null due to UUID lookup even if track isn't
            // properly connected
            TrackTargetingBehaviour<?> edgePoint = stationBE.edgePoint;
            boolean needsTrackRestore = edgePoint == null || !edgePoint.hasValidTrack();

            GlobalStation station = stationBE.getStation();
            if (needsTrackRestore && config.getAbsoluteTrackPos() != null) {
                // Station not properly connected to track - apply track targeting data
                LOGGER.debug("{} Station at {} needs track restoration (hasValidTrack={})", LogTags.MIGRATION,
                        stationBE.getBlockPos(), edgePoint != null ? edgePoint.hasValidTrack() : "null");
                station = restoreTrackTargeting(level, stationBE, config);
            }

            if (station == null) {
                stationsFailed++;
                warnings.add("Station '" + targetName + "' - could not connect station to track");
                continue;
            }

            // Apply the saved name
            station.name = targetName;
            stationBE.setChanged();
            stationBE.notifyUpdate();

            restoredStations.put(targetName, station);
            stationsRestored++;

            LOGGER.info("{} Restored station '{}' at {}", LogTags.MIGRATION, targetName, stationBE.getBlockPos());
        }

        // Phase 2: Restore postbox configurations
        Set<PostboxBlockEntity> usedPostboxes = new HashSet<>();

        for (TrainStationMigrationData.PostboxConfig config : data.getPostboxes()) {
            String targetAddress = config.getAddressFilter();

            // Find postboxes with matching address (or empty address to configure)
            List<PostboxBlockEntity> candidates = postboxesByAddress.get(targetAddress);

            // If no exact match, try empty-address postboxes
            if (candidates == null || candidates.isEmpty()) {
                candidates = postboxesByAddress.get("");
            }

            PostboxBlockEntity postboxBE = null;
            if (candidates != null) {
                for (PostboxBlockEntity candidate : candidates) {
                    if (!usedPostboxes.contains(candidate)) {
                        postboxBE = candidate;
                        usedPostboxes.add(candidate);
                        break;
                    }
                }
            }

            if (postboxBE == null) {
                postboxesFailed++;
                warnings.add("Postbox with address '" + targetAddress + "' - no postbox found");
                continue;
            }

            boolean success = applyPostboxConfig(postboxBE, config, restoredStations);
            if (success) {
                postboxesRestored++;
                LOGGER.info("{} Restored postbox at {} with address '{}'", LogTags.MIGRATION, postboxBE.getBlockPos(),
                        targetAddress);
            } else {
                postboxesFailed++;
                warnings.add("Postbox with address '" + targetAddress + "' - failed to apply configuration");
            }
        }

        LOGGER.info("{} Migration complete: {} stations restored, {} failed; {} postboxes restored, {} failed",
                LogTags.MIGRATION, stationsRestored, stationsFailed, postboxesRestored, postboxesFailed);

        return new MigrationResult(stationsRestored, postboxesRestored, stationsFailed, postboxesFailed, warnings);
    }

    private static boolean applyPostboxConfig(PostboxBlockEntity postboxBE,
            TrainStationMigrationData.PostboxConfig config, Map<String, GlobalStation> restoredStations) {
        try {
            // Restore addressFilter
            if (config.getAddressFilter() != null) {
                postboxBE.addressFilter = config.getAddressFilter();
            }

            // Restore acceptsPackages
            postboxBE.acceptsPackages = config.isAcceptsPackages();

            // Restore PackagePortTarget
            if (config.getTarget() != null) {
                try {
                    PackagePortTarget target = PackagePortTarget.read(config.getTarget());
                    if (target != null) {
                        postboxBE.target = target;
                    }
                } catch (Exception e) {
                    LOGGER.warn("{} Failed to restore PackagePortTarget: {}", LogTags.MIGRATION, e.getMessage());
                }
            }

            // Re-establish station link if possible
            if (config.getLinkedStationName() != null && !config.getLinkedStationName().isEmpty()) {
                GlobalStation station = restoredStations.get(config.getLinkedStationName());
                if (station != null) {
                    postboxBE.trackedGlobalStation = new WeakReference<>(station);

                    // Re-register in station's connectedPorts
                    BlockPos postboxPos = postboxBE.getBlockPos();
                    GlobalPackagePort port = station.connectedPorts.get(postboxPos);
                    if (port == null) {
                        port = new GlobalPackagePort();
                        port.address = config.getAddressFilter();
                        station.connectedPorts.put(postboxPos, port);
                    } else {
                        port.address = config.getAddressFilter();
                    }

                    LOGGER.debug("{} Re-linked postbox to station '{}'", LogTags.MIGRATION,
                            config.getLinkedStationName());
                } else {
                    LOGGER.warn("{} Could not find restored station '{}' for postbox linking", LogTags.MIGRATION,
                            config.getLinkedStationName());
                }
            }

            // Mark block entity as changed
            postboxBE.setChanged();
            postboxBE.notifyUpdate();

            return true;
        } catch (Exception e) {
            LOGGER.error("{} Failed to apply postbox config: {}", LogTags.MIGRATION, e.getMessage());
            return false;
        }
    }

    /**
     * Restores track targeting for a station block entity by applying saved track
     * position data. This reconnects the station to the track graph after the
     * building has been rebuilt at a new location.
     *
     * Uses reflection to set the private fields in TrackTargetingBehaviour since
     * there's no public API for setting track targeting data.
     *
     * @param level
     *            The world (unused but kept for API consistency)
     * @param stationBE
     *            The station block entity to restore
     * @param config
     *            The saved station configuration with track targeting data
     * @return The GlobalStation if successfully connected, null otherwise
     */
    @Nullable
    @SuppressWarnings("unused")
    private static GlobalStation restoreTrackTargeting(Level level, StationBlockEntity stationBE,
            TrainStationMigrationData.StationConfig config) {
        try {
            TrackTargetingBehaviour<?> edgePoint = stationBE.edgePoint;
            if (edgePoint == null) {
                LOGGER.warn("{} Station at {} has no edgePoint behaviour", LogTags.MIGRATION, stationBE.getBlockPos());
                return null;
            }

            BlockPos stationPos = stationBE.getBlockPos();
            BlockPos absoluteTrack = config.getAbsoluteTrackPos();

            // Calculate new relative offset from new station pos to same track
            BlockPos relativeTrack = absoluteTrack.subtract(stationPos);

            LOGGER.debug("{} Restoring track targeting: station at {}, track at {}, relative offset {}",
                    LogTags.MIGRATION, stationPos, absoluteTrack, relativeTrack);

            // Use reflection to set private fields in TrackTargetingBehaviour
            Class<?> clazz = TrackTargetingBehaviour.class;

            // Set targetTrack field
            Field targetTrackField = clazz.getDeclaredField("targetTrack");
            targetTrackField.setAccessible(true);
            targetTrackField.set(edgePoint, relativeTrack);

            // Set targetDirection field
            Field targetDirectionField = clazz.getDeclaredField("targetDirection");
            targetDirectionField.setAccessible(true);
            AxisDirection direction = config.isDirectionPositive() ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE;
            targetDirectionField.set(edgePoint, direction);

            // Set new UUID for the edge point
            Field idField = clazz.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(edgePoint, UUID.randomUUID());

            // Mark for update - the tick() method will call createEdgePoint()
            stationBE.setChanged();
            stationBE.notifyUpdate();

            // Try to get the station - it may not be immediately available
            // as createEdgePoint() is called in tick()
            GlobalStation station = stationBE.getStation();
            if (station == null) {
                // Force a tick to trigger edge point creation
                edgePoint.tick();
                station = stationBE.getStation();
            }

            if (station != null) {
                LOGGER.info("{} Successfully restored track targeting for station at {}", LogTags.MIGRATION,
                        stationPos);
            } else {
                LOGGER.warn("{} Track targeting restored but station not yet connected at {}", LogTags.MIGRATION,
                        stationPos);
            }

            return station;
        } catch (Exception e) {
            LOGGER.error("{} Failed to restore track targeting: {}", LogTags.MIGRATION, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Result of migration application.
     */
    public static class MigrationResult {
        public final int stationsRestored;
        public final int postboxesRestored;
        public final int stationsFailed;
        public final int postboxesFailed;
        public final List<String> warnings;

        public MigrationResult(int stationsRestored, int postboxesRestored, int stationsFailed, int postboxesFailed,
                List<String> warnings) {
            this.stationsRestored = stationsRestored;
            this.postboxesRestored = postboxesRestored;
            this.stationsFailed = stationsFailed;
            this.postboxesFailed = postboxesFailed;
            this.warnings = warnings != null ? new ArrayList<>(warnings) : Collections.emptyList();
        }
    }
}
