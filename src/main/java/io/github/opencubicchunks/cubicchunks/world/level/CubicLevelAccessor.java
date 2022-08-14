package io.github.opencubicchunks.cubicchunks.world.level;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface CubicLevelAccessor extends CubicLevelHeightAccessor {
    CubeAccess getCube(int cubeX, int cubeY, int cubeZ);

    default CubeAccess getCube(CubePos cubePos) {
        return getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ());
    }

    CubeAccess getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status);

    default CubeAccess getCube(CubePos cubePos, ChunkStatus status) {
        return getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), status);
    }

    default CubeAccess getCube(BlockPos pos) {
        return getCube(CubePos.from(pos));
    }

    @Nullable
    default CubeAccess getCube(BlockPos pos, ChunkStatus status, boolean notnull) {
        return getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()), status, notnull);
    }

    @Nullable
    CubeAccess getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status, boolean notnull);
}