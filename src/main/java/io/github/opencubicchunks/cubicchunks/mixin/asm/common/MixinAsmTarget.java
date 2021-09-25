package io.github.opencubicchunks.cubicchunks.mixin.asm.common;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({
    ChunkMap.DistanceManager.class,
    ChunkMap.class,
    ChunkHolder.class,
    NaturalSpawner.class,
    DynamicGraphMinFixedPoint.class
})
public class MixinAsmTarget {
    // intentionally empty
}