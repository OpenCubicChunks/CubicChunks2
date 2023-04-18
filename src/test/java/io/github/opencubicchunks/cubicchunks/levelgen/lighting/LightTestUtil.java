package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightSlice.createXZLightSlices;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mock.TestBlockGetter;
import io.github.opencubicchunks.cubicchunks.utils.Result;
import io.github.opencubicchunks.cubicchunks.utils.Vector2i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.lighting.LayerLightEngine;

public class LightTestUtil {
    public static Result<Void, StringBuilder> validateBlockLighting(LayerLightEngine<?, ?> lightEngine, TestBlockGetter blockGetter,
                                             Set<SectionPos> sectionsPresent, Map<BlockPos, Integer> lights) throws AssertionError {
        for (SectionPos sectionPos : sectionsPresent) {
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
            for (int x = sectionPos.minBlockX(), maxX = x + SectionPos.SECTION_SIZE; x < maxX; x++) {
                for (int y = sectionPos.minBlockY(), maxY = y + SectionPos.SECTION_SIZE; y < maxY; y++) {
                    for (int z = sectionPos.minBlockZ(), maxZ = z + SectionPos.SECTION_SIZE; z < maxZ; z++) {
                        int light = lightEngine.getLightValue(blockPos.set(x, y, z));
                        Integer sourceLight = lights.get(blockPos);
                        if (sourceLight != null) {
                            if (sourceLight != light) {
                                StringBuilder sb = createXZLightSlices(lightEngine, blockGetter,
                                    x, y, z,
                                    sectionPos.minBlockX(), maxX,
                                    sectionPos.minBlockY(), maxY,
                                    sectionPos.minBlockZ(), maxZ);
                                sb.append(String.format("Light sources wrong! (%d, %d, %d)", x, y, z));
                                return Result.err(sb);
                            }
                            // Light is source, so we can skip other validation
                            continue;
                        }

                        Result<Void, StringBuilder> result = validateLight(lightEngine, blockGetter, sectionsPresent, sectionPos, blockPos, x, maxX, y, maxY, z, maxZ, light, -0xFFFFFFFF);
                        if (result.isErr()) {
                            return result;
                        }
                    }
                }
            }
        }
        return Result.ok(null);
    }

    public static Result<Void, StringBuilder> validateSkyLighting(LayerLightEngine<?, ?> lightEngine, TestBlockGetter blockGetter, Set<SectionPos> sectionsPresent,
                                           Map<Vector2i, SortedArraySet<Integer>> heightMap) throws AssertionError {
        for (SectionPos sectionPos : sectionsPresent) {
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
            for (int x = sectionPos.minBlockX(), maxX = x + SectionPos.SECTION_SIZE; x < maxX; x++) {
                for (int y = sectionPos.minBlockY(), maxY = y + SectionPos.SECTION_SIZE; y < maxY; y++) {
                    for (int z = sectionPos.minBlockZ(), maxZ = z + SectionPos.SECTION_SIZE; z < maxZ; z++) {
                        int light = lightEngine.getLightValue(blockPos.set(x, y, z));
                        Integer height = heightMap.get(new Vector2i(x, z)).first();
                        //noinspection ConstantValue
                        if (height == null)
                            height = Integer.MIN_VALUE;
                        if (y >= height) {
                            if (15 != light) {
                                StringBuilder sb = createXZLightSlices(lightEngine, blockGetter,
                                    x, y, z,
                                    sectionPos.minBlockX(), maxX,
                                    sectionPos.minBlockY(), maxY,
                                    sectionPos.minBlockZ(), maxZ
                                );
                                sb.append(String.format("Block above heightmap wrong! (%d, %d, %d)", x, y, z));
                                return Result.err(sb);
                            }
                            // Light is source, so we can skip other validation
                            continue;
                        }

                        Result<Void, StringBuilder> result = validateLight(lightEngine, blockGetter, sectionsPresent, sectionPos, blockPos, x, maxX, y, maxY, z, maxZ, light, height);
                        if (result.isErr()) {
                            return result;
                        }
                    }
                }
            }
        }
        return Result.ok(null);
    }

