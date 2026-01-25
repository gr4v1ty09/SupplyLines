package com.gr4v1ty.supplylines.colony.buildings.modulewindows;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.gr4v1ty.supplylines.colony.buildings.moduleviews.RestockPolicyModuleView;
import com.gr4v1ty.supplylines.network.ModNetwork;
import com.gr4v1ty.supplylines.network.messages.AddRestockPolicyMessage;
import com.gr4v1ty.supplylines.network.messages.RemoveRestockPolicyMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Window for managing restock policies in the Stock Keeper hut. Allows defining
 * which items should be kept stocked and their target quantities.
 */
public class RestockPolicyModuleWindow extends AbstractModuleWindow<RestockPolicyModuleView> {
    /** Resource path for the layout. */
    @SuppressWarnings("removal")
    private static final ResourceLocation RESOURCE = new ResourceLocation(SupplyLines.MOD_ID,
            "gui/layouthuts/layoutrestockpolicy.xml");

    /** Label for when the policy limit is reached. */
    private static final String LABEL_LIMIT_REACHED = "com.supplylines.gui.stockkeeper.restockpolicy.limitreached";

    /** Resource ID for the scrolling list of policies. */
    private static final String LIST_POLICIES = "policies";

    /** Resource ID for stock label (hut vault) with color coding. */
    private static final String LABEL_STOCK = "stock";

    /** Resource ID for remote stock label (sum across suppliers). */
    private static final String LABEL_REMOTE = "remote";

    /** Color for fully stocked (green). */
    private static final int COLOR_STOCKED = 0x00AA00;

    /** Color for can fulfill from remote (orange). */
    private static final int COLOR_CAN_FULFILL = 0xFFAA00;

    /** Color for insufficient stock (red). */
    private static final int COLOR_INSUFFICIENT = 0xAA0000;

    /** Resource ID for item icon. */
    private static final String ICON_ITEM = "itemicon";

    /** Button ID for adding a policy. */
    private static final String BUTTON_ADD = "add";

    /** Button ID for removing a policy. */
    private static final String BUTTON_REMOVE = "remove";

    /** The scrolling list of policies. */
    private final ScrollingList policyList;

    /**
     * Constructor for the restock policy window.
     *
     * @param moduleView
     *            the module view.
     */
    public RestockPolicyModuleWindow(final RestockPolicyModuleView moduleView) {
        super(moduleView, RESOURCE);

        policyList = findPaneOfTypeByID(LIST_POLICIES, ScrollingList.class);

        registerButton(BUTTON_ADD, this::addPolicy);
        registerButton(BUTTON_REMOVE, this::removePolicy);

        // Disable add button if limit reached
        if (moduleView.hasReachedLimit()) {
            final ButtonImage button = findPaneOfTypeByID(BUTTON_ADD, ButtonImage.class);
            if (button != null) {
                button.setText(Component.translatable(LABEL_LIMIT_REACHED));
                button.disable();
            }
        }
    }

    @Override
    public void onOpened() {
        super.onOpened();
        updatePolicyList();
    }

    /**
     * Updates the policy list display.
     */
    private void updatePolicyList() {
        if (policyList == null) {
            return;
        }

        policyList.enable();
        policyList.show();

        policyList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return moduleView.getPolicies().size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane) {
                final RestockPolicyModule.PolicyEntry entry = moduleView.getPolicies().get(index);
                final ItemStack stack = entry.getItem().getItemStack().copy();
                stack.setCount(entry.getTargetQuantity());

                // Stock level with color coding based on status
                final Text stockLabel = rowPane.findPaneOfTypeByID(LABEL_STOCK, Text.class);
                if (stockLabel != null) {
                    long localStock = moduleView.getLocalStockLevel(entry.getItem());
                    long remoteStock = moduleView.getRemoteStockLevel(entry.getItem());
                    int target = entry.getTargetQuantity();

                    stockLabel.setText(Component.literal(formatCompact(localStock)));

                    // Color based on status
                    if (localStock >= target) {
                        stockLabel.setColors(COLOR_STOCKED);
                    } else if (localStock + remoteStock >= target) {
                        stockLabel.setColors(COLOR_CAN_FULFILL);
                    } else {
                        stockLabel.setColors(COLOR_INSUFFICIENT);
                    }

                    // Tooltip with description
                    PaneBuilders.tooltipBuilder().hoverPane(stockLabel).build().setText(
                            Component.translatable("com.supplylines.gui.stockkeeper.restockpolicy.header.stock.desc"));
                }

                // Remote stock = sum across all suppliers
                final Text remoteLabel = rowPane.findPaneOfTypeByID(LABEL_REMOTE, Text.class);
                if (remoteLabel != null) {
                    long remoteStock = moduleView.getRemoteStockLevel(entry.getItem());
                    remoteLabel.setText(Component.literal(formatCompact(remoteStock)));

                    // Tooltip with description
                    PaneBuilders.tooltipBuilder().hoverPane(remoteLabel).build().setText(
                            Component.translatable("com.supplylines.gui.stockkeeper.restockpolicy.header.remote.desc"));
                }

                final ItemIcon icon = rowPane.findPaneOfTypeByID(ICON_ITEM, ItemIcon.class);
                if (icon != null) {
                    icon.setItem(stack);

                    // Tooltip with item name and target quantity
                    PaneBuilders.tooltipBuilder().hoverPane(icon).build()
                            .setText(Component.translatable("com.supplylines.gui.stockkeeper.restockpolicy.item.desc",
                                    entry.getItem().getItemStack().getHoverName(), entry.getTargetQuantity()));
                }
            }
        });
    }

    /**
     * Handle add policy button click. Opens an item selector window to choose an
     * item and quantity.
     */
    private void addPolicy() {
        if (!moduleView.hasReachedLimit()) {
            new WindowSelectRes(this.window, Component.empty(), null,
                    IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems(), (stack, qty) -> {
                        ModNetwork.sendToServer(new AddRestockPolicyMessage(buildingView, stack, qty));
                        // Optimistic update - add to local view for immediate feedback
                        moduleView.addPolicy(new RestockPolicyModule.PolicyEntry(new ItemStorage(stack), qty));
                        updatePolicyList();
                    }, true, Component.translatable("com.supplylines.gui.stockkeeper.restockpolicy.selectquantity"))
                    .open();
        }
    }

    /**
     * Handle remove policy button click.
     *
     * @param button
     *            the clicked button.
     */
    private void removePolicy(final Button button) {
        final int row = policyList.getListElementIndexByPane(button);
        if (row >= 0 && row < moduleView.getPolicies().size()) {
            final RestockPolicyModule.PolicyEntry entry = moduleView.getPolicies().get(row);
            ModNetwork.sendToServer(new RemoveRestockPolicyMessage(buildingView, entry.getItem().getItemStack()));
            moduleView.getPolicies().remove(row);
            updatePolicyList();
        }
    }

    /**
     * Format a number in compact form (e.g., 1.2K, 35K, 1.5M).
     *
     * @param value
     *            the value to format.
     * @return the formatted string.
     */
    private static String formatCompact(long value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
}
