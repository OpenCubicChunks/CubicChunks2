package io.github.opencubicchunks.cubicchunks.server.level;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;

public interface CubeHolderLevelChangeListener {
    void onCloLevelChange(CloPos pos, IntSupplier intSupplier, int i, IntConsumer consumer);
}