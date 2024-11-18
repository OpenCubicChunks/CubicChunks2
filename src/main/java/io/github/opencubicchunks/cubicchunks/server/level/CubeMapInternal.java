package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;

public interface CubeMapInternal {
    SectionPos updatePlayerCubePos(ServerPlayer player);

    void updateCubeTracking(ServerPlayer player, CubePos cubePosIn, Object[] packetCache, boolean wasLoaded, boolean load);

    boolean isExistingCubeFull(CubePos pos);
}
