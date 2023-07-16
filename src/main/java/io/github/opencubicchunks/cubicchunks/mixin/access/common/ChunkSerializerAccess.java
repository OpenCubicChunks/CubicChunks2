package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.Map;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkSerializer.class)
public interface ChunkSerializerAccess {

    @Invoker
    static CompoundTag invokePackStructureData(StructurePieceSerializationContext context, ChunkPos chunkPos, Map<Structure, StructureStart> structureStarts,
                                               Map<Structure, LongSet> structureReferences) {
        throw new Error("Mixin did not apply.");
    }

    @Invoker
    static Map<Structure, StructureStart> invokeUnpackStructureStart(StructurePieceSerializationContext context, CompoundTag nbt,
                                                                               long worldSeed) {
        throw new Error("Mixin did not apply.");
    }

    @Invoker
    static Map<Structure, LongSet> invokeUnpackStructureReferences(RegistryAccess registryAccess, ChunkPos chunkPos, CompoundTag nbt) {
        throw new Error("Mixin did not apply.");
    }

    @Accessor
    static Codec<PalettedContainer<BlockState>> getBLOCK_STATE_CODEC() {
        throw new Error("Mixin did not apply.");
    }
}
