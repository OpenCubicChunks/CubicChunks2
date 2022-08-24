package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import static io.github.opencubicchunks.cc_core.utils.Utils.unsafeCast;
import static net.minecraft.core.Registry.BIOME_REGISTRY;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.levelgen.chunk.NoiseAndSurfaceBuilderHelper;
import io.github.opencubicchunks.cubicchunks.levelgen.util.CubicWorldGenUtils;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkMapAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ThreadedLevelLightEngineAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ColumnBiomeContainer;
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
    //Pre 1.18
    // lambda$static$0 == NOOP_LOADING_WORKER
    // lambda$static$1 == EMPTY
    // lambda$static$2 == STRUCTURE_STARTS
    // lambda$static$3 == STRUCTURE_REFERENCES
    // lambda$static$4 == BIOMES
    // lambda$static$5 == NOISE
    // lambda$static$6 == SURFACE
    // lambda$static$7 == CARVERS
    // lambda$static$8 == LIQUID_CARVERS
    // lambda$static$9 == FEATURES
    // lambda$static$10 == LIGHT
    // lambda$static$11 == LIGHT (loading worker)
    // lambda$static$12 == SPAWM
    // lambda$static$13 == HEIGHTMAPS
    // lambda$static$14 == FULL
    // lambda$static$15 == FULL (loading worker)

    //1.18
    // unnamed - named
    // method_20615 = lambda$static$0 = PASSTHROUGH_LOAD_TASK
    // method_17036 = lambda$static$1 = EMPTY
    // method_39464 = lambda$static$2 = STRUCTURE_STARTS (generation task)
    // method_39790 = lambda$static$3 = STRUCTURE_STARTS (loading_task)
    // method_16565 = lambda$static$4 = STRUCTURE_REFERENCES (generation_task)
    // method_38283 = lambda$static$5
    // method_38285 = lambda$static$6
    // method_39463 = lambda$static$7
    // method_38284 = lambda$static$8
    // method_16569 = lambda$static$9
    // method_38282 = lambda$static$10
    // method_39789 = lambda$static$11
    // method_20613 = lambda$static$12
    // method_20614 = lambda$static$13
    // method_16566 = lambda$static$14
    // method_17033 = lambda$static$15
    // method_38277 = lambda$static$16
    // method_20609 = lambda$static$17
    // method_38278 = lambda$static$18
    // method_12166 = lambda$static$19
    // method_38280 = lambda$generate$20


    @SuppressWarnings("target")
    @Inject(
        method = "method_20615",
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
    @Inject(
        method = "method_39464",
        at= @At("HEAD"), cancellable = true
    )
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
            if (!((CubeAccess) chunk).getCubeStatus().isOrAfter(status)) {
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
        if (!((CubeAccess) chunk).getCubeStatus().isOrAfter(status)) {
            if (level.getServer().getWorldData().worldGenSettings().generateFeatures()) { // check if structures are enabled
                // structureFeatureManager ==  getStructureManager?
                generator.createStructures(level.registryAccess(), level.structureFeatureManager(), chunk, structureManager, level.getSeed());
            }
            if (chunk instanceof ProtoCube) {
                ((ProtoCube) chunk).updateCubeStatus(status);
            }
        }
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "method_16565",
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

    @Inject(
        method = "method_38285",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksBiome(ChunkStatus chunkStatus, Executor executor, ServerLevel level, ChunkGenerator chunkGenerator, StructureManager structureManager,
                                         ThreadedLevelLightEngine threadedLevelLightEngine, Function function, List list, ChunkAccess chunkAccess, boolean bl,
                                         CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        if (chunkAccess instanceof ProtoCube cube) {
            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, unsafeCast(list), chunkStatus, chunkAccess, -1);

            cube.setHeightToCubeBounds(true);
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> chainedNoiseFutures = null;


            for (int columnX = 0; columnX < CubeAccess.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < CubeAccess.DIAMETER_IN_SECTIONS; columnZ++) {
                    cube.moveColumns(columnX, columnZ);
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> eitherCompletableFuture =
                        chunkGenerator.createBiomes(level.registryAccess().registryOrThrow(BIOME_REGISTRY), executor, Blender.empty(),
                            level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion), chunkAccess).thenApply(Either::left);
                    if (chainedNoiseFutures == null) {
                        chainedNoiseFutures = eitherCompletableFuture;
                    } else {
                        chainedNoiseFutures.thenApply(access -> eitherCompletableFuture);
                    }
                }
            }
            cube.setHeightToCubeBounds(false);

            cir.setReturnValue(chainedNoiseFutures);
        }

        if (chunkAccess instanceof ProtoChunk) {
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunkAccess)));
        }
    }


    // biomes -> handled by MixinChunkGenerator
    @Inject(
        method = "method_38284",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksNoise(ChunkStatus status, Executor executor, ServerLevel level, ChunkGenerator chunkGenerator, StructureManager structureManager,
                                         ThreadedLevelLightEngine threadedLevelLightEngine, Function function, List neighbors, ChunkAccess chunk, boolean bl,
                                         CallbackInfoReturnable<CompletableFuture> ci) {


        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            if (chunk instanceof CubeAccess) {
                ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
            }
            return;
        }

        ci.cancel();
        if (chunk instanceof CubeAccess) {
            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, unsafeCast(neighbors), status, chunk, 0);

            CubePos cubePos = ((CubeAccess) chunk).getCubePos();
            int cubeY = cubePos.getY();

            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(BIOME_REGISTRY);
            ProtoCube cubeAbove = new ProtoCube(CubePos.of(cubePos.getX(), cubeY + 1, cubePos.getZ()), UpgradeData.EMPTY, cubeWorldGenRegion,
                biomeRegistry, null);

            CompletableFuture<ChunkAccess> chainedNoiseFutures = null;

            for (int columnX = 0; columnX < CubeAccess.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < CubeAccess.DIAMETER_IN_SECTIONS; columnZ++) {
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
            ci.setReturnValue(chainedNoiseFutures.thenApply(ignored -> {
                if (chunk instanceof ProtoCube) {
                    ((ProtoCube) chunk).updateCubeStatus(status);
                }

                return Either.left(chunk);
            }));
        } else {
            ci.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
        }
    }

    private static CompletableFuture<ChunkAccess> getNoiseSurfaceCarverFuture(Executor executor, ServerLevel level, ChunkGenerator generator, CubeWorldGenRegion cubeWorldGenRegion,
                                                                              int cubeY, ChunkPos pos, NoiseAndSurfaceBuilderHelper cubeAccessWrapper) {
        StructureFeatureManager structureFeatureManager = level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion);
        return generator.fillFromNoise(executor, Blender.empty(), structureFeatureManager, cubeAccessWrapper).thenApply(chunkAccess -> {
            cubeAccessWrapper.applySections();

            // Exit early and don't waste time on empty sections.
            if (areSectionsEmpty(cubeY, pos, ((NoiseAndSurfaceBuilderHelper) chunkAccess).getDelegateByIndex(0))) {
                return chunkAccess;
            }
//            generator.buildSurface(cubeWorldGenRegion, structureFeatureManager, chunkAccess);
            cubeAccessWrapper.setNeedsExtraHeight(false);

            // Carvers
            generator.applyCarvers(cubeWorldGenRegion, level.getSeed(), level.getBiomeManager(), structureFeatureManager, cubeAccessWrapper, GenerationStep.Carving.AIR);
            generator.applyCarvers(cubeWorldGenRegion, level.getSeed(), level.getBiomeManager(), structureFeatureManager, cubeAccessWrapper, GenerationStep.Carving.LIQUID);
            return chunkAccess;
        });
    }

    private static boolean areSectionsEmpty(int cubeY, ChunkPos pos, CubeAccess cube) {
        int emptySections = 0;
        for (int yScan = 0; yScan < CubeAccess.DIAMETER_IN_SECTIONS; yScan++) {
            int sectionY = Coords.cubeToSection(cubeY, yScan);
            int sectionIndex = Coords.sectionToIndex(pos.x, sectionY, pos.z);
            LevelChunkSection cubeSection = cube.getCubeSections()[sectionIndex];
            if (cubeSection.hasOnlyAir()) {
                emptySections++;
            }
            if (emptySections == CubeAccess.DIAMETER_IN_SECTIONS) {
                return true;
            }
        }
        return false;
    }

    @Inject(
        method = "method_16569",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksSurface(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
        //if (chunk instanceof IBigCube) {
        //   generator.generateSurface(new CubeWorldGenRegion(world, unsafeCast(neighbors)), chunk);
        //}
    }

    @SuppressWarnings({ "UnresolvedMixinReference", "target" })
    @Inject(
        method = "method_38282",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksCarvers(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
//        if (chunk instanceof IBigCube) {
//            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors), chunk);
//
//            CubePrimer cubeAbove = new CubePrimer(CubePos.of(((IBigCube) chunk).getCubePos().getX(), ((IBigCube) chunk).getCubePos().getY() + 1,
//                ((IBigCube) chunk).getCubePos().getZ()), UpgradeData.EMPTY, cubeWorldGenRegion);
//
//            NoiseAndSurfaceBuilderHelper noiseAndSurfaceBuilderHelper = new NoiseAndSurfaceBuilderHelper((IBigCube) chunk, cubeAbove);
//
//            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
//                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
//                    cubeAbove.moveColumns(columnX, columnZ);
//                    if (chunk instanceof CubePrimer) {
//                        ((CubePrimer) chunk).moveColumns(columnX, columnZ);
//                    }
//                    noiseAndSurfaceBuilderHelper.moveColumn(columnX, columnZ);
//                    noiseAndSurfaceBuilderHelper.applySections();
//                }
//            }
//        }
    }

    @Inject(
        method = "method_39789",
        at = @At("HEAD"), cancellable = true
    )
    private static void cubicChunksLiquidCarvers(ChunkStatus status, ServerLevel level, ChunkGenerator generator, List<ChunkAccess> neighbors, ChunkAccess chunk, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) level).generates2DChunks()) {
            return;
        }
        ci.cancel();
