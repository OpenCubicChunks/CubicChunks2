package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import io.github.opencubicchunks.cc_core.api.CubePos;

public interface CubeHolderLevelChangeListener {
    void onCubeLevelChange(CubePos pos, IntSupplier intSupplier, int i, IntConsumer consumer);
}