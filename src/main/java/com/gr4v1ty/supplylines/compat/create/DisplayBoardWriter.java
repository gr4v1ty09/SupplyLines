package com.gr4v1ty.supplylines.compat.create;

import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Utility class for writing text to Create mod Display Boards.
 * Message-agnostic: callers control content and formatting.
 */
public final class DisplayBoardWriter {

    private DisplayBoardWriter() {
    }

    /**
     * Get the dimensions and state of a Display Board at the given position.
     *
     * @param level
     *            The world
     * @param pos
     *            Position of any block in the Display Board
     * @return DisplayBoardInfo with line count and max chars per line, or null if
     *         not a valid display
     */
    @Nullable
    public static DisplayBoardInfo getDisplayInfo(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FlapDisplayBlockEntity flapDisplay)) {
            return null;
        }

        FlapDisplayBlockEntity controller = flapDisplay.getController();
        if (controller == null) {
            return null;
        }

        return new DisplayBoardInfo(controller.ySize * 2, controller.getMaxCharCount(),
                controller.isSpeedRequirementFulfilled());
    }

    /**
     * Write lines of text to a Display Board.
     *
     * @param level
     *            The world
     * @param pos
     *            Position of any block in the Display Board
     * @param lines
     *            Text lines to write (extra lines beyond display capacity are
     *            ignored)
     * @return true if successful, false if display not found or not powered
     */
    public static boolean writeLines(Level level, BlockPos pos, List<Component> lines) {
        if (level == null || pos == null || lines == null) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FlapDisplayBlockEntity flapDisplay)) {
            return false;
        }

        FlapDisplayBlockEntity controller = flapDisplay.getController();
        if (controller == null) {
            return false;
        }

        if (!controller.isSpeedRequirementFulfilled()) {
            return false;
        }

        int maxLines = controller.ySize * 2;
        for (int i = 0; i < maxLines; i++) {
            if (i < lines.size()) {
                Component line = lines.get(i);
                String json = Component.Serializer.toJson(line);
                controller.applyTextManually(i, json);
            } else {
                // Clear remaining lines
                controller.applyTextManually(i, null);
            }
        }

        return true;
    }

    /**
     * Clear all lines on a Display Board.
     *
     * @param level
     *            The world
     * @param pos
     *            Position of any block in the Display Board
     * @return true if successful, false if display not found or not powered
     */
    public static boolean clearDisplay(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FlapDisplayBlockEntity flapDisplay)) {
            return false;
        }

        FlapDisplayBlockEntity controller = flapDisplay.getController();
        if (controller == null) {
            return false;
        }

        if (!controller.isSpeedRequirementFulfilled()) {
            return false;
        }

        int maxLines = controller.ySize * 2;
        for (int i = 0; i < maxLines; i++) {
            controller.applyTextManually(i, null);
        }

        return true;
    }
}
