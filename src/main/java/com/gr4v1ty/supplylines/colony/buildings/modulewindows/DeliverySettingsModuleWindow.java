package com.gr4v1ty.supplylines.colony.buildings.modulewindows;

import com.gr4v1ty.supplylines.SupplyLines;
import com.gr4v1ty.supplylines.colony.buildings.modules.DeliverySettingsModule;
import com.gr4v1ty.supplylines.colony.buildings.moduleviews.DeliverySettingsModuleView;
import com.gr4v1ty.supplylines.network.ModNetwork;
import com.gr4v1ty.supplylines.network.messages.TriggerSettingMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Window for displaying and editing Stock Keeper building settings. Built to
 * mirror SuppliersModuleWindow exactly.
 */
public class DeliverySettingsModuleWindow extends AbstractModuleWindow<DeliverySettingsModuleView> {

    @SuppressWarnings("removal")
    private static final ResourceLocation RESOURCE = new ResourceLocation(SupplyLines.MOD_ID,
            "gui/layouthuts/layoutsettings.xml");

    private static final String LIST_SETTINGS = "settings";
    private static final String LABEL_LABEL = "label";
    private static final String FIELD_INPUT = "input";
    private static final String LABEL_VALUE = "value";
    private static final String BUTTON_TOGGLE = "toggle";

    private final ScrollingList settingsList;
    private final List<SettingEntry> settings = new ArrayList<>();

    /**
     * Local setting entry - mirrors SupplierEntry pattern.
     */
    private static class SettingEntry {
        final String key;
        final String labelKey;
        final boolean isBoolean;
        final int minValue;
        final int maxValue;
        boolean isDouble; // For double values stored as scaled ints (x100)
        String currentText;
        int currentRaw;

        SettingEntry(String key, String labelKey, boolean isBoolean, int minValue, int maxValue) {
            this.key = key;
            this.labelKey = labelKey;
            this.isBoolean = isBoolean;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        void setCurrentText(String text) {
            this.currentText = text;
        }

        String getCurrentText() {
            return currentText;
        }

        void setCurrentRaw(int raw) {
            this.currentRaw = raw;
        }

        int getCurrentRaw() {
            return currentRaw;
        }
    }

    public DeliverySettingsModuleWindow(final DeliverySettingsModuleView moduleView) {
        super(moduleView, RESOURCE);

        settingsList = findPaneOfTypeByID(LIST_SETTINGS, ScrollingList.class);

        // Initialize settings with current values from moduleView
        addBooleanSetting(DeliverySettingsModule.SETTING_SPECULATIVE_ORDERING, moduleView.getRawSpeculativeOrdering());
        addBooleanSetting(DeliverySettingsModule.SETTING_IDLE_WANDER, moduleView.getRawIdleWander());
        addBooleanSetting(DeliverySettingsModule.SETTING_RANDOM_PATROL, moduleView.getRawRandomPatrol());
        addIntegerSetting(DeliverySettingsModule.SETTING_ORDER_EXPIRY_BUFFER, 200, 72000,
                moduleView.getRawOrderExpiryBuffer());
        addIntegerSetting(DeliverySettingsModule.SETTING_SPECULATIVE_DELAY, 200, 12000,
                moduleView.getRawSpeculativeDelay());
        addIntegerSetting(DeliverySettingsModule.SETTING_DEFAULT_DELIVERY, 100, 2400,
                moduleView.getRawDefaultDelivery());
        addIntegerSetting(DeliverySettingsModule.SETTING_STAGING_TIMEOUT, 200, 6000, moduleView.getRawStagingTimeout());

        // AI/Movement settings
        // Doubles stored as scaled ints (x100): 0.5-2.0 -> 50-200, 1.0-16.0 -> 100-1600
        addDoubleSetting(DeliverySettingsModule.SETTING_WALK_SPEED, 50, 200, moduleView.getRawWalkSpeed());
        addDoubleSetting(DeliverySettingsModule.SETTING_ARRIVE_DISTANCE_SQ, 100, 1600,
                moduleView.getRawArriveDistanceSq());
        addIntegerSetting(DeliverySettingsModule.SETTING_INSPECT_DURATION, 1, 20, moduleView.getRawInspectDuration());
        addIntegerSetting(DeliverySettingsModule.SETTING_IDLE_WANDER_CHANCE, 0, 100,
                moduleView.getRawIdleWanderChance());
        addIntegerSetting(DeliverySettingsModule.SETTING_IDLE_WANDER_COOLDOWN, 1, 60,
                moduleView.getRawIdleWanderCooldown());
        addIntegerSetting(DeliverySettingsModule.SETTING_IDLE_INSPECT_DURATION, 1, 10,
                moduleView.getRawIdleInspectDuration());
    }

