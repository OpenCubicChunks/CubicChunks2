package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccess {

    @Accessor ServerLevel getLevel();

    @Invoker @Nullable ChunkHolder invokeGetUpdatingChunkIfPresent(long chunkPosIn);
    @Invoker boolean invokePromoteChunkMap();
    @Invoker void invokeOnFullChunkStatusChange(ChunkPos chunkPos, FullChunkStatus fullChunkStatus);
    @Accessor int getViewDistance();

    @Invoker void invokeReleaseLightTicket(ChunkPos pos);
    @Accessor Long2ObjectLinkedOpenHashMap<ChunkHolder> getVisibleChunkMap();
}