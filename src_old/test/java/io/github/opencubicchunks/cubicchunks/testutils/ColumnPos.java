package io.github.opencubicchunks.cubicchunks.testutils;

import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToCube;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.ChunkPos;

public record ColumnPos(int x, int z) {
    public static ColumnPos from(CubePos pos) {
        return new ColumnPos(pos.getX(), pos.getZ());
    }

    public static ColumnPos from(ChunkPos pos) {
        return new ColumnPos(sectionToCube(pos.x), sectionToCube(pos.z));
    }
}
