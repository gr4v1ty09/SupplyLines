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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;

public class AIStockKeeper extends AbstractEntityAIInteract<JobStockKeeper, BuildingStockKeeper> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AIStockKeeper.class);

    /** Gets the state machine tick rate (requires restart to take effect) */
    private static int getStateMachineTickRate() {
        return ModConfig.SERVER.stateMachineTickRate.get();
    }

    /** Gets the walk speed from config */
    private static double getWalkSpeed() {
        return ModConfig.SERVER.walkSpeed.get();
    }

    /** Gets the arrival distance squared from config */
    private static double getArriveDistanceSq() {
        return ModConfig.SERVER.arriveDistanceSq.get();
    }

    /** Gets the inspect duration in state machine ticks from config */
    private static int getInspectDurationTicks() {
        return ModConfig.SERVER.inspectDurationTicks.get();
    }

    private enum Phase {
        WALKING, // Walking to main work target
        WORKING, // Main work at stock ticker
        PATROLLING, // Walking to a point of interest
        INSPECTING // Brief pause at point of interest
    }

    private Phase phase = Phase.WALKING;
    private BlockPos workTarget = null;
    private BlockPos patrolTarget = null;
    private int tickCounter = 0;
    private int inspectTicks = 0;
    private int patrolIndex = 0;
    private final Random rnd = new Random();

    @SuppressWarnings({"unchecked"})
    public AIStockKeeper(JobStockKeeper job) {
        super(job);
        super.registerTargets(new TickingTransition[]{
                new AITarget<IAIState>(AIWorkerState.IDLE, this::idleProviderLoop, getStateMachineTickRate())});
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

                if (distSq <= getArriveDistanceSq()) {
                    entity.getNavigation().stop();
                    phase = Phase.WORKING;
                } else {
                    entity.getNavigation().moveTo(workTarget.getX() + 0.5, workTarget.getY() + 1.0,
                            workTarget.getZ() + 0.5, getWalkSpeed());
                }
                break;

            case WORKING :
                // Check if we drifted too far (e.g., after sleeping)
                if (checkDriftAndReset(entity, workTarget)) {
                    break;
                }

                // Ensure seat occupied for stock ticker (level 4+)
                if (hut != null && hut.getBuildingLevel() >= BuildingStockKeeper.getStockTickerRequiredLevel()) {
                    BlockPos seatPos = hut.getSeatPos();
                    if (seatPos != null) {
                        ensureSeatOccupied(seatPos);
                    }
                }

                // Look at work target
                if (workTarget != null && tickCounter % 20 == 0) {
                    entity.getLookControl().setLookAt(workTarget.getX() + 0.5, workTarget.getY() + 0.5,
                            workTarget.getZ() + 0.5);
                }

                // Arm swing animation
                if (tickCounter % 60 == 0 && rnd.nextBoolean()) {
                    entity.swing(InteractionHand.MAIN_HAND, true);
                }

                // Check if patrol requested (triggered by order placement)
                if (hut != null && hut.consumePatrolRequest()) {
                    LOGGER.debug("[SK-AI] Patrol requested, selecting patrol point");
                    BlockPos nextPatrol = selectNextPatrolPoint(hut, entity.blockPosition());
                    if (nextPatrol != null) {
                        LOGGER.debug("[SK-AI] Starting patrol to {}", nextPatrol);
                        patrolTarget = nextPatrol;
                        phase = Phase.PATROLLING;
                        break;
                    } else {
                        LOGGER.debug("[SK-AI] No valid patrol point found");
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

                if (patrolDistSq <= getArriveDistanceSq()) {
                    LOGGER.debug("[SK-AI] Arrived at patrol target, starting inspection");
                    entity.getNavigation().stop();
                    phase = Phase.INSPECTING;
                    inspectTicks = 0;
                } else {
                    // Navigate to belt level (not on top of it)
                    entity.getNavigation().moveTo(patrolTarget.getX() + 0.5, patrolTarget.getY(),
                            patrolTarget.getZ() + 0.5, getWalkSpeed());
                }
                break;

            case INSPECTING :
                // Check if we drifted too far (e.g., after sleeping)
                if (checkDriftAndReset(entity, patrolTarget)) {
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
                if (inspectTicks >= getInspectDurationTicks()) {
                    LOGGER.debug("[SK-AI] Inspection complete, returning to work");
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
    private boolean checkDriftAndReset(AbstractEntityCitizen entity, BlockPos target) {
        if (target == null) {
            return false;
        }
        double distSq = entity.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSq > getArriveDistanceSq() * 4) {
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
        LOGGER.debug("[SK-AI] Belt positions: {}", belts.size());
        candidates.addAll(belts);

        // Add a random rack position as fallback
        List<BlockPos> racks = hut.getRackPositions();
        LOGGER.debug("[SK-AI] Rack positions: {}", racks.size());
        if (!racks.isEmpty()) {
            candidates.add(racks.get(rnd.nextInt(racks.size())));
        }

        if (candidates.isEmpty()) {
            LOGGER.debug("[SK-AI] No candidates at all");
            return null;
        }

        // Filter out positions too close to current position
        List<BlockPos> validCandidates = new ArrayList<>();
        for (BlockPos pos : candidates) {
            double distSq = currentPos.distSqr(pos);
            if (distSq > getArriveDistanceSq() * 2) {
                validCandidates.add(pos);
            } else {
                LOGGER.debug("[SK-AI] Filtered out {} (too close, distSq={})", pos, distSq);
            }
        }

        if (validCandidates.isEmpty()) {
            LOGGER.debug("[SK-AI] All candidates filtered out");
            return null;
        }

        // Cycle through candidates
        patrolIndex = (patrolIndex + 1) % validCandidates.size();
        return validCandidates.get(patrolIndex);
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
