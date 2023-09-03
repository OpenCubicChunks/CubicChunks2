package io.github.opencubicchunks.cubicchunks.world.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;

public interface CubicLevelLightEngine {
    void retainData(CubePos cubePos, boolean retain);
}