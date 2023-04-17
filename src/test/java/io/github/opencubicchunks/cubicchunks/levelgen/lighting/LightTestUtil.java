package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightSlice.createXZLightSlices;
import static org.junit.jupiter.api.Assertions.fail;

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
    public static void validateBlockLighting(LayerLightEngine<?, ?> lightEngine, TestBlockGetter blockGetter,
                                             Set<SectionPos> sectionsPresent, Map<BlockPos, Integer> lights) throws AssertionError {
        sectionsPresent.forEach(sectionPos -> {
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
            for (int x = sectionPos.minBlockX(), maxX = x + SectionPos.SECTION_SIZE; x < maxX; x++) {
                for (int y = sectionPos.minBlockY(), maxY = y + SectionPos.SECTION_SIZE; y < maxY; y++) {
                    for (int z = sectionPos.minBlockZ(), maxZ = z + SectionPos.SECTION_SIZE; z < maxZ; z++) {
                        int light = lightEngine.getLightValue(blockPos.set(x, y, z));
                        Integer sourceLight = lights.get(blockPos);
                        if (sourceLight != null) {
                            if (sourceLight != light) {
                                System.err.println(createXZLightSlices(lightEngine, blockGetter,
                                    x, y, z,
                                    sectionPos.minBlockX(), maxX,
                                    sectionPos.minBlockY(), maxY,
                                    sectionPos.minBlockZ(), maxZ)
                                );
                                fail(String.format("Light sources wrong! (%d, %d, %d)", x, y, z));
                            }
                            // Light is source, so we can skip other validation
                            continue;
                        }

                        validateLight(lightEngine, blockGetter, sectionsPresent, sectionPos, blockPos, x, maxX, y, maxY, z, maxZ, light);
                    }
                }
            }
        });
    }

    public static void validateSkyLighting(LayerLightEngine<?, ?> lightEngine, TestBlockGetter blockGetter, Set<SectionPos> sectionsPresent,
                                           Map<Vector2i, SortedArraySet<Integer>> heightMap) throws AssertionError {
        sectionsPresent.forEach(sectionPos -> {
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
            for (int x = sectionPos.minBlockX(), maxX = x + SectionPos.SECTION_SIZE; x < maxX; x++) {
                for (int y = sectionPos.minBlockY(), maxY = y + SectionPos.SECTION_SIZE; y < maxY; y++) {
                    for (int z = sectionPos.minBlockZ(), maxZ = z + SectionPos.SECTION_SIZE; z < maxZ; z++) {
                        int light = lightEngine.getLightValue(blockPos.set(x, y, z));
                        Integer height = heightMap.get(new Vector2i(x, z)).first();
                        //noinspection ConstantValue
                        if (height == null) height = Integer.MIN_VALUE;
                        if (y >= height) {
                            if (15 != light) {
                                System.err.println(createXZLightSlices(lightEngine,
                                    blockGetter,
                                    x, y, z,
                                    sectionPos.minBlockX(), maxX,
                                    sectionPos.minBlockY(), maxY,
                                    sectionPos.minBlockZ(), maxZ)
                                );
                                fail(String.format("Block above heightmap wrong! (%d, %d, %d)", x, y, z));
                            }
                            // Light is source, so we can skip other validation
                            continue;
                        }

                        validateLight(lightEngine, blockGetter, sectionsPresent, sectionPos, blockPos, x, maxX, y, maxY, z, maxZ, light);
                    }
                }
            }
        });
    }

    private static void validateLight(LayerLightEngine<?, ?> lightEngine, TestBlockGetter blockGetter, Set<SectionPos> sectionsPresent, SectionPos sectionPos,
                                      BlockPos.MutableBlockPos blockPos, int x, int maxX, int y, int maxY, int z, int maxZ, int light) {
        // TODO: handle voxel shape occlusion
        Result<Boolean, Void> occludedOrError = validateOccluded(blockGetter, blockPos, light);
        if (occludedOrError.isErr()) {
            System.err.println(createXZLightSlices(lightEngine,
                blockGetter,
                x, y, z,
                sectionPos.minBlockX(), maxX,
                sectionPos.minBlockY(), maxY,
                sectionPos.minBlockZ(), maxZ)
            );
            fail(String.format("Occluding block has light! (%d, %d, %d)", x, y, z));
        } else {
            if (occludedOrError.asOk()) {
                return;
            }
        }

        Optional<String> error = validateNeighbors(lightEngine, sectionsPresent, blockPos, x, y, z, light);
        if (error.isPresent()) {
            System.err.println(createXZLightSlices(lightEngine,
                blockGetter,
                x, y, z,
                sectionPos.minBlockX(), maxX,
                sectionPos.minBlockY(), maxY,
                sectionPos.minBlockZ(), maxZ)
            );
            fail(error.get());
        }
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
                                                      int light) {
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
        if (Math.max(brightestNeighbor - 1, 0) != light) {
            return Optional.of(formatPropagationError(x, y, z, lightAbove, lightBelow, lightNorth, lightSouth, lightEast, lightWest));
        }
        return Optional.empty();
    }

    /**
     * This exists because having 9 final variables every time it's called would be dumb.
     * <p><b>Java is dumb.</b></p>
     */
    public static String formatPropagationError(int x, int y, int z, int lightAbove, int lightBelow, int lightNorth, int lightSouth, int lightEast, int lightWest) {
        return String.format("Propagation wrong for pos: (%d, %d, %d)!\n\tAbove: %d | Below: %d\n\tNorth: %d | South: %d\n\tEast: %d | West: %d",
            x, y, z, lightAbove, lightBelow, lightNorth, lightSouth, lightEast, lightWest);
    }
}
