package com.gr4v1ty.supplylines.colony.manager;

import com.gr4v1ty.supplylines.config.ModConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkillManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillManager.class);
    private final WorkerBuildingModule workerModule;

    public SkillManager(WorkerBuildingModule workerModule) {
        this.workerModule = workerModule;
    }

    /**
     * Gets the worker's skill level for the specified skill.
     *
     * @param skill
     *            the skill to query
     * @return the skill level, or 0 if no worker is assigned or skill unavailable
     */
    public int getWorkerSkill(Skill skill) {
        if (this.workerModule == null) {
            return 0;
        }
        List<ICitizenData> assignedCitizens = this.workerModule.getAssignedCitizen();
        if (assignedCitizens.isEmpty()) {
            return 0;
        }
        ICitizenData citizen = assignedCitizens.get(0);
        if (citizen == null) {
            return 0;
        }
        ICitizenSkillHandler skillHandler = citizen.getCitizenSkillHandler();
        if (skillHandler == null) {
            return 0;
        }
        return skillHandler.getLevel(skill);
    }

    public int getWorkerStrengthSkill() {
        return getWorkerSkill(Skill.Strength);
    }

    public int getWorkerDexteritySkill() {
        return getWorkerSkill(Skill.Dexterity);
    }

    public int getRescanIntervalTicks() {
        int strength = this.getWorkerStrengthSkill();
        int base = ModConfig.SERVER.rescanBase.get();
        int min = ModConfig.SERVER.rescanMin.get();
        double multiplier = ModConfig.SERVER.rescanStrengthMultiplier.get();
        return Math.max(min, base - (int) (strength * multiplier));
    }

    public int getStockSnapshotIntervalTicks() {
        int dexterity = this.getWorkerDexteritySkill();
        int base = ModConfig.SERVER.snapshotBase.get();
        int min = ModConfig.SERVER.snapshotMin.get();
        double multiplier = ModConfig.SERVER.snapshotDexterityMultiplier.get();
        return Math.max(min, base - (int) (dexterity * multiplier));
    }

    public int getStagingProcessIntervalTicks() {
        int strength = this.getWorkerStrengthSkill();
        int base = ModConfig.SERVER.stagingBase.get();
        int min = ModConfig.SERVER.stagingMin.get();
        double multiplier = ModConfig.SERVER.stagingStrengthMultiplier.get();
        return Math.max(min, base - (int) (strength * multiplier));
    }

    public void awardWorkerSkillXP(double baseXp) {
        if (baseXp <= 0.0) {
            return;
        }
        if (this.workerModule == null) {
            return;
        }
        List<ICitizenData> assignedCitizens = this.workerModule.getAssignedCitizen();
        if (assignedCitizens.isEmpty()) {
            return;
        }
        ICitizenData citizen = assignedCitizens.get(0);
        if (citizen == null) {
            return;
        }
        ICitizenSkillHandler skillHandler = citizen.getCitizenSkillHandler();
        if (skillHandler == null) {
            return;
        }
        Random random = new Random();
        Skill skill = random.nextBoolean() ? Skill.Strength : Skill.Dexterity;
        skillHandler.addXpToSkill(skill, baseXp, citizen);
        LOGGER.debug("awardWorkerSkillXP: Awarded {} XP to {} skill for citizen {}",
                new Object[]{baseXp, skill.name(), citizen.getName()});
    }

    public boolean hasWorkerAssigned() {
        if (this.workerModule == null) {
            return false;
        }
        List<ICitizenData> assignedCitizens = this.workerModule.getAssignedCitizen();
        return !assignedCitizens.isEmpty() && assignedCitizens.get(0) != null;
    }

    /**
     * Gets the interval for restock policy checks based on worker dexterity. Scales
     * from 600 ticks (skill 0) to 200 ticks (skill 50).
     *
     * @return interval in ticks
     */
    public int getRestockIntervalTicks() {
        int dexterity = this.getWorkerDexteritySkill();
        int base = ModConfig.SERVER.restockBase.get();
        int min = ModConfig.SERVER.restockMin.get();
        double multiplier = ModConfig.SERVER.restockDexterityMultiplier.get();
        return Math.max(min, base - (int) (dexterity * multiplier));
    }
}
