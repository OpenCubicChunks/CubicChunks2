package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import java.util.List;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkMapAccess;
import io.github.opencubicchunks.cubicchunks.server.level.CubeMap;
import io.github.opencubicchunks.cubicchunks.world.CubicLocalMobCapCalculator;
import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelAccessor;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalMobCapCalculator.class)
public class MixinLocalMobCapCalculator implements CubicLocalMobCapCalculator {
    @Shadow @Final private Long2ObjectMap<List<ServerPlayer>> playersNearChunk;
    @Shadow @Final private ChunkMap chunkMap;
    @Shadow @Final private Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts;
    private boolean isCubic;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setCubic(ChunkMap chunkMap, CallbackInfo ci) {
        this.isCubic = ((CubicLevelAccessor) ((ChunkMapAccess) chunkMap).getLevel()).isCubic();
    }

    @Inject(method = "getPlayersNear", at = @At("HEAD"))
    private void crashIfCubic(ChunkPos chunkPos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        sanityCheck(false);
    }

    @Inject(method = "addMob", at = @At("RETURN"))
    private void crashIfCubic(ChunkPos chunkPos, MobCategory mobCategory, CallbackInfo ci) {
        sanityCheck(false);
    }

    @Inject(method = "canSpawn", at = @At("HEAD"))
    private void canSpawn(MobCategory mobCategory, ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
        sanityCheck(false);
    }

    private void sanityCheck(boolean shouldBeCubic) {
        if (this.isCubic != shouldBeCubic) {
            throw new IllegalStateException("isCubic is " + this.isCubic + " but should be " + shouldBeCubic);
        }
    }

    @Override
    public void addMob(CubePos cube, MobCategory category) {
        sanityCheck(true);

        for (ServerPlayer player : this.getPlayersNear(cube)) {
            this.playerMobCounts.computeIfAbsent(player, k -> new LocalMobCapCalculator.MobCounts()).add(category);
        }
    }

    @Override
    public boolean canSpawn(MobCategory category, CubePos cube) {
        sanityCheck(true);

        for (ServerPlayer player : this.getPlayersNear(cube)) {
            LocalMobCapCalculator.MobCounts mobCounts = this.playerMobCounts.get(player);
            if (mobCounts == null || mobCounts.canSpawn(category)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isCubic() {
        return this.isCubic;
    }

    private List<ServerPlayer> getPlayersNear(CubePos cubePos) {
        return this.playersNearChunk.computeIfAbsent(cubePos.asLong(), posAsLong -> ((CubeMap) this.chunkMap).getPlayersCloseForSpawning(cubePos));
    }
}
