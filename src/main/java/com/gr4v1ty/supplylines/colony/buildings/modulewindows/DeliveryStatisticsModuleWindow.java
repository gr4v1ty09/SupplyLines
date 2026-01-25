package com.gr4v1ty.supplylines.colony.buildings.modulewindows;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.buildings.modules.DeliveryStatisticsModule;
import com.gr4v1ty.supplylines.colony.buildings.moduleviews.DeliveryStatisticsModuleView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Window for displaying Stock Keeper building statistics. Shows metrics like
 * requests fulfilled, items delivered, etc. with time period filtering.
 */
public class DeliveryStatisticsModuleWindow extends AbstractModuleWindow<DeliveryStatisticsModuleView> {

    /** Resource path for the layout. */
    @SuppressWarnings("removal")
    private static final ResourceLocation RESOURCE = new ResourceLocation(SupplyLines.MOD_ID,
            "gui/layouthuts/layoutstatistics.xml");

    /** Map of interval translation keys to day counts. */
    private static final LinkedHashMap<String, Integer> INTERVALS = new LinkedHashMap<>();

    static {
        INTERVALS.put("com.supplylines.gui.interval.yesterday", 1);
        INTERVALS.put("com.supplylines.gui.interval.lastweek", 7);
        INTERVALS.put("com.supplylines.gui.interval.100days", 100);
        INTERVALS.put("com.supplylines.gui.interval.alltime", -1);
    }

    /** ID for the statistics list. */
    private static final String LIST_STATS = "stats";

    /** ID for the interval dropdown. */
    private static final String DROPDOWN_INTERVAL = "intervals";

    /** ID for the hide zero stats button. */
    private static final String BUTTON_HIDE_ZERO = "hidezero";

    /** Texture for hide zero button when enabled. */
    @SuppressWarnings("removal")
    private static final ResourceLocation TEXTURE_CHECK_ON = new ResourceLocation("minecolonies",
            "textures/gui/builderhut/builder_button_mini_check.png");

    /** Texture for hide zero button when disabled. */
    @SuppressWarnings("removal")
    private static final ResourceLocation TEXTURE_CHECK_OFF = new ResourceLocation("minecolonies",
            "textures/gui/builderhut/builder_button_mini.png");

    /** Currently selected interval key. */
    private String selectedInterval = "com.supplylines.gui.interval.yesterday";

    /** Whether to hide stats with zero values in the current period. */
    private boolean hideZeroStats = false;

    /**
     * Simple holder for a stat ID and its value.
     */
    private record StatEntry(String statId, int value) {
    }

    /**
     * Constructor for the statistics window.
     *
     * @param moduleView
     *            the module view.
     */
    public DeliveryStatisticsModuleWindow(final DeliveryStatisticsModuleView moduleView) {
        super(moduleView, RESOURCE);
        registerButton(BUTTON_HIDE_ZERO, this::toggleHideZero);
    }

    @Override
    public void onOpened() {
        super.onOpened();
        updateStats();
    }

