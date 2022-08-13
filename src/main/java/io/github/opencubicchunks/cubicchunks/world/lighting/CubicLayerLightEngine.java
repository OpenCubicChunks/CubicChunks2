package io.github.opencubicchunks.cubicchunks.world.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;

public interface CubicLayerLightEngine {
    void retainCubeData(CubePos pos, boolean retain);

    void enableLightSources(CubePos cubePos, boolean enable);
}