package com.gr4v1ty.supplylines.colony.ai;

import com.gr4v1ty.supplylines.colony.buildings.BuildingStockKeeper;
import com.gr4v1ty.supplylines.colony.jobs.JobStockKeeper;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;

public class AIStockKeeper extends AbstractEntityAIInteract<JobStockKeeper, BuildingStockKeeper> {

    private static final int STATE_MACHINE_TICK_RATE = 20;
    private static final double WALK_SPEED = 1.0;
    private static final double ARRIVE_DISTANCE_SQ = 4.0;
    private static final int STOCK_TICKER_LEVEL = 4;

    private enum Phase {
        WALKING, WORKING
    }

    private Phase phase = Phase.WALKING;
    private BlockPos workTarget = null;
    private int tickCounter = 0;
    private final Random rnd = new Random();

    @SuppressWarnings({"unchecked"})
    public AIStockKeeper(JobStockKeeper job) {
        super(job);
        super.registerTargets(new TickingTransition[]{
                new AITarget<IAIState>(AIWorkerState.IDLE, this::idleProviderLoop, STATE_MACHINE_TICK_RATE)});
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

                if (distSq <= ARRIVE_DISTANCE_SQ) {
                    entity.getNavigation().stop();
                    phase = Phase.WORKING;
                } else {
                    entity.getNavigation().moveTo(workTarget.getX() + 0.5, workTarget.getY() + 1.0,
                            workTarget.getZ() + 0.5, WALK_SPEED);
                }
                break;

            case WORKING :
                // Check if we drifted too far (e.g., after sleeping)
                if (workTarget != null) {
                    double workDistSq = entity.distanceToSqr(workTarget.getX() + 0.5, workTarget.getY() + 0.5,
                            workTarget.getZ() + 0.5);
                    if (workDistSq > ARRIVE_DISTANCE_SQ * 4) {
                        phase = Phase.WALKING;
                        break;
                    }
                }

                // Ensure seat occupied for stock ticker (level 4+)
                if (hut != null && hut.getBuildingLevel() >= STOCK_TICKER_LEVEL) {
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

                tickCounter++;
                break;
        }
        return null;
    }

    private BlockPos getWorkTarget(BuildingStockKeeper hut) {
        if (hut == null) {
            return null;
        }
        if (hut.getBuildingLevel() >= STOCK_TICKER_LEVEL) {
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
