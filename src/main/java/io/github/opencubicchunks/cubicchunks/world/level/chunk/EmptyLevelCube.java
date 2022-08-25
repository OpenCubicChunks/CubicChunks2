package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.Level;

public class EmptyLevelCube extends LevelCube {
    public EmptyLevelCube(Level worldIn) {
        super(worldIn, CubePos.of(0, 0, 0));
    }
}