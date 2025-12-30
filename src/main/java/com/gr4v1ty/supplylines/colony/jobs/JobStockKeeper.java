package com.gr4v1ty.supplylines.colony.jobs;

import com.gr4v1ty.supplylines.colony.ai.AIStockKeeper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobStockKeeper extends AbstractJob<AIStockKeeper, JobStockKeeper> {
    public JobStockKeeper(ICitizenData citizen) {
        super(citizen);
    }

    public AIStockKeeper generateAI() {
        return new AIStockKeeper(this);
    }
}
