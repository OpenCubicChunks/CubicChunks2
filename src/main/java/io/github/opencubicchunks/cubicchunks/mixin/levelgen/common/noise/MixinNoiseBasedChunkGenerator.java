package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.noise;

import io.github.opencubicchunks.cubicchunks.levelgen.CubicNoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator implements CubicNoiseBasedChunkGenerator {
    private boolean isCubic = false;

    @Override
    public void setCubic() {
        this.isCubic = true;
    }
}
