package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LayerLightSectionStorage.class)
public abstract class MixinLayerLightSectionStorage<M extends DataLayerStorageMap<M>> {
    @Shadow @Final protected LightChunkGetter chunkSource;

    // FIXME (1.20) this breaks - probably because cubes aren't getting properly marked as lit?
//    @Redirect(method = { "markNewInconsistencies", "lightOnInSection" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;getZeroNode(J)J"))
//    protected long useCubeSectionPos(long pos) {
//        if (this.chunkSource.getLevel() == null || !((CubicLevelHeightAccessor) this.chunkSource.getLevel()).isCubic()) {
//            return SectionPos.getZeroNode(pos);
//        }
//        return CubePos.sectionToCubeSectionLong(pos);
//    }
}
