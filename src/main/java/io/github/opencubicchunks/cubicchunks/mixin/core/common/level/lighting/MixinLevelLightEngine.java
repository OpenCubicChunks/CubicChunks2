package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLevelLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicSkyLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEventListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelLightEngine.class)
public abstract class MixinLevelLightEngine implements CubicLevelLightEngine, LightEventListener, SkyLightColumnChecker {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    @Shadow @Final @Nullable private LightEngine<?, ?> blockEngine;

    @Shadow @Final @Nullable private LightEngine<?, ?> skyEngine;

    // these can't be abstract because they need to be called as super.method()
    @Shadow public void checkBlock(BlockPos pos) {
        throw new Error("Mixin failed to apply correctly");
    }

    @Shadow public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        throw new Error("Mixin failed to apply correctly");
    }

    @Override
    public void retainData(CubePos cubePos, boolean retain) {
        if (this.blockEngine != null) {
            ((CubicLightEngine) this.blockEngine).retainCubeData(cubePos, retain);
        }

        if (this.skyEngine != null) {
            ((CubicLightEngine) this.skyEngine).retainCubeData(cubePos, retain);
        }
    }

    @Override
    public void enableLightSources(CubePos cubePos, boolean retain) {
        if (this.blockEngine != null) {
            ((CubicLightEngine) this.blockEngine).enableLightSources(cubePos, retain);
        }

        if (this.skyEngine != null) {
            ((CubicLightEngine) this.skyEngine).enableLightSources(cubePos, retain);
        }
    }

    protected void doSkyLightForCube(CubeAccess cube) {
        if (this.skyEngine != null) {
            ((CubicSkyLightEngine) this.skyEngine).doSkyLightForCube(cube);
        }
    }

    @Override
    public void checkSkyLightColumn(ColumnCubeMapGetter chunk, int x, int z, int oldHeight, int newHeight) {
        if (this.skyEngine != null) {
            ((SkyLightColumnChecker) skyEngine).checkSkyLightColumn(chunk, x, z, oldHeight, newHeight);
        }
    }
}