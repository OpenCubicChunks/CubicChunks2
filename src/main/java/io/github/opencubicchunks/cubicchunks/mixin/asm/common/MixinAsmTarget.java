package io.github.opencubicchunks.cubicchunks.mixin.asm.common;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.DynamicGraphMinFixedPointAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.Container;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({
    ChunkMap.DistanceManager.class,
    ChunkMap.class,
    ChunkHolder.class,
    DynamicGraphMinFixedPoint.class,
    NaturalSpawner.class,

    //Long Pos Transforms
    BlockLightEngine.class,
    SkyLightEngine.class,
    LayerLightEngine.class,
    SectionPos.class,
    LayerLightSectionStorage.class,
    SkyLightSectionStorage.class,
    BlockLightSectionStorage.class
})
public class MixinAsmTarget {
    // intentionally empty
}