    private static Result<Void, StringBuilder> validateLight(LayerLightEngine<?, ?> lightEngine, TestBlockGetter blockGetter, Set<SectionPos> sectionsPresent, SectionPos sectionPos,
                                      BlockPos.MutableBlockPos blockPos, int x, int maxX, int y, int maxY, int z, int maxZ, int light, int height) {
        // TODO: handle voxel shape occlusion
        Result<Boolean, Void> occludedOrError = validateOccluded(blockGetter, blockPos, light);
        if (occludedOrError.isErr()) {
            StringBuilder sb = createXZLightSlices(lightEngine, blockGetter,
                x, y, z,
                sectionPos.minBlockX(), maxX,
                sectionPos.minBlockY(), maxY,
                sectionPos.minBlockZ(), maxZ
            );
            sb.append(String.format("Occluding block has light! (%d, %d, %d) | Heightmap: %d", x, y, z, height));
            return Result.err(sb);
        } else {
            if (occludedOrError.asOk()) {
                return Result.ok(null);
            }
        }

        Optional<String> error = validateNeighbors(lightEngine, sectionsPresent, blockPos, x, y, z, light, height);
        if (error.isPresent()) {
            StringBuilder sb = createXZLightSlices(lightEngine, blockGetter,
                x, y, z,
                sectionPos.minBlockX(), maxX,
                sectionPos.minBlockY(), height,
                sectionPos.minBlockZ(), maxZ
            );
            sb.append(error.get());
            return Result.err(sb);
        }
        return Result.ok(null);
    }

    private static Result<Boolean, Void> validateOccluded(BlockGetter blockGetter, BlockPos.MutableBlockPos blockPos, int light) {
        if (blockGetter.getBlockState(blockPos).canOcclude()) {
            if (0 != light) {
                return Result.err(null);
            }
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    /**
     * @return An optional error message (if there was an error)
     */
    private static Optional<String> validateNeighbors(LayerLightEngine<?, ?> lightEngine, Set<SectionPos> sectionsPresent, BlockPos.MutableBlockPos blockPos, int x, int y, int z,
                                                      int light, int height) {
        blockPos.set(x, y + 1, z);
        int lightAbove = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightAbove = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x, y - 1, z);
        int lightBelow = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightBelow = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x, y, z - 1);
        int lightNorth = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightNorth = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x + 1, y, z);
        int lightEast = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightEast = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x, y, z + 1);
        int lightSouth = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightSouth = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x - 1, y, z);
        int lightWest = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightWest = lightEngine.getLightValue(blockPos);
        }

        int brightestNeighbor = Math.max(Math.max(Math.max(lightAbove, lightBelow), Math.max(lightNorth, lightEast)), Math.max(lightSouth, lightWest));
        int expected = Math.max(brightestNeighbor - 1, 0);
        if (expected != light) {
            return Optional.of(formatPropagationError(x, y, z, expected, light, lightAbove, lightBelow, lightNorth, lightSouth, lightEast, lightWest, height));
        }
        return Optional.empty();
    }

    /**
     * This exists because having 9 final variables every time it's called would be dumb.
     * <p><b>Java is dumb.</b></p>
     */
    public static String formatPropagationError(int x, int y, int z,
                                                int expected, int found,
                                                int lightAbove, int lightBelow, int lightNorth, int lightSouth, int lightEast, int lightWest,
                                                int height) {
        return String.format("Invalid propagation! Expected: %d | Found: %d | Pos: (%d, %d, %d)\n\tAbove: %d | Below: %d\n\tNorth: %d | South: %d\n\tEast: %d | West: %d\n\tHeightmap: %d",
            expected, found, x, y, z, lightAbove, lightBelow, lightNorth, lightSouth, lightEast, lightWest, height);
    }
}
