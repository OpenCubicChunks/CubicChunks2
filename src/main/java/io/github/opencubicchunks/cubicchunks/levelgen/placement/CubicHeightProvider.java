package io.github.opencubicchunks.cubicchunks.levelgen.placement;

import java.util.OptionalInt;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public interface CubicHeightProvider {

    OptionalInt sampleCubic(RandomSource random, WorldGenerationContext context, int minY, int maxY);
}
