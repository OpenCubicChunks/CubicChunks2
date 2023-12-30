package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.Map;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.class)
public interface NoiseChunkAccess {
    @Mutable
    @Accessor("wrapped")
    void setWrapped(Map<DensityFunction, DensityFunction> map);

    @Accessor
    Map<DensityFunction, DensityFunction> getWrapped();
}
