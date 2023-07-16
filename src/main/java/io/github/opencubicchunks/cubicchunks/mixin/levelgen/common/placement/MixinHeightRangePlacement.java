package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.placement;

import java.util.OptionalInt;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.levelgen.placement.CubicHeightProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeightRangePlacement.class)
public class MixinHeightRangePlacement {
    @Shadow @Final private HeightProvider height;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void handleCubicRangeDecorator(PlacementContext placementContext, RandomSource random, BlockPos blockPos,
                                           CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!((CubicLevelHeightAccessor) placementContext.getLevel()).isCubic()) {
            return;
        }
        CubeWorldGenRegion level = (CubeWorldGenRegion) placementContext.getLevel();
        HeightProvider heightProvider = this.height;

        if (heightProvider instanceof CubicHeightProvider) {
            OptionalInt optionalInt = ((CubicHeightProvider) heightProvider)
                .sampleCubic(random, placementContext, Coords.cubeToMinBlock(level.getMainCubeY()), Coords.cubeToMaxBlock(level.getMainCubeY()));
            cir.setReturnValue(optionalInt.stream().mapToObj(blockPos::atY));
        } else {
            int sample = this.height.sample(random, placementContext);
            if (level.insideCubeHeight(sample)) {
                cir.setReturnValue(Stream.of(blockPos.atY(sample)));
            } else {
                cir.setReturnValue(Stream.empty());
            }
        }
    }
}
