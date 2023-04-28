package io.github.opencubicchunks.cubicchunks.world.lighting;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cc_core.api.CubePos;

public interface CubicLayerLightEngine {
    void retainCubeData(CubePos pos, boolean retain);

    void enableLightSources(CubePos cubePos, boolean enable);

    @VisibleForTesting
    void setCubic();
}