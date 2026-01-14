package com.gr4v1ty.supplylines.colony.buildings.modulewindows;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.buildings.modules.SuppliersModule;
import com.gr4v1ty.supplylines.colony.buildings.moduleviews.SuppliersModuleView;
import com.gr4v1ty.supplylines.compat.create.CreateNetworkHelper;
import com.gr4v1ty.supplylines.network.ModNetwork;
import com.gr4v1ty.supplylines.network.messages.GiveScepterMessage;
import com.gr4v1ty.supplylines.network.messages.RemoveSupplierMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierAddressMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierLabelMessage;
import com.gr4v1ty.supplylines.network.messages.SetSupplierPriorityMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Window for managing supplier networks in the Stock Keeper hut. Allows
 * adding/removing remote Create stock networks and setting priorities.
 */
public class SuppliersModuleWindow extends AbstractModuleWindow {
    /** Resource path for the layout. */
    private static final String RESOURCE = SupplyLines.MOD_ID + ":gui/layouthuts/layoutsuppliers.xml";

    /** Resource ID for the scrolling list of suppliers. */
    private static final String LIST_SUPPLIERS = "suppliers";

    /** Resource ID for supplier network ID label. */
    private static final String LABEL_NETWORK_ID = "networkid";

    /** Resource ID for supplier priority label. */
    private static final String LABEL_PRIORITY = "priority";

    /** Resource ID for supplier status label. */
    private static final String LABEL_STATUS = "status";

    /** Resource ID for supplier address field. */
    private static final String FIELD_ADDRESS = "address";

    /** Resource ID for supplier label field. */
    private static final String FIELD_LABEL = "label";

    /** Button ID for linking a stock network. */
    private static final String BUTTON_LINK_NETWORK = "linknetwork";

    /** Button ID for removing a supplier. */
    private static final String BUTTON_REMOVE = "remove";

    /** Button ID for moving supplier up in priority. */
    private static final String BUTTON_UP = "up";

    /** Button ID for moving supplier down in priority. */
    private static final String BUTTON_DOWN = "down";

    /** The module view. */
    private final SuppliersModuleView moduleView;

    /** The scrolling list of suppliers. */
    private final ScrollingList supplierList;

    /**
     * Constructor for the suppliers window.
     *
     * @param moduleView
     *            the module view.
     */
    public SuppliersModuleWindow(final SuppliersModuleView moduleView) {
        super(moduleView.getBuildingView(), RESOURCE);
        this.moduleView = moduleView;

        supplierList = findPaneOfTypeByID(LIST_SUPPLIERS, ScrollingList.class);

        registerButton(BUTTON_LINK_NETWORK, this::linkNetwork);
        registerButton(BUTTON_REMOVE, this::removeSupplier);
        registerButton(BUTTON_UP, this::moveUp);
        registerButton(BUTTON_DOWN, this::moveDown);
    }

    @Override
    public void onOpened() {
        super.onOpened();
        updateSupplierList();
    }

    /**
     * Handle link network button click. Sends a message to the server to give the
     * player the scepter tool.
     *
     * @param button
     *            the clicked button.
     */
    private void linkNetwork(final Button button) {
        ModNetwork.sendToServer(new GiveScepterMessage(buildingView));
        close();
    }