    private void addBooleanSetting(String key, int rawValue) {
        SettingEntry entry = new SettingEntry(key, "com.supplylines.setting." + key, true, 0, 1);
        entry.setCurrentRaw(rawValue);
        settings.add(entry);
    }

    private void addIntegerSetting(String key, int min, int max, int rawValue) {
        SettingEntry entry = new SettingEntry(key, "com.supplylines.setting." + key, false, min, max);
        entry.setCurrentRaw(rawValue);
        int displayValue = (rawValue == -1) ? getGlobalIntValue(key) : rawValue;
        entry.setCurrentText(String.valueOf(displayValue));
        settings.add(entry);
    }

    /**
     * Adds a double setting (stored as scaled int x100).
     */
    private void addDoubleSetting(String key, int min, int max, int rawValue) {
        SettingEntry entry = new SettingEntry(key, "com.supplylines.setting." + key, false, min, max);
        entry.isDouble = true;
        entry.setCurrentRaw(rawValue);
        double displayValue = (rawValue == -1) ? getGlobalDoubleValue(key) : rawValue / 100.0;
        entry.setCurrentText(String.format("%.2f", displayValue));
        settings.add(entry);
    }

    @Override
    public void onOpened() {
        super.onOpened();
        updateSettingsList();
    }

    private void updateSettingsList() {
        if (settingsList == null) {
            return;
        }

        settingsList.enable();
        settingsList.show();

        settingsList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return settings.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane) {
                final SettingEntry entry = settings.get(index);

                final Text labelText = rowPane.findPaneOfTypeByID(LABEL_LABEL, Text.class);
                if (labelText != null) {
                    labelText.setText(Component.translatable(entry.labelKey));
                    // Add tooltip with description
                    PaneBuilders.tooltipBuilder().hoverPane(labelText).build()
                            .setText(Component.translatable(entry.labelKey + ".desc"));
                }

                final TextField inputField = rowPane.findPaneOfTypeByID(FIELD_INPUT, TextField.class);
                final Text valueText = rowPane.findPaneOfTypeByID(LABEL_VALUE, Text.class);

                if (entry.isBoolean) {
                    // Boolean: hide input, show value text
                    if (inputField != null) {
                        inputField.hide();
                    }
                    if (valueText != null) {
                        valueText.show();
                        int raw = entry.getCurrentRaw();
                        if (raw == -1) {
                            boolean globalVal = getGlobalBooleanValue(entry.key);
                            valueText.setText(Component.translatable("com.supplylines.setting.global",
                                    globalVal
                                            ? Component.translatable("com.supplylines.setting.on")
                                            : Component.translatable("com.supplylines.setting.off")));
                        } else {
                            valueText.setText(raw == 1
                                    ? Component.translatable("com.supplylines.setting.on")
                                    : Component.translatable("com.supplylines.setting.off"));
                        }
                    }
                } else {
                    // Integer or double setting
                    int raw = entry.getCurrentRaw();
                    if (raw == -1) {
                        // Using global: hide input, show "Global: X"
                        if (inputField != null) {
                            inputField.hide();
                        }
                        if (valueText != null) {
                            valueText.show();
                            String globalDisplay;
                            if (entry.isDouble) {
                                globalDisplay = String.format("%.2f", getGlobalDoubleValue(entry.key));
                            } else {
                                globalDisplay = String.valueOf(getGlobalIntValue(entry.key));
                            }
                            valueText.setText(Component.translatable("com.supplylines.setting.global",
                                    Component.literal(globalDisplay)));
                        }
                    } else {
                        // Custom value: show input, hide value text
                        if (valueText != null) {
                            valueText.hide();
                        }
                        if (inputField != null) {
                            inputField.show();
                            inputField.setText(entry.getCurrentText());

                            // Handler for input field
                            inputField.setHandler(textField -> {
                                final String newText = textField.getText();
                                if (!newText.equals(entry.getCurrentText())) {
                                    entry.setCurrentText(newText);
                                    try {
                                        int value;
                                        if (entry.isDouble) {
                                            // Parse as double, convert to scaled int
                                            double parsed = Double.parseDouble(newText.trim());
                                            value = (int) (parsed * 100);
                                        } else {
                                            value = Integer.parseInt(newText.trim());
                                        }
                                        value = Math.max(entry.minValue, Math.min(entry.maxValue, value));
                                        entry.setCurrentRaw(value);
                                        moduleView.setRawValue(entry.key, value);
                                        ModNetwork.sendToServer(
                                                new TriggerSettingMessage(buildingView.getColony().getID(),
                                                        buildingView.getID(), entry.key, value));
                                    } catch (NumberFormatException e) {
                                        // Invalid - ignore
                                    }
                                }
                            });
                        }
                    }
                }

                // Set handler directly on button (registerButton doesn't work inside
                // ScrollingList)
                final Button toggleButton = rowPane.findPaneOfTypeByID(BUTTON_TOGGLE, Button.class);
                if (toggleButton != null) {
                    toggleButton.setHandler(button -> handleToggle(entry));
                }
            }
        });
    }

    private void handleToggle(final SettingEntry entry) {
        int currentRaw = entry.getCurrentRaw();
        int newValue;

        if (entry.isBoolean) {
            // Cycle: -1 (global) -> 1 (on) -> 0 (off) -> -1 (global)
            if (currentRaw == -1) {
                newValue = 1;
            } else if (currentRaw == 1) {
                newValue = 0;
            } else {
                newValue = -1;
            }
        } else if (entry.isDouble) {
            // Toggle override for double: -1 (global) <-> current global value scaled
            if (currentRaw == -1) {
                double globalVal = getGlobalDoubleValue(entry.key);
                newValue = (int) (globalVal * 100);
                entry.setCurrentText(String.format("%.2f", globalVal));
            } else {
                newValue = -1;
                entry.setCurrentText(String.format("%.2f", getGlobalDoubleValue(entry.key)));
            }
        } else {
            // Toggle override for int: -1 (global) <-> current global value
            if (currentRaw == -1) {
                newValue = getGlobalIntValue(entry.key);
                entry.setCurrentText(String.valueOf(newValue));
            } else {
                newValue = -1;
                entry.setCurrentText(String.valueOf(getGlobalIntValue(entry.key)));
            }
        }

        entry.setCurrentRaw(newValue);
        moduleView.setRawValue(entry.key, newValue);
        ModNetwork.sendToServer(
                new TriggerSettingMessage(buildingView.getColony().getID(), buildingView.getID(), entry.key, newValue));
        updateSettingsList();
    }

    private boolean getGlobalBooleanValue(String key) {
        return switch (key) {
            case DeliverySettingsModule.SETTING_SPECULATIVE_ORDERING -> moduleView.getGlobalSpeculativeOrdering();
            case DeliverySettingsModule.SETTING_IDLE_WANDER -> moduleView.getGlobalIdleWander();
            case DeliverySettingsModule.SETTING_RANDOM_PATROL -> moduleView.getGlobalRandomPatrol();
            default -> false;
        };
    }

    private int getGlobalIntValue(String key) {
        return switch (key) {
            case DeliverySettingsModule.SETTING_ORDER_EXPIRY_BUFFER -> moduleView.getGlobalOrderExpiryBuffer();
            case DeliverySettingsModule.SETTING_SPECULATIVE_DELAY -> moduleView.getGlobalSpeculativeDelay();
            case DeliverySettingsModule.SETTING_DEFAULT_DELIVERY -> moduleView.getGlobalDefaultDelivery();
            case DeliverySettingsModule.SETTING_STAGING_TIMEOUT -> moduleView.getGlobalStagingTimeout();
            // AI/Movement integer settings
            case DeliverySettingsModule.SETTING_INSPECT_DURATION -> moduleView.getGlobalInspectDuration();
            case DeliverySettingsModule.SETTING_IDLE_WANDER_CHANCE -> moduleView.getGlobalIdleWanderChance();
            case DeliverySettingsModule.SETTING_IDLE_WANDER_COOLDOWN -> moduleView.getGlobalIdleWanderCooldown();
            case DeliverySettingsModule.SETTING_IDLE_INSPECT_DURATION -> moduleView.getGlobalIdleInspectDuration();
            default -> 0;
        };
    }

    private double getGlobalDoubleValue(String key) {
        return switch (key) {
            case DeliverySettingsModule.SETTING_WALK_SPEED -> moduleView.getGlobalWalkSpeed();
            case DeliverySettingsModule.SETTING_ARRIVE_DISTANCE_SQ -> moduleView.getGlobalArriveDistanceSq();
            default -> 1.0;
        };
    }
}
