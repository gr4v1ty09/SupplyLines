package com.gr4v1ty.supplylines.registry;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.jobs.JobStockKeeper;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModJobs {
    @SuppressWarnings("removal") // ResourceLocation constructor deprecated in Forge 47.x, will migrate in 1.21
    public static final DeferredRegister<JobEntry> JOBS = DeferredRegister
            .create(new ResourceLocation("minecolonies", "jobs"), "supplylines");
    public static final RegistryObject<JobEntry> STOCK_KEEPER = JOBS.register("stock_keeper",
            () -> new JobEntry.Builder().setRegistryName(SupplyLines.JOB_ID).setJobProducer(JobStockKeeper::new)
                    .setJobViewProducer(() -> DefaultJobView::new).createJobEntry());

    private ModJobs() {
    }
}
