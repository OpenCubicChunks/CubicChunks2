package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.chunk.VerticalViewDistanceListener;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkMapAccess;
import io.github.opencubicchunks.cubicchunks.server.level.CubeHolder;
import io.github.opencubicchunks.cubicchunks.server.level.CubeMap;
import io.github.opencubicchunks.cubicchunks.server.level.CubicDistanceManager;
import io.github.opencubicchunks.cubicchunks.server.level.ServerCubeCache;
import io.github.opencubicchunks.cubicchunks.test.LightingIntegrationTest;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeStatus;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkCache.class)
public abstract class MixinServerChunkCache_CubeLoadUnload implements LightingIntegrationTest.TestContext.CubeLoadUnload, ServerCubeCache, LightCubeGetter, VerticalViewDistanceListener {
    @Shadow @Final public ChunkMap chunkMap;
    @Shadow @Final ServerLevel level;
    @Shadow @Final private DistanceManager distanceManager;

    @Shadow protected abstract boolean chunkAbsent(@Nullable ChunkHolder chunkHolderIn, int i);

    @Override public CompletableFuture<Either<CubeAccess, ChunkHolder.ChunkLoadingFailure>> getCubeFuture(CubePos pos, ChunkStatus requiredStatus) {
        CubePos cubePos = CubePos.of(pos.getX(), pos.getY(), pos.getZ());
        long i = cubePos.asLong();
        int j = 33 + CubeStatus.getDistance(requiredStatus);
        ChunkHolder chunkholder = this.getVisibleCubeIfPresent(i);
        ((CubicDistanceManager) this.distanceManager).addCubeTicket(TicketType.START, cubePos, j, Unit.INSTANCE);
        if (this.chunkAbsent(chunkholder, j)) {
            this.runCubeDistanceManagerUpdates();
            chunkholder = this.getVisibleCubeIfPresent(i);
            if (this.chunkAbsent(chunkholder, j)) {
                throw Util.pauseInIde(new IllegalStateException("No cube holder after ticket has been added"));
            }
        }

        if (this.chunkAbsent(chunkholder, j)) {
            throw Util.pauseInIde(new IllegalStateException("This shouldn't happen"));
        }

        return ((CubeHolder) chunkholder).getOrScheduleCubeFuture(requiredStatus, this.chunkMap);
    }

    @Override public CompletableFuture<CubeAccess> getCubeUnloadFuture(CubePos cubePos, ChunkStatus requiredStatus) {
        long i = cubePos.asLong();
        int j = 33 + CubeStatus.getDistance(requiredStatus);
        ((CubicDistanceManager) this.distanceManager).removeCubeTicket(TicketType.START, cubePos, j, Unit.INSTANCE);
        var cubeHolder = ((CubeHolder) this.getVisibleCubeIfPresent(i));
        return cubeHolder.getCubeToSave();
    }

    // getVisibleChunkIfPresent
    @Nullable
    private ChunkHolder getVisibleCubeIfPresent(long cubePosIn) {
        return ((CubeMap) this.chunkMap).getVisibleCubeIfPresent(cubePosIn);
    }

    // runDistanceManagerUpdates
    private boolean runCubeDistanceManagerUpdates() {
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = ((ChunkMapAccess) this.chunkMap).invokePromoteChunkMap();
        return flag || flag1;
    }
}
