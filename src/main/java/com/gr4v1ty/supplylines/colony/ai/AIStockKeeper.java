package com.gr4v1ty.supplylines.colony.ai;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.colony.jobs.JobStockKeeper;
import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;

public class AIStockKeeper extends AbstractEntityAIInteract<JobStockKeeper, BuildingStockKeeper> {

    /** Gets the state machine tick rate (requires restart to take effect) */
    private static int getStateMachineTickRate() {
        return ModConfig.SERVER.stateMachineTickRate.get();
    }

    /** Gets the walk speed for the given building */
    private static double getWalkSpeed(BuildingStockKeeper building) {
        return building != null ? building.getWalkSpeed() : ModConfig.SERVER.walkSpeed.get();
    }

    /** Gets the arrival distance squared for the given building */
    private static double getArriveDistanceSq(BuildingStockKeeper building) {
        return building != null ? building.getArriveDistanceSq() : ModConfig.SERVER.arriveDistanceSq.get();
    }

    /** Gets the inspect duration in state machine ticks for the given building */
    private static int getInspectDurationTicks(BuildingStockKeeper building) {
        return building != null ? building.getInspectDurationTicks() : ModConfig.SERVER.inspectDurationTicks.get();
    }

    /** Returns whether idle wander is enabled for the given building */
    private static boolean isIdleWanderEnabled(BuildingStockKeeper building) {
        return building != null ? building.isIdleWanderEnabled() : ModConfig.SERVER.enableIdleWander.get();
    }

    /** Returns idle wander chance (0-100) for the given building */
    private static int getIdleWanderChance(BuildingStockKeeper building) {
        return building != null ? building.getIdleWanderChance() : ModConfig.SERVER.idleWanderChance.get();
    }

    /**
     * Returns cooldown in game ticks for the given building (config is in seconds)
     */
    private static int getIdleWanderCooldownTicks(BuildingStockKeeper building) {
        int seconds = building != null ? building.getIdleWanderCooldown() : ModConfig.SERVER.idleWanderCooldown.get();
        return seconds * 20;
    }

    /**
     * Returns inspect duration in state machine ticks for the given building
     * (config is in seconds)
     */
    private static int getIdleInspectDuration(BuildingStockKeeper building) {
        int tickRate = getStateMachineTickRate();
        if (tickRate <= 0) {
            tickRate = 20;
        }
        int seconds = building != null ? building.getIdleInspectDuration() : ModConfig.SERVER.idleInspectDuration.get();
        return (seconds * 20) / tickRate;
    }

    /** Returns whether patrol should be randomized for the given building */
    private static boolean isPatrolRandomized(BuildingStockKeeper building) {
        return building != null ? building.isRandomPatrol() : ModConfig.SERVER.randomPatrol.get();
    }

    private enum Phase {
        WALKING, // Walking to main work target
        WORKING, // Main work at stock ticker
        PATROLLING, // Walking to a point of interest (order-triggered)
        INSPECTING, // Brief pause at point of interest
        IDLE_WANDER, // Walking to random inspection point (no orders)
        IDLE_INSPECT // Brief pause during idle wander
    }

    private Phase phase = Phase.WALKING;
    private BlockPos workTarget = null;
    private BlockPos patrolTarget = null;
    private int tickCounter = 0;
    private int inspectTicks = 0;
    private int patrolIndex = 0;
    private long lastIdleWanderTick = 0;
    private int nextArmSwingTick = 0;
    private final Random rnd = new Random();

    @SuppressWarnings({"unchecked"})
    public AIStockKeeper(JobStockKeeper job) {
        super(job);
        super.registerTargets(new TickingTransition[]{
                new AITarget<IAIState>(AIWorkerState.IDLE, this::idleProviderLoop, getStateMachineTickRate())});

        // Random initial offset to prevent synchronized behavior across multiple SKs
        this.tickCounter = rnd.nextInt(Math.max(1, getStateMachineTickRate()));
        this.nextArmSwingTick = rnd.nextInt(60);
    }

    @Override
    public Class<BuildingStockKeeper> getExpectedBuildingClass() {
        return BuildingStockKeeper.class;
    }