    /**
     * Updates the supplier list display.
     */
    private void updateSupplierList() {
        if (supplierList == null) {
            return;
        }

        supplierList.enable();
        supplierList.show();

        supplierList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return moduleView.getSuppliers().size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane) {
                final SuppliersModule.SupplierEntry entry = moduleView.getSuppliers().get(index);
                final UUID networkId = entry.getNetworkId();

                final Text networkIdLabel = rowPane.findPaneOfTypeByID(LABEL_NETWORK_ID, Text.class);
                if (networkIdLabel != null) {
                    networkIdLabel.setText(Component.literal(CreateNetworkHelper.formatNetworkId(networkId)));
                }

                final Text priorityLabel = rowPane.findPaneOfTypeByID(LABEL_PRIORITY, Text.class);
                if (priorityLabel != null) {
                    priorityLabel.setText(Component.literal("#" + (entry.getPriority() + 1)));
                }

                final TextField labelField = rowPane.findPaneOfTypeByID(FIELD_LABEL, TextField.class);
                if (labelField != null) {
                    final String label = entry.getLabel();
                    labelField.setText(label);

                    // Set handler for when text changes and field loses focus
                    labelField.setHandler(textField -> {
                        final String newLabel = textField.getText();
                        if (!newLabel.equals(entry.getLabel())) {
                            ModNetwork.sendToServer(
                                    new SetSupplierLabelMessage(buildingView, entry.getNetworkId(), newLabel));
                            entry.setLabel(newLabel);
                        }
                    });
                }

                final TextField addressField = rowPane.findPaneOfTypeByID(FIELD_ADDRESS, TextField.class);
                if (addressField != null) {
                    final String address = entry.getRequestAddress();
                    addressField.setText(address);

                    // Set handler for when text changes and field loses focus
                    addressField.setHandler(textField -> {
                        final String newAddress = textField.getText();
                        if (!newAddress.equals(entry.getRequestAddress())) {
                            ModNetwork.sendToServer(
                                    new SetSupplierAddressMessage(buildingView, entry.getNetworkId(), newAddress));
                            entry.setRequestAddress(newAddress);
                        }
                    });
                }

                final Text statusLabel = rowPane.findPaneOfTypeByID(LABEL_STATUS, Text.class);
                if (statusLabel != null) {
                    // TODO: Compute actual network status
                    statusLabel.setText(
                            Component.translatable("com.supplylines.gui.stockkeeper.suppliers.status.unknown"));
                }
            }
        });
    }

    /**
     * Handle remove supplier button click.
     *
     * @param button
     *            the clicked button.
     */
    private void removeSupplier(final Button button) {
        final int row = supplierList.getListElementIndexByPane(button);
        if (row >= 0 && row < moduleView.getSuppliers().size()) {
            final SuppliersModule.SupplierEntry entry = moduleView.getSuppliers().get(row);
            ModNetwork.sendToServer(new RemoveSupplierMessage(buildingView, entry.getNetworkId()));
            moduleView.getSuppliers().remove(row);
            updateSupplierList();
        }
    }

    /**
     * Move supplier up in priority.
     *
     * @param button
     *            the clicked button.
     */
    private void moveUp(final Button button) {
        final int row = supplierList.getListElementIndexByPane(button);
        if (row > 0 && row < moduleView.getSuppliers().size()) {
            final SuppliersModule.SupplierEntry entry = moduleView.getSuppliers().get(row);
            ModNetwork.sendToServer(new SetSupplierPriorityMessage(buildingView, entry.getNetworkId(), row - 1));
            // Swap locally for immediate UI feedback
            final SuppliersModule.SupplierEntry other = moduleView.getSuppliers().get(row - 1);
            moduleView.getSuppliers().set(row - 1, entry);
            moduleView.getSuppliers().set(row, other);
            updateSupplierList();
        }
    }

    /**
     * Move supplier down in priority.
     *
     * @param button
     *            the clicked button.
     */
    private void moveDown(final Button button) {
        final int row = supplierList.getListElementIndexByPane(button);
        if (row >= 0 && row < moduleView.getSuppliers().size() - 1) {
            final SuppliersModule.SupplierEntry entry = moduleView.getSuppliers().get(row);
            ModNetwork.sendToServer(new SetSupplierPriorityMessage(buildingView, entry.getNetworkId(), row + 1));
            // Swap locally for immediate UI feedback
            final SuppliersModule.SupplierEntry other = moduleView.getSuppliers().get(row + 1);
            moduleView.getSuppliers().set(row + 1, entry);
            moduleView.getSuppliers().set(row, other);
            updateSupplierList();
        }
    }

}
