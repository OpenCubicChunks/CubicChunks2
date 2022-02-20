package io.github.opencubicchunks.cubicchunks.world.server;

import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;

public interface CubicServerLevel {
    void onCubeUnloading(LevelCube cube);

    void tickCube(LevelCube cube, int randomTicks);

    HeightmapStorage getHeightmapStorage();
}