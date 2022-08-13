package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.feature.nether;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.ReplaceBlobsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ReplaceBlobsFeature.class)
public class MixinReplaceBlobsFeature {

    @Redirect(method = "findTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getMinBuildHeight()I"))
    private static int useCubeMinY(LevelAccessor levelAccessor) {
        if (!((CubicLevelHeightAccessor) levelAccessor).isCubic()) {
            return levelAccessor.getMinBuildHeight();
        }
        return Coords.cubeToMinBlock(((CubeWorldGenRegion) levelAccessor).getMainCubeY());
    }
}