    private IAIState idleProviderLoop() {
        AbstractEntityCitizen entity = this.worker.getCitizenData().getEntity().get();
        BuildingStockKeeper hut = getOwnBuilding();

        if (hut != null) {
            hut.serverTick(this.world, true);
        }

        switch (phase) {
            case WALKING :
                if (workTarget == null) {
                    workTarget = getWorkTarget(hut);
                }
                if (workTarget == null) {
                    break;
                }

                double distSq = entity.distanceToSqr(workTarget.getX() + 0.5, workTarget.getY() + 0.5,
                        workTarget.getZ() + 0.5);

                if (distSq <= getArriveDistanceSq(hut)) {
                    entity.getNavigation().stop();
                    phase = Phase.WORKING;
                } else {
                    entity.getNavigation().moveTo(workTarget.getX() + 0.5, workTarget.getY() + 1.0,
                            workTarget.getZ() + 0.5, getWalkSpeed(hut));
                }
                break;

            case WORKING :
                // Check if we drifted too far (e.g., after sleeping)
                if (checkDriftAndReset(entity, workTarget, hut)) {
                    break;
                }

                // Ensure seat occupied for stock ticker (level 4+)
                if (hut != null && hut.getBuildingLevel() >= BuildingStockKeeper.getStockTickerRequiredLevel()) {
                    BlockPos seatPos = hut.getSeatPos();
                    if (seatPos != null) {
                        ensureSeatOccupied(seatPos);
                    }
                }

                // Look at work target with occasional variation
                handleLookAtBehavior(entity, hut);

                // Arm swing animation with variance
                handleArmSwing(entity);

                // Check if patrol requested (triggered by order placement)
                if (hut != null && hut.consumePatrolRequest()) {
                    BlockPos nextPatrol = selectNextPatrolPoint(hut, entity.blockPosition());
                    if (nextPatrol != null) {
                        patrolTarget = nextPatrol;
                        phase = Phase.PATROLLING;
                        break;
                    }
                }

                // Idle wander check (only when no pending work)
                if (shouldTriggerIdleWander(hut)) {
                    BlockPos wanderTarget = selectIdleWanderTarget(hut, entity.blockPosition());
                    if (wanderTarget != null) {
                        patrolTarget = wanderTarget;
                        phase = Phase.IDLE_WANDER;
                        lastIdleWanderTick = this.world.getGameTime();
                        break;
                    }
                }

                tickCounter++;
                break;

            case PATROLLING :
                if (patrolTarget == null) {
                    resetToWalking();
                    break;
                }

                double patrolDistSq = entity.distanceToSqr(patrolTarget.getX() + 0.5, patrolTarget.getY() + 0.5,
                        patrolTarget.getZ() + 0.5);

                if (patrolDistSq <= getArriveDistanceSq(hut)) {
                    entity.getNavigation().stop();
                    phase = Phase.INSPECTING;
                    inspectTicks = 0;
                } else {
                    // Navigate to belt level (not on top of it)
                    entity.getNavigation().moveTo(patrolTarget.getX() + 0.5, patrolTarget.getY(),
                            patrolTarget.getZ() + 0.5, getWalkSpeed(hut));
                }
                break;

            case INSPECTING :
                // Check if we drifted too far (e.g., after sleeping)
                if (checkDriftAndReset(entity, patrolTarget, hut)) {
                    break;
                }

                // Look at patrol target
                if (patrolTarget != null && inspectTicks % 20 == 0) {
                    entity.getLookControl().setLookAt(patrolTarget.getX() + 0.5, patrolTarget.getY() + 0.5,
                            patrolTarget.getZ() + 0.5);
                }

                // Arm swing animation while inspecting
                if (inspectTicks % 40 == 0 && rnd.nextBoolean()) {
                    entity.swing(InteractionHand.MAIN_HAND, true);
                }

                inspectTicks++;

                // Done inspecting, return to main work position
                if (inspectTicks >= getInspectDurationTicks(hut)) {
                    resetToWalking();
                }
                break;

            case IDLE_WANDER :
                if (patrolTarget == null) {
                    resetToWalking();
                    break;
                }

                double idleDistSq = entity.distanceToSqr(patrolTarget.getX() + 0.5, patrolTarget.getY() + 0.5,
                        patrolTarget.getZ() + 0.5);

                if (idleDistSq <= getArriveDistanceSq(hut)) {
                    entity.getNavigation().stop();
                    phase = Phase.IDLE_INSPECT;
                    inspectTicks = 0;
                } else {
                    entity.getNavigation().moveTo(patrolTarget.getX() + 0.5, patrolTarget.getY(),
                            patrolTarget.getZ() + 0.5, getWalkSpeed(hut));
                }
                break;

            case IDLE_INSPECT :
                // Check drift
                if (checkDriftAndReset(entity, patrolTarget, hut)) {
                    break;
                }

                // Look at the inspection target
                if (patrolTarget != null) {
                    entity.getLookControl().setLookAt(patrolTarget.getX() + 0.5, patrolTarget.getY() + 0.5,
                            patrolTarget.getZ() + 0.5);
                }

                // Occasional arm swing while inspecting
                if (inspectTicks % 30 == 0 && rnd.nextInt(100) < 30) {
                    entity.swing(InteractionHand.MAIN_HAND, true);
                }

                inspectTicks++;

                // Done inspecting, return to work
                if (inspectTicks >= getIdleInspectDuration(hut)) {
                    resetToWalking();
                }
                break;
        }
        return null;
    }

