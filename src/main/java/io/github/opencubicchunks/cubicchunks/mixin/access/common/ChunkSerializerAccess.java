package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.Map;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkSerializer.class)
public interface ChunkSerializerAccess {

    @Invoker
    static CompoundTag invokePackStructureData(StructurePieceSerializationContext context, ChunkPos chunkPos, Map<ConfiguredStructureFeature<?, ?>, StructureStart> structureStarts,
                                               Map<ConfiguredStructureFeature<?, ?>, LongSet> structureReferences) {
        throw new Error("Mixin did not apply.");
    }

    @Invoker
    static Map<ConfiguredStructureFeature<?, ?>, StructureStart> invokeUnpackStructureStart(StructurePieceSerializationContext context, CompoundTag nbt,
                                                                               long worldSeed) {
        throw new Error("Mixin did not apply.");
    }

    @Invoker
    static Map<ConfiguredStructureFeature<?, ?>, LongSet> invokeUnpackStructureReferences(RegistryAccess registryAccess, ChunkPos chunkPos, CompoundTag nbt) {
        throw new Error("Mixin did not apply.");
    }

    @Accessor
    static Codec<PalettedContainer<BlockState>> getBLOCK_STATE_CODEC() {
        throw new Error("Mixin did not apply.");
    }
}