//        if (chunk instanceof IBigCube) {
//            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(world, unsafeCast(neighbors), chunk);
//
//            CubePrimer cubeAbove = new CubePrimer(CubePos.of(((IBigCube) chunk).getCubePos().getX(), ((IBigCube) chunk).getCubePos().getY() + 1,
//                ((IBigCube) chunk).getCubePos().getZ()), UpgradeData.EMPTY, cubeWorldGenRegion);
//
//            NoiseAndSurfaceBuilderHelper noiseAndSurfaceBuilderHelper = new NoiseAndSurfaceBuilderHelper((IBigCube) chunk, cubeAbove);
//
//            //TODO: Verify liquid carvers are generating appropriately
//            for (int columnX = 0; columnX < IBigCube.DIAMETER_IN_SECTIONS; columnX++) {
//                for (int columnZ = 0; columnZ < IBigCube.DIAMETER_IN_SECTIONS; columnZ++) {
//                    cubeAbove.moveColumns(columnX, columnZ);
//                    if (chunk instanceof CubePrimer) {
//                        ((CubePrimer) chunk).moveColumns(columnX, columnZ);
//                    }
//                    noiseAndSurfaceBuilderHelper.moveColumn(columnX, columnZ);
//                    noiseAndSurfaceBuilderHelper.applySections();
//                }
//            }
//        }
    }

    @Inject(
        method = "method_20613",
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
            if (!protoCube.getCubeStatus().isOrAfter(status)) {
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
        protoCube.setCubeLightEngine(lightEngine);
        if (!protoCube.getCubeStatus().isOrAfter(status)) {
            // TODO: reimplement heightmaps
            //Heightmap.updateChunkHeightmaps(chunk, EnumSet
            //        .of(Heightmap.Type.MOTION_BLOCKING, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Heightmap.Type.OCEAN_FLOOR,
            //        Heightmap.Type.WORLD_SURFACE));

            CubeWorldGenRegion cubeWorldGenRegion = new CubeWorldGenRegion(level, unsafeCast(chunks), status, chunk, 1);
            StructureFeatureManager structureFeatureManager = level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion);
//            if (cubePrimer.getCubePos().getY() >= 0)
            protoCube.applyFeatureStates();
            //TODO: Features
//            generator.applyBiomeDecoration(cubeWorldGenRegion, chunk, level.structureFeatureManager().forWorldGenRegion(cubeWorldGenRegion));

//            ((CubeGenerator) generator).decorate(cubeWorldGenRegion, structureFeatureManager, (ProtoCube) chunk);
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
        boolean flag = ((ProtoCube) chunk).getCubeStatus().isOrAfter(status) && ((ProtoCube) chunk).hasCubeLight();
        if (!chunk.getStatus().isOrAfter(status)) {
            ((ProtoCube) chunk).updateCubeStatus(status);
        }
        cir.setReturnValue(unsafeCast(((CubicThreadedLevelLightEngine) lightEngine).lightCube((CubeAccess) chunk, flag).thenApply(Either::left)));
    }

    //lambda$static$12
    @Inject(
        method = "method_17033",
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
            for (int columnX = 0; columnX < CubeAccess.DIAMETER_IN_SECTIONS; columnX++) {
                for (int columnZ = 0; columnZ < CubeAccess.DIAMETER_IN_SECTIONS; columnZ++) {
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
