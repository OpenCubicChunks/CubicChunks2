package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import net.minecraft.world.level.chunk.LightChunkGetter;

/**
 * Exists to mock both {@link LightChunkGetter} and {@link LightCubeGetter} at once
 */
public interface LightCubeChunkGetter extends LightChunkGetter, LightCubeGetter {
}
