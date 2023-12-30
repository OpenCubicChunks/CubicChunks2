package io.github.opencubicchunks.cubicchunks.world;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.entity.MobCategory;

public interface CubicLocalMobCapCalculator {
    void addMob(CubePos cube, MobCategory category);

    boolean canSpawn(MobCategory category, CubePos cube);

    boolean isCubic();
}
