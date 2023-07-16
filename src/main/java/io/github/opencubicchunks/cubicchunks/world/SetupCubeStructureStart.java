package io.github.opencubicchunks.cubicchunks.world;

import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public interface SetupCubeStructureStart {

    //We use a BlockPos as our final parameter in place of a chunk position.
    void placeInCube(WorldGenLevel worldGenLevel, StructureManager structureFeatureManager, ChunkGenerator chunkGenerator, Random random, BoundingBox boundingBox, BlockPos chunkPos);

}
