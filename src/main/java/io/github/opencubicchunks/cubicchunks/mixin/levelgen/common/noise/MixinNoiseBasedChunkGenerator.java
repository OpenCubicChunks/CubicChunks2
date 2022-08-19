package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.noise;

import io.github.opencubicchunks.cubicchunks.levelgen.CubicNoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator implements CubicNoiseBasedChunkGenerator {
    private boolean isCubic = false;

    @Override
    public void setCubic() {
        this.isCubic = true;
    }

    @Redirect(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;min(II)I"
        )
    )
    private int returnLevelValueMin(int a, int b) {
        if (this.isCubic) {
            return b;
        } else {
            return Math.min(a, b);
        }
    }

    @Redirect(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;max(II)I"
        )
    )
    private int returnLevelValueMax(int a, int b) {
        if (this.isCubic) {
            return b;
        } else {
            return Math.max(a, b);
        }
    }
}
