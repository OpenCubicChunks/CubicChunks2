package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LayerLightEngine;

public class LightSlice {
    private static final String XZ_SLICE_GAP = " ".repeat(10);
    private static final char[] HEX_CHARS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
    public static String createXZLightSlices(LayerLightEngine<?, ?> lightEngine, int errorX, int errorY, int errorZ, int startX, int endX, int startY, int endY, int startZ, int endZ) {
        StringBuilder stringBuilder = new StringBuilder();

        appendHeader(errorX, errorZ, stringBuilder, endX - startX, endZ - startZ);

        appendBody(lightEngine, errorX, errorY, errorZ, startX, endX, startY, endY, startZ, endZ, stringBuilder);

        appendFooter(stringBuilder, startX, endX, startZ, endZ);

        return stringBuilder.toString();
    }

    private static void appendHeader(int errorX, int errorZ, StringBuilder stringBuilder, int xRange, int zRange) {
        stringBuilder.append("\n\n").append("       ")
            .append(String.format("%-" + (3 * xRange) + "s", "XY Slice " + errorZ))
            .append(XZ_SLICE_GAP)
            .append(String.format("%-" + (3 * zRange) + "s", "ZY Slice " + errorX))
            .append("\n");
    }

    private static void appendBody(LayerLightEngine<?, ?> lightEngine, int errorX, int errorY, int errorZ, int startX, int endX, int startY, int endY, int startZ, int endZ,
                                  StringBuilder stringBuilder) {
        for (int y = endY - 1; y >= startY; y--) {
            stringBuilder.append(String.format("%2d:   ", y));
            appendRow(stringBuilder, y, startX, endX, errorX, errorY, (row, col) -> lightEngine.getLightValue(new BlockPos(row, col, errorZ)));

            stringBuilder.append(XZ_SLICE_GAP);
            appendRow(stringBuilder, y, startZ, endZ, errorZ, errorY, (row, col) -> lightEngine.getLightValue(new BlockPos(errorX, col, row)));

            stringBuilder.append("\n");
        }
    }

    private static void appendRow(StringBuilder stringBuilder, int y, int start, int end, int errorRow, int errorColumn,
                                  BiFunction<Integer, Integer, Integer> rowColToLightValue) {
        for (int i = start; i < end; i++) {
            stringBuilder.append((i == errorRow && y == errorColumn) ? ">" : " ");
            stringBuilder.append(HEX_CHARS[rowColToLightValue.apply(i, y)]);
            stringBuilder.append((i == errorRow && y == errorColumn) ? "<" : " ");
        }
    }

    private static void appendFooter(StringBuilder stringBuilder, int startX, int endX, int startZ, int endZ) {
        stringBuilder
            .append("\n      ")
            .append(" ^ ".repeat(Math.max(0, endX - startX)))
            .append(XZ_SLICE_GAP)
            .append(" ^ ".repeat(Math.max(0, endZ - startZ)))
            .append("\n      ");

        for (int x = startX; x < endX; x++) {
            stringBuilder.append(String.format("%2d ", x));
        }
        stringBuilder.append(XZ_SLICE_GAP);
        for (int z = startZ; z < endZ; z++) {
            stringBuilder.append(String.format("%2d ", z));
        }
        stringBuilder.append("\n");
    }
}