    /**
     * Update the statistics display.
     */
    private void updateStats() {
        final IStatisticsManager statisticsManager = moduleView.getBuildingStatisticsManager();
        final int interval = INTERVALS.get(selectedInterval);
        final int currentDay = buildingView.getColony().getDay();

        // Build filtered and sorted stat list
        final List<StatEntry> displayStats = new ArrayList<>();

        for (String statId : statisticsManager.getStatTypes()) {
            // Skip legacy stat keys that don't have our new prefixes
            if (!statId.startsWith(DeliveryStatisticsModule.PREFIX_ORDER)
                    && !statId.startsWith(DeliveryStatisticsModule.PREFIX_ITEM)) {
                continue;
            }

            int value;
            if (interval > 0) {
                value = statisticsManager.getStatsInPeriod(statId, currentDay - interval, currentDay);
            } else {
                value = statisticsManager.getStatTotal(statId);
            }

            if (hideZeroStats && value == 0) {
                continue;
            }

            displayStats.add(new StatEntry(statId, value));
        }

        // Sort: order stats first (by key), then item stats sorted by value descending
        displayStats.sort((a, b) -> {
            boolean aIsOrder = a.statId.startsWith(DeliveryStatisticsModule.PREFIX_ORDER);
            boolean bIsOrder = b.statId.startsWith(DeliveryStatisticsModule.PREFIX_ORDER);

            if (aIsOrder && !bIsOrder) {
                return -1; // Orders first
            }
            if (!aIsOrder && bIsOrder) {
                return 1;
            }
            if (aIsOrder) {
                // Both are orders - sort by key
                return a.statId.compareTo(b.statId);
            }
            // Both are items - sort by value descending
            return Integer.compare(b.value, a.value);
        });

        findPaneOfTypeByID(LIST_STATS, ScrollingList.class).setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return displayStats.size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane) {
                final StatEntry entry = displayStats.get(index);
                final Text descLabel = rowPane.findPaneOfTypeByID("desc", Text.class);
                if (descLabel != null) {
                    Component text = formatStatEntry(entry);
                    descLabel.setText(text);
                    PaneBuilders.tooltipBuilder().hoverPane(descLabel).build().setText(text);
                }
            }
        });

        // Set up the interval dropdown
        final DropDownList intervalDropdown = findPaneOfTypeByID(DROPDOWN_INTERVAL, DropDownList.class);
        if (intervalDropdown != null) {
            intervalDropdown.setHandler(this::onIntervalChanged);

            intervalDropdown.setDataProvider(new DropDownList.DataProvider() {
                @Override
                public int getElementCount() {
                    return INTERVALS.size();
                }

                @Override
                public String getLabel(final int index) {
                    return Component.translatable((String) INTERVALS.keySet().toArray()[index]).getString();
                }
            });

            intervalDropdown.setSelectedIndex(new ArrayList<>(INTERVALS.keySet()).indexOf(selectedInterval));
        }
    }

    /**
     * Handle interval dropdown change.
     *
     * @param dropDownList
     *            the dropdown.
     */
    private void onIntervalChanged(final DropDownList dropDownList) {
        final String newInterval = (String) INTERVALS.keySet().toArray()[dropDownList.getSelectedIndex()];
        if (!newInterval.equals(selectedInterval)) {
            selectedInterval = newInterval;
            updateStats();
        }
    }

    /**
     * Toggle the hide zero stats setting.
     *
     * @param button
     *            the clicked button.
     */
    private void toggleHideZero(final Button button) {
        hideZeroStats = !hideZeroStats;

        final ButtonImage hideButton = findPaneOfTypeByID(BUTTON_HIDE_ZERO, ButtonImage.class);
        if (hideButton != null) {
            if (hideZeroStats) {
                hideButton.setImage(TEXTURE_CHECK_ON, true);
            } else {
                hideButton.setImage(TEXTURE_CHECK_OFF, true);
            }
        }

        updateStats();
    }

    /**
     * Format a stat entry for display.
     *
     * @param entry
     *            the stat entry.
     * @return the formatted component.
     */
    @SuppressWarnings("removal")
    private Component formatStatEntry(StatEntry entry) {
        String statId = entry.statId();
        int value = entry.value();

        if (statId.startsWith(DeliveryStatisticsModule.PREFIX_ORDER)) {
            // Order stat - use translation key
            String orderType = statId.substring(DeliveryStatisticsModule.PREFIX_ORDER.length());
            return Component.translatable("com.supplylines.stats.order." + orderType, value);
        } else if (statId.startsWith(DeliveryStatisticsModule.PREFIX_ITEM)) {
            // Item stat - look up item name from registry
            String itemId = statId.substring(DeliveryStatisticsModule.PREFIX_ITEM.length());
            ResourceLocation itemKey = new ResourceLocation(itemId);
            Item item = ForgeRegistries.ITEMS.getValue(itemKey);
            if (item != null) {
                Component itemName = item.getDescription();
                return Component.translatable("com.supplylines.stats.item", itemName, value);
            } else {
                // Fallback if item not found
                return Component.literal(itemId + ": " + value);
            }
        } else {
            // Unknown stat type - display as-is
            return Component.literal(statId + ": " + value);
        }
    }
}