    /**
     * Checks if the worker has drifted too far from the target (e.g., after
     * sleeping). If so, resets state and returns true.
     */
    private boolean checkDriftAndReset(AbstractEntityCitizen entity, BlockPos target, BuildingStockKeeper hut) {
        if (target == null) {
            return false;
        }
        double distSq = entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSq > getArriveDistanceSq(hut) * 4) {
            resetToWalking();
            return true;
        }
        return false;
    }

    /**
     * Resets state to walk back to main work position.
     */
    private void resetToWalking() {
        patrolTarget = null;
        workTarget = null;
        phase = Phase.WALKING;
    }

    /**
     * Determines if idle wander should be triggered.
     */
    private boolean shouldTriggerIdleWander(BuildingStockKeeper hut) {
        if (!isIdleWanderEnabled(hut)) {
            return false;
        }
        if (hut == null || this.world == null) {
            return false;
        }

        // Check cooldown
        long now = this.world.getGameTime();
        if (now - lastIdleWanderTick < getIdleWanderCooldownTicks(hut)) {
            return false;
        }

        // Random chance
        return rnd.nextInt(100) < getIdleWanderChance(hut);
    }

    /**
     * Selects a random target for idle wandering.
     */
    private BlockPos selectIdleWanderTarget(BuildingStockKeeper hut, BlockPos currentPos) {
        if (hut == null) {
            return null;
        }

        List<BlockPos> candidates = new ArrayList<>();

        // Add display board (high priority for "checking status")
        BlockPos displayBoard = hut.getDisplayBoardPos();
        if (displayBoard != null) {
            candidates.add(displayBoard);
            candidates.add(displayBoard); // Double weight
        }

        // Add random subset of racks
        List<BlockPos> racks = hut.getRackPositions();
        if (!racks.isEmpty()) {
            int count = Math.min(2, racks.size());
            for (int i = 0; i < count; i++) {
                candidates.add(racks.get(rnd.nextInt(racks.size())));
            }
        }

        // Add belt positions
        candidates.addAll(hut.getBeltPositions());

        if (candidates.isEmpty()) {
            return null;
        }

        // Filter out positions too close
        List<BlockPos> validCandidates = new ArrayList<>();
        for (BlockPos pos : candidates) {
            double distSq = currentPos.distSqr(pos);
            if (distSq > getArriveDistanceSq(hut) * 2) {
                validCandidates.add(pos);
            }
        }

        if (validCandidates.isEmpty()) {
            return null;
        }

        // Random selection
        return validCandidates.get(rnd.nextInt(validCandidates.size()));
    }

    /**
     * Handles look-at behavior with variety.
     */
    private void handleLookAtBehavior(AbstractEntityCitizen entity, BuildingStockKeeper hut) {
        // Only update look direction periodically
        if (tickCounter % 20 != 0) {
            return;
        }

        // Random choice: work target (50%), display board (20%), belt (20%), no change
        // (10%)
        int choice = rnd.nextInt(10);
        BlockPos lookTarget = null;

        if (choice < 5 && workTarget != null) {
            lookTarget = workTarget;
        } else if (choice < 7 && hut != null && hut.getDisplayBoardPos() != null) {
            lookTarget = hut.getDisplayBoardPos();
        } else if (choice < 9 && hut != null && !hut.getBeltPositions().isEmpty()) {
            List<BlockPos> belts = hut.getBeltPositions();
            lookTarget = belts.get(rnd.nextInt(belts.size()));
        }
        // choice 9 = don't change look direction (natural pause)

        if (lookTarget != null) {
            entity.getLookControl().setLookAt(lookTarget.getX() + 0.5, lookTarget.getY() + 0.5,
                    lookTarget.getZ() + 0.5);
        }
    }

    /**
     * Handles arm swing with randomized timing.
     */
    private void handleArmSwing(AbstractEntityCitizen entity) {
        if (tickCounter >= nextArmSwingTick) {
            if (rnd.nextBoolean()) {
                entity.swing(InteractionHand.MAIN_HAND, true);
            }
            // Schedule next swing with variance: 40-80 ticks (at 20 tick rate = 2-4
            // seconds)
            nextArmSwingTick = tickCounter + 40 + rnd.nextInt(40);
        }
    }

    /**
     * Selects the next patrol point from available points of interest. Prioritizes
     * belt positions for order verification.
     */
    private BlockPos selectNextPatrolPoint(BuildingStockKeeper hut, BlockPos currentPos) {
        if (hut == null) {
            return null;
        }

        List<BlockPos> candidates = new ArrayList<>();

        // Add belt positions (primary target for order verification)
        List<BlockPos> belts = hut.getBeltPositions();
        candidates.addAll(belts);

        // Add a random rack position as fallback
        List<BlockPos> racks = hut.getRackPositions();
        if (!racks.isEmpty()) {
            candidates.add(racks.get(rnd.nextInt(racks.size())));
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Filter out positions too close to current position
        List<BlockPos> validCandidates = new ArrayList<>();
        for (BlockPos pos : candidates) {
            double distSq = currentPos.distSqr(pos);
            if (distSq > getArriveDistanceSq(hut) * 2) {
                validCandidates.add(pos);
            }
        }

        if (validCandidates.isEmpty()) {
            return null;
        }

        // Randomized or sequential selection based on config
        if (isPatrolRandomized(hut)) {
            return validCandidates.get(rnd.nextInt(validCandidates.size()));
        } else {
            patrolIndex = (patrolIndex + 1) % validCandidates.size();
            return validCandidates.get(patrolIndex);
        }
    }

    private BlockPos getWorkTarget(BuildingStockKeeper hut) {
        if (hut == null) {
            return null;
        }
        if (hut.getBuildingLevel() >= BuildingStockKeeper.getStockTickerRequiredLevel()) {
            return hut.getSeatPos();
        }
        return hut.getPosition();
    }

    private void ensureSeatOccupied(BlockPos seatPos) {
        if (this.world == null || seatPos == null) {
            return;
        }

        List<SeatEntity> nearbySeats = this.world.getEntitiesOfClass(SeatEntity.class, new AABB(seatPos).inflate(0.5));

        SeatEntity seatEntity;
        if (!nearbySeats.isEmpty()) {
            seatEntity = nearbySeats.get(0);
            if (!seatEntity.getPassengers().isEmpty()) {
                return;
            }
        } else {
            seatEntity = new SeatEntity(this.world, seatPos);
            seatEntity.setPos(seatPos.getX() + 0.5, seatPos.getY() + 0.5, seatPos.getZ() + 0.5);
            this.world.addFreshEntity(seatEntity);
        }

        Marker marker = new Marker(EntityType.MARKER, this.world);
        marker.setPos(seatPos.getX() + 0.5, seatPos.getY() + 0.5, seatPos.getZ() + 0.5);
        this.world.addFreshEntity(marker);

        if (!marker.startRiding(seatEntity, true)) {
            marker.discard();
        }
    }

    private BuildingStockKeeper getOwnBuilding() {
        return (BuildingStockKeeper) this.worker.getCitizenColonyHandler().getWorkBuilding();
    }
}
