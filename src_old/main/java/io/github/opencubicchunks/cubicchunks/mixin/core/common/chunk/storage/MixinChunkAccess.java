package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk.storage;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkAccess.class)
//A lot of these mixins are taken from ether MixinLevelChunk or MixinProtoChunk
public class MixinChunkAccess implements CubicLevelHeightAccessor {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;

    @Shadow @Final protected LevelChunkSection[] sections;
    private boolean isCubic;
    private boolean generates2DChunks;
    private CubicLevelHeightAccessor.WorldStyle worldStyle;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelAccessor, Registry registry, long l, LevelChunkSection[] levelChunkSections,
                        BlendingData blendingData, CallbackInfo ci) {
        isCubic = ((CubicLevelHeightAccessor) levelAccessor).isCubic();
        generates2DChunks = ((CubicLevelHeightAccessor) levelAccessor).generates2DChunks();
        worldStyle = ((CubicLevelHeightAccessor) levelAccessor).worldStyle();
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"))
    private int getFakeSectionsCount(LevelHeightAccessor accessor) {
        if (!((CubicLevelHeightAccessor) accessor).isCubic()) {
            return this.levelHeightAccessor.getSectionsCount();
        }
        if (accessor instanceof ProtoCube.FakeSectionCount) {
            return accessor.getSectionsCount();
        }
        if (accessor instanceof Level) {
            if (((CubicLevelHeightAccessor) accessor).generates2DChunks()) {
                int height = ((Level) accessor).dimensionType().height();
                int minY = ((Level) accessor).dimensionType().minY();

                int minSectionY = SectionPos.blockToSectionCoord(minY);
                int maxSectionY = SectionPos.blockToSectionCoord(minY + height - 1) + 1;

                return maxSectionY - minSectionY;
            }
        }
        if (accessor.getMaxBuildHeight() > 2048) {
            return 16;
        }
        return Math.min(CubicConstants.SECTION_COUNT * 2, accessor.getSectionsCount()); // TODO: properly handle ProtoChunk
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void setHeight(CallbackInfoReturnable<Integer> cir) {
        if (this.levelHeightAccessor instanceof Level level) {
            if (((CubicLevelHeightAccessor) this).generates2DChunks()) {
                cir.setReturnValue(level.dimensionType().height());
            }
        }
    }

    @Inject(method = "getMinBuildHeight", at = @At("HEAD"), cancellable = true)
    private void setMinHeight(CallbackInfoReturnable<Integer> cir) {
        if (this.levelHeightAccessor instanceof Level level) {
            if (this.generates2DChunks()) {
                cir.setReturnValue(level.dimensionType().minY());
            }
        }
    }

    @Override
    public WorldStyle worldStyle() {
        return worldStyle;
    }

    @Override
    public boolean isCubic() {
        return isCubic;
    }

    @Override
    public boolean generates2DChunks() {
        return generates2DChunks;
    }

    @Override
    public void setWorldStyle(WorldStyle worldStyle) {
        this.worldStyle = worldStyle;
    }
}
