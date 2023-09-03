package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageAccess {

    @Invoker("setLightEnabled") void invokeSetLightEnabled(long chunkOrCubeSectionPos, boolean enabled);

//    @Invoker boolean invokeStoringLightForSection(long sectionPos);

    @Accessor LightChunkGetter getChunkSource();
}