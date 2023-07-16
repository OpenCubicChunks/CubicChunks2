package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import javax.annotation.Nullable;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LightChunk;

public interface LightCubeGetter {
    @Nullable LightChunk getCubeForLighting(int cubeX, int cubeY, int cubeZ);
}