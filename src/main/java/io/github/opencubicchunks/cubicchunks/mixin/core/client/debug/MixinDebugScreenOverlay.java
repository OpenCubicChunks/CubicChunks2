package io.github.opencubicchunks.cubicchunks.mixin.core.client.debug;

import java.util.List;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeSource;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(DebugScreenOverlay.class)
public abstract class MixinDebugScreenOverlay {

    @Shadow @Final private Minecraft minecraft;

    @Nullable @Shadow protected abstract LevelChunk getServerChunk();

    @SuppressWarnings("rawtypes")
    @Inject(method = "getGameInformation",
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 6),
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onAddChunkInfo(CallbackInfoReturnable<List> cir, /*IntegratedServer integratedserver, Connection networkmanager, float f, float f1,*/
                                String s, BlockPos blockpos, Entity entity, Direction direction, String s1, /*ChunkPos chunkpos,*/ Level world, LongSet longset,
                                List<String> debugScreenList/*, String s2*/) {
        debugScreenList.add(String.format("Cube:  %d %d %d in %d %d %d",
            Coords.blockToLocal(blockpos.getX()), Coords.blockToLocal(blockpos.getY()), Coords.blockToLocal(blockpos.getZ()),
            Coords.blockToCube(blockpos.getX()), Coords.blockToCube(blockpos.getY()), Coords.blockToCube(blockpos.getZ()))
        );
    }

    @Inject(method = "getGameInformation",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",
            ordinal = 0),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void onAddLightInfo(CallbackInfoReturnable<List<String>> cir, String string2, BlockPos pos, Entity entity, Direction direction,
                                      String string7, Level level, LongSet longSet, List<String> list, LevelChunk clientChunk, int i) {
        LevelChunk serverChunk = this.getServerChunk();
        if (((CubicLevelHeightAccessor) level).isCubic()) {
            if (this.minecraft.getSingleplayerServer() != null) {
                String serverHeight = "???";
                if (serverChunk != null) {
                    LightSurfaceTrackerWrapper heightmap = ((LightHeightmapGetter) serverChunk).getServerLightHeightmap();
                    int height = heightmap.getFirstAvailable(pos.getX() & 0xF, pos.getZ() & 0xF);
                    serverHeight = "" + height;
                }
                list.add("Server light heightmap height: " + serverHeight);
            }
            int clientHeight = ((LightHeightmapGetter) clientChunk).getClientLightHeightmap().getFirstAvailable(pos.getX() & 0xF, pos.getZ() & 0xF);
            list.add("Client light heightmap height: " + clientHeight);
        }

        // No cubic check here because it's a vanilla feature that was removed anyway
        if (this.minecraft.getSingleplayerServer() != null) {
            if (serverChunk != null) {
                LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
                list.add("Server Light: (" + lightEngine.getLayerListener(LightLayer.SKY).getLightValue(pos) + " sky, "
                    + lightEngine.getLayerListener(LightLayer.BLOCK).getLightValue(pos) + " block)");
            } else {
                list.add("Server Light: (?? sky, ?? block)");
            }
        }
    }

    @Inject(method = "getGameInformation",
            at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void onAddLocalDifficultyInfo(CallbackInfoReturnable<List<String>> cir, String string2, BlockPos blockPos, Entity entity, Direction direction, String string7, Level level,
                                          LongSet longSet, List<String> list, LevelChunk clientChunk, int i, int j, int k, LevelChunk serverChunk) {
        if (this.minecraft.getSingleplayerServer() != null) {
            list.add("Chunk inhabited time: " + (serverChunk == null ? "???" : "" + serverChunk.getInhabitedTime()));
            if (((CubicLevelHeightAccessor) level).isCubic()) {
                var cube = ((CubeSource) level.getChunkSource())
                        .getCube(Coords.blockToCube(blockPos.getX()), Coords.blockToCube(blockPos.getY()), Coords.blockToCube(blockPos.getZ()), ChunkStatus.FULL, false);
                list.add("Cube inhabited time: " + (cube == null ? "???" : "" + cube.getCubeInhabitedTime()));
            }
        }
    }
}
