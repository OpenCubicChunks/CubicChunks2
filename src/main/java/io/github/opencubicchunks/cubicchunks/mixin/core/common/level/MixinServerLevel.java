package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.InterleavedHeightmapStorage;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.entity.ChunkEntityStateEventSource;
import io.github.opencubicchunks.cubicchunks.chunk.entity.IsCubicEntityContext;
import io.github.opencubicchunks.cubicchunks.levelgen.CubicNoiseBasedChunkGenerator;
import io.github.opencubicchunks.cubicchunks.world.CubicChunksSavedData;
import io.github.opencubicchunks.cubicchunks.world.level.CubicFastServerTickList;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelTicks;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.server.CubicMinecraftServer;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel extends MixinLevel implements CubicServerLevel {

    @Shadow @Final private static Logger LOGGER;

    private HeightmapStorage heightmapStorage;

    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

    @Shadow public abstract ServerChunkCache getChunkSource();

    @Shadow @Final private MinecraftServer server;

    @Shadow @Final private LevelTicks<Fluid> fluidTicks;

    @Shadow @Final private LevelTicks<Block> blockTicks;

    @Inject(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/level/Level;<init>(Lnet/minecraft/world/level/storage/WritableLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/Holder;Ljava/util/function/Supplier;ZZJ)V"
        )
    )
    private void initSetCubic(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData,
                              ResourceKey<Level> levelKey, Holder<DimensionType> dimensionHolder, ChunkProgressListener chunkProgressListener, ChunkGenerator chunkGenerator, boolean bl,
                              long l,
                              List list,
                              boolean bl2,
                              CallbackInfo ci) {
        var dataFixer = minecraftServer.getFixerUpper();

        DimensionType dimension = dimensionHolder.value();
        ResourceLocation dimensionKey = minecraftServer.registryAccess().registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getKey(dimension);

        Path dimensionFolderPath = levelStorageAccess.getDimensionPath(levelKey);
        File dimensionFolder = dimensionFolderPath.toFile();
        var config = ((CubicMinecraftServer) minecraftServer).getServerConfig();

        this.heightmapStorage = new InterleavedHeightmapStorage(new File(dimensionFolder, "tempHeightmap"));

        if (config == null) {
            CubicChunks.LOGGER.info("No cubic chunks config found; disabling CC for dimension " + dimensionKey);
            worldStyle = WorldStyle.CHUNK;
        } else {
            File file2 = new File(dimensionFolder, "data");
            file2.mkdirs();
            // The dimension's DimensionDataStorage isn't created at this point, so we make our own temporary one to check/create the CC data
            var tempDataStorage = new DimensionDataStorage(file2, dataFixer);
            var cubicChunksSavedData = tempDataStorage.get(CubicChunksSavedData::load, CubicChunksSavedData.FILE_ID);
            if (cubicChunksSavedData != null) {
                CubicChunks.LOGGER.info("Loaded CC world style " + cubicChunksSavedData.worldStyle.name() + " for dimension " + dimensionKey);
            } else {
                CubicChunks.LOGGER.info("CC data for dimension " + dimensionKey + " is null, generating it");
                cubicChunksSavedData = tempDataStorage.computeIfAbsent(CubicChunksSavedData::load,
                        () -> new CubicChunksSavedData(config.getWorldStyle(levelKey)), CubicChunksSavedData.FILE_ID);
                CubicChunks.LOGGER.info("Generated CC data. World style: " + cubicChunksSavedData.worldStyle.name());
                cubicChunksSavedData.setDirty();
            }
            tempDataStorage.save();
            worldStyle = cubicChunksSavedData.worldStyle;
        }

        isCubic = worldStyle.isCubic();
        generates2DChunks = worldStyle.generates2DChunks();

        if (isCubic && (chunkGenerator instanceof NoiseBasedChunkGenerator)) {
            ((CubicNoiseBasedChunkGenerator) chunkGenerator).setCubic();
        }
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/ticks/LevelTicks"))
    private <T> LevelTicks<T> constructTickList(LongPredicate longPredicate, Supplier<ProfilerFiller> supplier) {
        if (!((CubicLevelHeightAccessor) this).isCubic()) {
            return new LevelTicks<>(longPredicate, supplier);
        }
        return new CubicLevelTicks<>(longPredicate, supplier);
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void markCubic(MinecraftServer minecraftServer, Executor executor, LevelStorageSource.LevelStorageAccess levelStorageAccess, ServerLevelData serverLevelData,
                           ResourceKey resourceKey, Holder holder, ChunkProgressListener chunkProgressListener, ChunkGenerator chunkGenerator, boolean bl, long l,
                           List list, boolean bl2, CallbackInfo ci) {
        ((IsCubicEntityContext) this.entityManager).setIsCubic(((CubicLevelHeightAccessor) this).isCubic());
        if (this.fluidTicks instanceof CubicLevelTicks ticks) {
            ((ChunkEntityStateEventSource) this.entityManager).registerChunkEntityStateEventHandler(ticks);
        }
        if (this.blockTicks instanceof CubicLevelTicks ticks) {
            ((ChunkEntityStateEventSource) this.entityManager).registerChunkEntityStateEventHandler(ticks);
        }
    }

    private boolean isPositionTicking(BlockPos pos) {
        return this.entityManager.canPositionTick(pos);
    }

    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRainingAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean onLightningRainCheck(ServerLevel instance, BlockPos blockPos) {
        // Return false in the rain check if the position isn't loaded, so that lightning strikes don't occur in unloaded cubes
        return (!this.isCubic || isPositionTicking(blockPos)) && isRainingAt(blockPos);
    }

    // Prevent snow/ice if the blocks aren't loaded
    @Inject(method = "tickChunk", locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private void onSnowAndIce(LevelChunk levelChunk, int i, CallbackInfo ci, ChunkPos chunkPos, boolean bl, int j, int k, ProfilerFiller profilerFiller, BlockPos blockPos2,
                                BlockPos blockPos3) {
        if (!this.isCubic) {
            return;
        }
        if (isPositionTicking(blockPos2) && isPositionTicking(blockPos3)) {
            return;
        }
        // TODO we shouldn't cancel here, as it could interfere with other mods' injects
        //      however it's difficult to control snow+ice without cancelling out of the method
        ci.cancel();
        // cancelling skips a call to pop()
        profilerFiller.pop();
    }

    //TODO(Salamander): The argument of this method is now a chunk pos (as a long). It should be good but I'm not sure, we'll see
    /*@Inject(method = "close", at = @At("TAIL"))
    private void onLevelClose(CallbackInfo ci) {
        try {
            this.heightmapStorage.close();
        } catch (IOException e) {
            LOGGER.error("Error when closing heightmap storage", e);
        }
    }

    @Redirect(method = "isPositionTickingWithEntitiesLoaded", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long useCubePosInCubicWorld(BlockPos blockPos) {
        return ((CubicLevelHeightAccessor) this).isCubic() ? CubePos.asLong(blockPos) : ChunkPos.asLong(blockPos);
    }*/

    @Override
    public void onCubeUnloading(LevelCube cube) {
        cube.invalidateAllBlockEntities();
        cube.unregisterTicks(Utils.unsafeCast(this));
    }

    @Override
    public HeightmapStorage getHeightmapStorage() {
        return heightmapStorage;
    }

    @Override
    public void tickCube(LevelCube cube, int randomTicks) {
        ProfilerFiller profilerFiller = this.getProfiler();

        // TODO this method should probably use ASM and exclude lightning/snow rather than copying randomTicks
        profilerFiller.push("tickBlocks");
        if (randomTicks > 0) {
            LevelChunkSection[] sections = cube.getSections();
            CubePos cubePos = cube.getCubePos();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection chunkSection = sections[i];
                if (chunkSection.isRandomlyTicking()) {
                    SectionPos columnPos = Coords.sectionPosByIndex(cubePos, i);
                    int minX = columnPos.minBlockX();
                    int minY = columnPos.minBlockY();
                    int minZ = columnPos.minBlockZ();
                    for (int j = 0; j < randomTicks; j++) {
                        BlockPos blockPos = this.getBlockRandomPos(minX, minY, minZ, 15);
                        profilerFiller.push("randomTick");
                        BlockState blockState = chunkSection.getBlockState(blockPos.getX() - minX, blockPos.getY() - minY, blockPos.getZ() - minZ);
                        if (blockState.isRandomlyTicking()) {
                            blockState.randomTick((ServerLevel) (Object) this, blockPos, this.random);
                        }

                        FluidState fluidState = blockState.getFluidState();
                        if (fluidState.isRandomlyTicking()) {
                            fluidState.randomTick((Level) (Object) this, blockPos, this.random);
                        }
                        profilerFiller.pop();
                    }
                }
            }
        }
        profilerFiller.pop();
    }
}