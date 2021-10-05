package io.github.opencubicchunks.cubicchunks.mixin.asm.common;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({
    ChunkMap.DistanceManager.class,
    ChunkMap.class,
    ChunkHolder.class,
    DynamicGraphMinFixedPoint.class,
    NaturalSpawner.class,
    BlockLightEngine.class,
    SkyLightEngine.class
})
public class MixinAsmTarget {
    // intentionally empty
}