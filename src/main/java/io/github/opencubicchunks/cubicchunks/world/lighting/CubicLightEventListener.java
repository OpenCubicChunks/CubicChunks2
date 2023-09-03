package io.github.opencubicchunks.cubicchunks.world.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;

public interface CubicLightEventListener {
    void setLightEnabled(CubePos cubePos, boolean enable);

    void propagateLightSources(CubePos cubePos);
}
