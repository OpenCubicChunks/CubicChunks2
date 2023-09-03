package io.github.opencubicchunks.cubicchunks.world.lighting;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cc_core.api.CubePos;

public interface CubicLightEngine {
    void retainCubeData(CubePos pos, boolean retain);

    @VisibleForTesting
    void setCubic();
}