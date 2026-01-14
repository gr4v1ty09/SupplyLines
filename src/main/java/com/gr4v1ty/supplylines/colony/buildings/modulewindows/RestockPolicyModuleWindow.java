package com.gr4v1ty.supplylines.colony.buildings.modulewindows;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.buildings.modules.RestockPolicyModule;
import com.gr4v1ty.supplylines.colony.buildings.moduleviews.RestockPolicyModuleView;
import com.gr4v1ty.supplylines.network.ModNetwork;
import com.gr4v1ty.supplylines.network.messages.AddRestockPolicyMessage;
import com.gr4v1ty.supplylines.network.messages.RemoveRestockPolicyMessage;
import com.ldtteam.blockui.Pane;
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
import net.minecraft.world.item.ItemStack;

/**
 * Window for managing restock policies in the Stock Keeper hut. Allows defining
 * which items should be kept stocked and their target quantities.
 */
public class RestockPolicyModuleWindow extends AbstractModuleWindow {
    /** Resource path for the layout. */
    private static final String RESOURCE = SupplyLines.MOD_ID + ":gui/layouthuts/layoutrestockpolicy.xml";

    /** Label for when the policy limit is reached. */
    private static final String LABEL_LIMIT_REACHED = "com.supplylines.gui.stockkeeper.restockpolicy.limitreached";

    /** Resource ID for the scrolling list of policies. */
    private static final String LIST_POLICIES = "policies";

    /** Resource ID for item name label. */
    private static final String LABEL_ITEM_NAME = "itemname";

    /** Resource ID for target quantity label. */
    private static final String LABEL_TARGET = "target";

    /** Resource ID for current stock label. */
    private static final String LABEL_CURRENT = "current";

    /** Resource ID for item icon. */
    private static final String ICON_ITEM = "itemicon";

    /** Button ID for adding a policy. */
    private static final String BUTTON_ADD = "add";

    /** Button ID for removing a policy. */
    private static final String BUTTON_REMOVE = "remove";

    /** The module view. */
    private final RestockPolicyModuleView moduleView;

    /** The scrolling list of policies. */
    private final ScrollingList policyList;

    /**
     * Constructor for the restock policy window.
     *
     * @param moduleView
     *            the module view.
     */
    public RestockPolicyModuleWindow(final RestockPolicyModuleView moduleView) {
        super(moduleView.getBuildingView(), RESOURCE);
        this.moduleView = moduleView;

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
                stack.setCount(stack.getMaxStackSize());

                final Text nameLabel = rowPane.findPaneOfTypeByID(LABEL_ITEM_NAME, Text.class);
                if (nameLabel != null) {
                    nameLabel.setText(stack.getHoverName());
                }

                final Text targetLabel = rowPane.findPaneOfTypeByID(LABEL_TARGET, Text.class);
                if (targetLabel != null) {
                    targetLabel.setText(Component.literal(String.valueOf(entry.getTargetQuantity())));
                }

                final Text currentLabel = rowPane.findPaneOfTypeByID(LABEL_CURRENT, Text.class);
                if (currentLabel != null) {
                    // TODO: Get actual current stock level from building
                    currentLabel.setText(Component.literal("?"));
                }

                final ItemIcon icon = rowPane.findPaneOfTypeByID(ICON_ITEM, ItemIcon.class);
                if (icon != null) {
                    icon.setItem(stack);
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
}
