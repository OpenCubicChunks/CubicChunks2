package io.github.opencubicchunks.cubicchunks.testutils;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;

public class Misc {
    public static int chebyshevDistance(Vec3i a, Vec3i b) {
        return Math.max(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())), Math.abs(a.getZ() - b.getZ()));
    }
    public static int chebyshevDistance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }
}
