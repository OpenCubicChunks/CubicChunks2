package io.github.opencubicchunks.cubicchunks.chunk.entity;

import io.github.opencubicchunks.cc_core.api.CubePos;

public interface ChunkEntityStateEventHandler {
    void onCubeEntitiesLoad(CubePos pos);

    void onCubeEntitiesUnload(CubePos pos);
}
