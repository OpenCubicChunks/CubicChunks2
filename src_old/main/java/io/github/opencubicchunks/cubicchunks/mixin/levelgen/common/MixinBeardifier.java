package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Beardifier.class)
public class MixinBeardifier {

    @Nullable
    private ChunkAccess chunkAccess;

    @Redirect(method = "<init>(Lnet/minecraft/world/level/StructureFeatureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void setupChunkAccess(List<StructureStart> list, Consumer<StructureStart> action,
                                  StructureFeatureManager structureFeatureManager, ChunkAccess chunk) {
        if (((CubicLevelHeightAccessor) chunk).generates2DChunks()) {
            list.forEach(action);
            return;
        }
        this.chunkAccess = chunk;
        list.forEach(action);
        this.chunkAccess = null;
    }

    @SuppressWarnings("target")
    @Redirect(method = "lambda$new$2(Lnet/minecraft/world/level/ChunkPos;IILnet/minecraft/world/level/levelgen/structure/StructureStart;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/pools/JigsawJunction;getSourceX()I"))
    private int checkYBounds(JigsawJunction junction) {
        if (chunkAccess == null) {
            return junction.getSourceX();
        }
        int jigsawJunctionSourceY = junction.getSourceGroundY();
        int minY = chunkAccess.getMinBuildHeight();
        int maxY = chunkAccess.getMaxBuildHeight() - 1;
        boolean isInYBounds = jigsawJunctionSourceY > minY - 12 && jigsawJunctionSourceY < maxY + 12;

        if (isInYBounds) {
            return junction.getSourceX();
        } else {
            return Integer.MIN_VALUE;
        }
    }
}
