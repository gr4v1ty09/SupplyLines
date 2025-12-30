package com.gr4v1ty.supplylines.registry;

import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;

public class ModBuildingModules {
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> STOCK_KEEPER_WORK = new BuildingEntry.ModuleProducer<>(
            "stock_keeper_work", () -> {
                WorkerBuildingModule module = new WorkerBuildingModule((JobEntry) ModJobs.STOCK_KEEPER.get(),
                        Skill.Strength, Skill.Dexterity, true, b -> 1);
                module.setHiringMode(HiringMode.MANUAL);
                return module;
            }, () -> WorkerBuildingModuleView::new);
}
