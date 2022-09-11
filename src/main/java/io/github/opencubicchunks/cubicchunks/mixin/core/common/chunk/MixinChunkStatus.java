package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import static io.github.opencubicchunks.cc_core.utils.Utils.unsafeCast;
import static net.minecraft.core.Registry.BIOME_REGISTRY;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.levelgen.chunk.CubeGenerator;
import io.github.opencubicchunks.cubicchunks.levelgen.chunk.NoiseAndSurfaceBuilderHelper;
import io.github.opencubicchunks.cubicchunks.levelgen.util.CubicWorldGenUtils;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkMapAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ThreadedLevelLightEngineAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.server.CubicThreadedLevelLightEngine;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(ChunkStatus.class)
public class MixinChunkStatus {
    // lambda$static$0 == PASSTHROUGH_LOAD_TASK
    // lambda$static$1 == EMPTY
    // lambda$static$2 == STRUCTURE_STARTS
    // lambda$static$3 == STRUCTURE_STARTS (loading worker)
    // lambda$static$4 == STRUCTURE_REFERENCES
    // lambda$static$6 == BIOMES
    // lambda$static$8 == NOISE
    // lambda$static$9 == SURFACE
    // lambda$static$10 == CARVERS
    // lambda$static$11 == LIQUID_CARVERS
    // lambda$static$12 == FEATURES
    // lambda$static$13 == LIGHT
    // lambda$static$14 == LIGHT (loading worker)
    // lambda$static$15 == SPAWM
    // lambda$static$16 == HEIGHTMAPS
    // lambda$static$17 == FULL
    // lambda$static$18 == FULL (loading worker)

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$0(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD")
    )
    private static void noopLoadingWorker(
        ChunkStatus status, ServerLevel level, StructureManager structureManager,
        ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {

        if (chunk instanceof ProtoCube && !chunk.getStatus().isOrAfter(status)) {
            ((ProtoCube) chunk).updateCubeStatus(status);
        }
    }

    // EMPTY -> does nothing already

    // structure starts - replace setStatus, handled by MixinChunkGenerator
    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$2(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;Z)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true)
    private static void generateStructureStatus(
        ChunkStatus status, Executor executor, ServerLevel level, ChunkGenerator generator,
        StructureManager structureManager, ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        List<ChunkAccess> chunks, ChunkAccess chunk, boolean bl,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            if (!(chunk instanceof CubeAccess)) {
                return;
            }
            if (!chunk.getStatus().isOrAfter(status)) {
                if (chunk instanceof ProtoCube) {
                    ((ProtoCube) chunk).updateCubeStatus(status);
                }
            }
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }

        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
        if (!(chunk instanceof CubeAccess)) {
            //vanilla
            return;
        }
        //cc
        if (!chunk.getStatus().isOrAfter(status)) {
            if (level.getServer().getWorldData().worldGenSettings().generateFeatures()) { // check if structures are enabled
                // structureFeatureManager ==  getStructureManager?
                generator.createStructures(level.registryAccess(), level.structureFeatureManager(), chunk, structureManager, level.getSeed());
            }
            if (chunk instanceof ProtoCube) {
                ((ProtoCube) chunk).updateCubeStatus(status);
            }
        }
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$4(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksStructureReferences(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
        if (chunk instanceof CubeAccess) {
            generator.createReferences(new CubeWorldGenRegion(level, unsafeCast(neighbors), status, chunk, -1), level.structureFeatureManager(), chunk);
        }
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$6(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;Z)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksBiome(ChunkStatus chunkStatus, Executor executor, ServerLevel level, ChunkGenerator chunkGenerator, StructureManager structureManager,
                                         ThreadedLevelLightEngine threadedLevelLightEngine, Function function, List<CubeAccess> cubes, ChunkAccess chunkAccess, boolean bl,
                                         CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        if (!bl && chunkAccess.getStatus().isOrAfter(chunkStatus)) {
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunkAccess)));
            return;
        }
        if (chunkAccess instanceof ProtoCube) {
            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, cubes, chunkStatus, chunkAccess, -1);

            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> eitherCompletableFuture =
                chunkGenerator.createBiomes(level.registryAccess().registryOrThrow(BIOME_REGISTRY), executor, Blender.empty(),
                    level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion), chunkAccess).thenApply(Either::left);

            cir.setReturnValue(eitherCompletableFuture);
            return;
        }
        if (chunkAccess instanceof ProtoChunk) {
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunkAccess)));
        }
    }


    // biomes -> handled by MixinChunkGenerator
    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$8(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;Z)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksNoise(ChunkStatus status, Executor executor, ServerLevel level, ChunkGenerator chunkGenerator, StructureManager structureManager,
                                         ThreadedLevelLightEngine threadedLevelLightEngine, Function function, List neighbors, ChunkAccess chunk, boolean bl,
                                         CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> ci) {


        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            if (chunk instanceof CubeAccess) {
                ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            }
            return;
        }

        ci.cancel();
        if (chunk instanceof CubeAccess) {
            if (chunk.getStatus().isOrAfter(status)) {
                ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
                return;
            }

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, unsafeCast(neighbors), status, chunk, 0);

            CubePos cubePos = ((CubeAccess) chunk).getCubePos();
            int cubeY = cubePos.getY();

            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(BIOME_REGISTRY);
            ProtoCube cubeAbove = new ProtoCube(CubePos.of(cubePos.getX(), cubeY + 1, cubePos.getZ()), UpgradeData.EMPTY, cubeWorldGenRegion,
                biomeRegistry, null);

            CompletableFuture<ChunkAccess> chainedNoiseFutures = null;

            for (int columnX = 0; columnX < CubicConstants.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < CubicConstants.DIAMETER_IN_SECTIONS; columnZ++) {
                    cubeAbove.moveColumns(columnX, columnZ);
                    if (chunk instanceof ProtoCube) {
                        ((ProtoCube) chunk).moveColumns(columnX, columnZ);
                    }

                    ChunkPos pos = chunk.getPos();

                    NoiseAndSurfaceBuilderHelper cubeAccessWrapper = new NoiseAndSurfaceBuilderHelper((CubeAccess) chunk, cubeAbove, biomeRegistry);
                    cubeAccessWrapper.moveColumn(columnX, columnZ);

                    if (chainedNoiseFutures == null) {
                        chainedNoiseFutures = getNoiseSurfaceCarverFuture(executor, level, chunkGenerator, cubeWorldGenRegion, cubeY, pos, cubeAccessWrapper);
                    } else {
                        // Wait for first completion stage to complete before getting the next future, as it calls supplyAsync internally
                        chainedNoiseFutures =
                            chainedNoiseFutures.thenCompose(futureIn -> getNoiseSurfaceCarverFuture(executor, level, chunkGenerator, cubeWorldGenRegion, cubeY, pos, cubeAccessWrapper));
                    }
                }
            }
            assert chainedNoiseFutures != null;
            ci.setReturnValue(chainedNoiseFutures.thenApply(helper -> Either.left(chunk)));
        } else {
            ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
        }
    }

    private static CompletableFuture<ChunkAccess> getNoiseSurfaceCarverFuture(Executor executor, ServerLevel level, ChunkGenerator generator, CubeWorldGenRegion cubeWorldGenRegion,
                                                                              int cubeY, ChunkPos pos, NoiseAndSurfaceBuilderHelper cubeAccessWrapper) {
        StructureFeatureManager structureFeatureManager = level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion);
        return generator.fillFromNoise(executor, Blender.empty(), structureFeatureManager, cubeAccessWrapper).thenApply(chunkAccess -> {
            cubeAccessWrapper.applySections();
            cubeAccessWrapper.setStatus(ChunkStatus.NOISE);
            // Exit early and don't waste time on empty sections.
            if (areSectionsEmpty(cubeY, pos, ((NoiseAndSurfaceBuilderHelper) chunkAccess).getDelegateByIndex(0))) {
                return chunkAccess;
            }
            generator.buildSurface(cubeWorldGenRegion, structureFeatureManager, chunkAccess);
            cubeAccessWrapper.setNeedsExtraHeight(false);
            cubeAccessWrapper.setStatus(ChunkStatus.SURFACE);

            // Carvers
            generator.applyCarvers(cubeWorldGenRegion, level.getSeed(), level.getBiomeManager(), structureFeatureManager, cubeAccessWrapper, GenerationStep.Carving.AIR);
            cubeAccessWrapper.setStatus(ChunkStatus.CARVERS);
            generator.applyCarvers(cubeWorldGenRegion, level.getSeed(), level.getBiomeManager(), structureFeatureManager, cubeAccessWrapper, GenerationStep.Carving.LIQUID);
            cubeAccessWrapper.setStatus(ChunkStatus.LIQUID_CARVERS);
            return chunkAccess;
        });
    }

    private static boolean areSectionsEmpty(int cubeY, ChunkPos pos, CubeAccess cube) {
        int emptySections = 0;
        for (int yScan = 0; yScan < CubicConstants.DIAMETER_IN_SECTIONS; yScan++) {
            int sectionY = Coords.cubeToSection(cubeY, yScan);
            int sectionIndex = Coords.sectionToIndex(pos.x, sectionY, pos.z);
            LevelChunkSection cubeSection = cube.getSections()[sectionIndex];
            if (cubeSection.hasOnlyAir()) {
                emptySections++;
            }
            if (emptySections == CubicConstants.DIAMETER_IN_SECTIONS) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$9(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksSurface(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$10(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksCarvers(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$11(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksLiquidCarvers(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$12(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;"
            + "Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;Z)"
            + "Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "HEAD"), cancellable = true
    )
    private static void featuresSetStatus(
        ChunkStatus status, Executor executor, ServerLevel level, ChunkGenerator generator,
        StructureManager structureManager, ThreadedLevelLightEngine lightEngine,
        Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> func,
        List<ChunkAccess> chunks, ChunkAccess chunk, boolean bl,
        CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            if (!(chunk instanceof ProtoCube protoCube)) {
                return;
            }
            if (!protoCube.getStatus().isOrAfter(status)) {
                protoCube.updateCubeStatus(status);
            }
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }
        if (!(chunk instanceof ProtoCube protoCube)) {
            // cancel column population for now
            ((ProtoChunk) chunk).setStatus(status);
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }
        protoCube.setLightEngine(lightEngine);
        ChunkStatus oldStatus = protoCube.getStatus();
        if (!oldStatus.isOrAfter(status)) {
            // TODO: reimplement heightmaps
            //Heightmap.updateChunkHeightmaps(chunk, EnumSet
            //        .of(Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Heightmap.Type.OCEAN_FLOOR,
            //        Heightmap.Type.WORLD_SURFACE));

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, unsafeCast(chunks), status, chunk, 1);
            StructureFeatureManager structureFeatureManager = level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion);
            protoCube.applyFeatureStates();
            ((CubeGenerator) generator).decorate(cubeWorldGenRegion, structureFeatureManager, (ProtoCube) chunk);
            protoCube.updateCubeStatus(status);
        }
        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
    }

    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private static void lightChunkCC(ChunkStatus status, ThreadedLevelLightEngine lightEngine, ChunkAccess chunk,
                                     CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {

        if (!((CubicLevelHeightAccessor) chunk).isCubic()) {
            return;
        }

        if (!(chunk instanceof ProtoCube)) {
            ChunkPos pos = chunk.getPos();
            ((ThreadedLevelLightEngineAccess) lightEngine).invokeAddTask(pos.x, pos.z, ThreadedLevelLightEngine.TaskType.PRE_UPDATE, () -> {
                ((ChunkMapAccess) ((ThreadedLevelLightEngineAccess) lightEngine).getChunkMap()).invokeReleaseLightTicket(pos);
            });
            if (!chunk.getStatus().isOrAfter(status)) {
                ((ProtoChunk) chunk).setStatus(status);
            }
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            return;
        }
        boolean flag = ((ProtoCube) chunk).getStatus().isOrAfter(status) && chunk.isLightCorrect();
        if (!chunk.getStatus().isOrAfter(status)) {
            ((ProtoCube) chunk).updateCubeStatus(status);
        }
        cir.setReturnValue(unsafeCast(((CubicThreadedLevelLightEngine) lightEngine).lightCube((CubeAccess) chunk, flag).thenApply(Either::left)));
    }

    @SuppressWarnings("target")
    @Inject(
        method = "lambda$static$15(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/List;"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;)V",
        at = @At("HEAD"), cancellable = true
    )
    //TODO: Expose the above and bottom cubes via neighbors or thing else. Check if chunk generator overrides "spawnOriginalMobs" and redirect to our spawner instead.
    private static void cubicChunksSpawnMobs(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) level).isCubic()) {
            return;
        }
        ci.cancel();
        if (chunk instanceof CubeAccess) {
            int cubeY = ((CubeAccess) chunk).getCubePos().getY();

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, unsafeCast(neighbors), status, chunk, -1);
            for (int columnX = 0; columnX < CubicConstants.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < CubicConstants.DIAMETER_IN_SECTIONS; columnZ++) {
                    cubeWorldGenRegion.moveCenterCubeChunkPos(columnX, columnZ);
                    if (CubicWorldGenUtils.areSectionsEmpty(cubeY, chunk.getPos(), (CubeAccess) chunk)) {
                        continue;
                    }
                    generator.spawnOriginalMobs(cubeWorldGenRegion);
                }
            }
        }
    }
}
