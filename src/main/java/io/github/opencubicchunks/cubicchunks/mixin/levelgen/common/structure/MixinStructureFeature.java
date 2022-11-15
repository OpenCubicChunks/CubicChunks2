package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.structure;

import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StructureFeature.class)
public abstract class MixinStructureFeature {
    @Redirect(method = "loadStaticStart", at = @At(value = "NEW", target = "net/minecraft/world/level/ChunkPos"))
    private static ChunkPos loadStaticCubeStart(int x, int z, StructurePieceSerializationContext context, CompoundTag nbt, long worldSeed) {
        ChunkPos original = new ChunkPos(x, z);
        return nbt.contains("ChunkY") ? new ImposterChunkPos(x, nbt.getInt("ChunkY"), z) : original;
    }
}
