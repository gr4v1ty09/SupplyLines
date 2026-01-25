package com.gr4v1ty.supplylines.compat.create;

/**
 * Information about a Create Display Board's dimensions and state.
 *
 * @param lineCount
 *            Total number of text lines available on the display
 * @param maxCharsPerLine
 *            Maximum characters that fit on each line
 * @param isPowered
 *            Whether the display has sufficient rotational power
 */
public record DisplayBoardInfo(int lineCount, int maxCharsPerLine, boolean isPowered) {
}
