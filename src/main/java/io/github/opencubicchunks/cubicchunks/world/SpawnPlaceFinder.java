package io.github.opencubicchunks.cubicchunks.world;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.annotation.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class SpawnPlaceFinder {

    private static final int MIN_FREE_SPACE_SPAWN = 32;

    private SpawnPlaceFinder() {
        throw new Error();
    }

    @Nullable
    public static BlockPos getTopBlockBisect(BlockGetter level, BlockPos pos, boolean checkValid) {
        BlockPos minPos, maxPos;
        if (findNonEmpty(level, pos) == null) {
            CubicChunks.LOGGER.debug("Starting bisect with empty space at init {}", pos);
            maxPos = pos;
            minPos = findMinPos(level, pos);
        } else {
            CubicChunks.LOGGER.debug("Starting bisect without empty space at init {}", pos);
            minPos = pos;
            maxPos = findMaxPos(level, pos);
        }
        CubicChunks.LOGGER.debug("Found minPos {} and maxPos {}", minPos, maxPos);
        if (minPos == null || maxPos == null) {
            CubicChunks.LOGGER.error("No suitable spawn found, using original input {} (min={}, max={})", pos, minPos, maxPos);
            return null;
        }
        assert findNonEmpty(level, maxPos) == null && findNonEmpty(level, minPos) != null;
        BlockPos groundPos = bisect(level, minPos.below(MIN_FREE_SPACE_SPAWN), maxPos.above(MIN_FREE_SPACE_SPAWN));
        if (checkValid && !level.getBlockState(groundPos).is(BlockTags.VALID_SPAWN)) {
            return null;
        }
        return groundPos.above();
    }

    private static BlockPos bisect(BlockGetter level, BlockPos min, BlockPos max) {
        while (min.getY() < max.getY() - 1) {
            CubicChunks.LOGGER.debug("Bisect step with min={}, max={}", min, max);
            BlockPos middle = middleY(min, max);
            if (findNonEmpty(level, middle) != null) {
                // middle has solid space, so it can be used as new minimum
                min = middle;
            } else {
                // middle is empty, so can be used as new maximum
                max = middle;
            }
        }
        // now max should contain the all-empty part, but min should still have filled part.
        return min;
    }

    private static BlockPos middleY(BlockPos min, BlockPos max) {
        return new BlockPos(min.getX(), (int) ((min.getY() + (long) max.getY()) >> 1), min.getZ());
    }

    @Nullable
    private static BlockPos findMinPos(BlockGetter level, BlockPos pos) {
        // go down twice as much each time until we hit filled space
        double dy = 16;
        while (findNonEmpty(level, inWorldUp(level, pos, -dy)) == null) {
            if (dy > Integer.MAX_VALUE) {
                CubicChunks.LOGGER.debug("Error finding spawn point: can't find solid start height at {}", pos);
                return null;
            }
            dy *= 2;
        }
        return inWorldUp(level, pos, -dy);
    }

    @Nullable
    private static BlockPos findMaxPos(BlockGetter level, BlockPos pos) {
        // go up twice as much each time until we hit empty space
        double dy = 16;
        while (findNonEmpty(level, inWorldUp(level, pos, dy)) != null) {
            if (dy > Integer.MAX_VALUE) {
                CubicChunks.LOGGER.debug("Error finding spawn point: can't find non-solid end height at {}", pos);
                return null;
            }
            dy *= 2;
        }
        return inWorldUp(level, pos, dy);
    }

    @Nullable
    private static BlockPos findNonEmpty(BlockGetter level, BlockPos pos) {
        for (int i = 0; i < MIN_FREE_SPACE_SPAWN; i++, pos = pos.above()) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos inWorldUp(LevelHeightAccessor level, BlockPos original, double up) {
        int y = (int) (original.getY() + up);
        y = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight());
        return new BlockPos(original.getX(), y, original.getZ());
    }
}