package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToMinBlock;
import static io.github.opencubicchunks.cc_core.utils.Coords.cubeToSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToMinBlock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRandom;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.levelgen.biome.BiomeGetter;
import io.github.opencubicchunks.cubicchunks.levelgen.biome.StripedBiomeSource;
import io.github.opencubicchunks.cubicchunks.levelgen.chunk.CubeGenerator;
import io.github.opencubicchunks.cubicchunks.levelgen.util.CubicWorldGenUtils;
import io.github.opencubicchunks.cubicchunks.levelgen.util.NonAtomicWorldgenRandom;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BiomeManagerAccess;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.OverworldBiomeSourceAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ChunkGenerator.class)
public abstract class MixinChunkGenerator implements CubeGenerator {

    @Mutable @Shadow @Final protected BiomeSource biomeSource;
    @Mutable @Shadow @Final protected BiomeSource runtimeBiomeSource;

    @Shadow public abstract Climate.Sampler climateSampler();

    @Shadow protected abstract List<StructurePlacement> getPlacementsForFeature(Holder<ConfiguredStructureFeature<?, ?>> holder);

    @Shadow @Nullable protected abstract BlockPos getNearestGeneratedStructure(BlockPos blockPos, ConcentricRingsStructurePlacement concentricRingsStructurePlacement);

    @Shadow public abstract Stream<Holder<StructureSet>> possibleStructureSets();

    @Shadow protected abstract Holder<Biome> adjustBiome(Holder<Biome> holder);

    // TODO: move this to debug mixins
    @Inject(
        method = "<init>(Lnet/minecraft/core/Registry;Ljava/util/Optional;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/biome/BiomeSource;J)V",
        at = @At("RETURN")
    )
    private void switchBiomeSource(Registry<StructureSet> registry, Optional<HolderSet<StructureSet>> optional, BiomeSource biomeSource, BiomeSource biomeSource2, long l, CallbackInfo ci) {
        if (System.getProperty("cubicchunks.debug.biomes", "false").equalsIgnoreCase("true")) {
            this.biomeSource = new StripedBiomeSource(this.biomeSource.possibleBiomes());
            this.runtimeBiomeSource = new StripedBiomeSource(this.runtimeBiomeSource.possibleBiomes());
        }
    }

    @Inject(
        method = "createStructures",
        at = @At("HEAD"),
        cancellable = true
    )
    private void createCubicStructures(RegistryAccess registryAccess, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, StructureManager structureManager, long seed, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        if (!(chunkAccess instanceof CubeAccess cube)) {
            return;
        }
        ci.cancel();

        //TODO: Patch entity game crashes in order to spawn villages(village pieces spawn villagers)
        //TODO: Setup a 2D and 3D placement.

        CubePos cubePos = cube.getCubePos();

        this.possibleStructureSets().forEach((structureSetHolder) -> {
            StructurePlacement placement = structureSetHolder.value().placement();
            List<StructureSet.StructureSelectionEntry> structures = structureSetHolder.value().structures();

            for (StructureSet.StructureSelectionEntry structure : structures) {
                StructureStart structureStart = structureFeatureManager.getStartForFeature(null, structure.structure().value(), cube);

                if (structureStart != null && structureStart.isValid()) {
                    return;
                }
            }

            if (isFeatureChunk(placement, cubePos, seed)) {
                if (structures.size() == 1) {
                    this.tryGenerateCCStructure(
                        structures.get(0),
                        structureFeatureManager,
                        registryAccess,
                        structureManager,
                        seed,
                        cube,
                        cubePos
                    );
                } else {
                    ArrayList<StructureSet.StructureSelectionEntry> arrayList = new ArrayList<>(structures.size());
                    arrayList.addAll(structures);
                    WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(0L));

                    ChunkPos chunkPos = cube.getPos();

                    worldgenRandom.setLargeFeatureSeed(seed, chunkPos.x, chunkPos.z);

                    int totalWeight = 0;
                    for (StructureSet.StructureSelectionEntry structure : arrayList) {
                        totalWeight += structure.weight();
                    }

                    while(!arrayList.isEmpty()) {
                        int j = worldgenRandom.nextInt(totalWeight);
                        int k = 0;

                        for(Iterator<StructureSet.StructureSelectionEntry> var17 = arrayList.iterator(); var17.hasNext(); ++k) {
                            StructureSet.StructureSelectionEntry structureSelectionEntry3 = var17.next();
                            j -= structureSelectionEntry3.weight();
                            if (j < 0) {
                                break;
                            }
                        }

                        StructureSet.StructureSelectionEntry structureSelectionEntry4 = arrayList.get(k);
                        if (this.tryGenerateCCStructure(structureSelectionEntry4, structureFeatureManager, registryAccess, structureManager, seed, cube, cubePos)) {
                            return;
                        }

                        arrayList.remove(k);
                        totalWeight -= structureSelectionEntry4.weight();
                    }
                }
            }
        });
    }

    private boolean tryGenerateCCStructure(StructureSet.StructureSelectionEntry structureSelectionEntry, StructureFeatureManager structureFeatureManager, RegistryAccess registryAccess,
                                        StructureManager structureManager, long seed, CubeAccess cube, CubePos cubePos) {
        ConfiguredStructureFeature<?, ?> configuredStructureFeature = structureSelectionEntry.structure().value();

        StructureStart baseStart = structureFeatureManager.getStartForFeature(null, configuredStructureFeature, cube);
        int numReferences = baseStart == null ? 0 : baseStart.getReferences();

        HolderSet<Biome> structureBiomes = configuredStructureFeature.biomes();

        Predicate<Holder<Biome>> placementCondition = (biomeHolder) -> {
            return structureBiomes.contains(this.adjustBiome(biomeHolder));
        };

        StructureStart structureStart = configuredStructureFeature.generate(
            registryAccess,
            Utils.unsafeCast(this),
            this.biomeSource,
            structureManager,
            seed,
            cubePos.asChunkPos(),
            numReferences,
            cube,
            placementCondition
        );

        if (structureStart.isValid()) {
            structureFeatureManager.setStartForFeature(null, configuredStructureFeature, structureStart, cube);
            return true;
        }

        return false;
    }

    protected boolean isFeatureChunk(StructurePlacement placement, CubePos cubePos, long seed) {
        if (placement instanceof RandomSpreadStructurePlacement spread) {
            CubePos placementPos = getPotentialFeatureCube(
                spread,
                seed,
                SectionPos.blockToSectionCoord(cubePos.getX()),
                SectionPos.blockToSectionCoord(cubePos.getY()),
                SectionPos.blockToSectionCoord(cubePos.getZ())
            );

            return cubePos.equals(placementPos);
        }

        ChunkPos chunkPos = cubePos.asChunkPos();

        return placement.isFeatureChunk(
            Utils.unsafeCast(this),
            seed,
            chunkPos.x,
            chunkPos.z
        );
    }

    // TODO: check which one is which
    /*@Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    public void onGenerateStructures(RegistryAccess registry, StructureFeatureManager featureManager, ChunkAccess chunkAccess, StructureManager manager, long seed, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        if (!(chunkAccess instanceof CubeAccess cube)) {
            return;
        }
        ci.cancel();

        //TODO: Patch entity game crashes in order to spawn villages(village pieces spawn villagers)
        //TODO: Setup a 2D and 3D placement.
        CubePos cubePos = cube.getCubePos();
        Biome biome = this.biomeSource.getPrimaryBiome(cube.getCubePos().asChunkPos());
        this.createCCStructure(StructureFeatures.STRONGHOLD, registry, featureManager, cube, manager, seed, cubePos, biome);

        for (Supplier<ConfiguredStructureFeature<?, ?>> configuredStructureFeatureSupplier : biome.getGenerationSettings().structures()) {
            this.createCCStructure(configuredStructureFeatureSupplier.get(), registry, featureManager, cube, manager, seed, cubePos, biome);
        }
    }

    private void createCCStructure(ConfiguredStructureFeature<?, ?> configuredStructureFeature, RegistryAccess registryAccess, StructureFeatureManager structureFeatureManager,
                                   CubeAccess cube, StructureManager structureManager, long seed, CubePos cubePos, Biome biome) {
        StructureStart<?> structureStart = structureFeatureManager
            .getStartForFeature(/*SectionPos.of(cube.getPos(), 0) We return null as a sectionPos Arg is not used in the method*//*null, configuredStructureFeature.feature, cube);
        int i = structureStart != null ? structureStart.getReferences() : 0;
        StructureFeatureConfiguration structureFeatureConfiguration = this.settings.getConfig(configuredStructureFeature.feature);
        if (structureFeatureConfiguration != null) {
            StructureStart<?> structureStart2 = configuredStructureFeature
                .generate(registryAccess, ((ChunkGenerator) (Object) this), this.biomeSource, structureManager, seed, cubePos.asChunkPos(), biome, i, structureFeatureConfiguration, cube);
            structureFeatureManager
                .setStartForFeature(/* SectionPos.of(cube.getPos(), 0) We return null as a sectionPos Arg is not used in the method*//*null, configuredStructureFeature.feature,
                    structureStart2, cube);
        }

    }*/

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    public void createReferences(WorldGenLevel worldGenLevel, StructureFeatureManager featureManager, ChunkAccess chunkAccess, CallbackInfo ci) {
        if (((CubicLevelHeightAccessor) chunkAccess).generates2DChunks()) {
            return;
        }
        if (!(chunkAccess instanceof CubeAccess cube)) {
            return;
        }
        ci.cancel();

        CubeWorldGenRegion world = (CubeWorldGenRegion) worldGenLevel;

        int cubeX = world.getMainCubeX();
        int cubeY = world.getMainCubeY();
        int cubeZ = world.getMainCubeZ();

        int blockX = cubeToMinBlock(cubeX);
        int blockY = cubeToMinBlock(cubeY);
        int blockZ = cubeToMinBlock(cubeZ);

        BoundingBox cubeBounds = new BoundingBox(
            blockX, blockY, blockZ,
            blockX + CubeAccess.DIAMETER_IN_BLOCKS - 1,
            blockY + CubeAccess.DIAMETER_IN_BLOCKS - 1,
            blockZ + CubeAccess.DIAMETER_IN_BLOCKS - 1
        );

        for (int x = cubeX - 8 / CubeAccess.DIAMETER_IN_SECTIONS; x <= cubeX + 8 / CubeAccess.DIAMETER_IN_SECTIONS; ++x) {
            for (int y = cubeY - 8 / CubeAccess.DIAMETER_IN_SECTIONS; y <= cubeY + 8 / CubeAccess.DIAMETER_IN_SECTIONS; ++y) {
                for (int z = cubeZ - 8 / CubeAccess.DIAMETER_IN_SECTIONS; z <= cubeZ + 8 / CubeAccess.DIAMETER_IN_SECTIONS; ++z) {
                    long cubePosAsLong = CubePos.asLong(x, y, z);

                    for (StructureStart structureStart : world.getCube(CubePos.of(x, y, z)).getAllCubeStructureStarts().values()) {
                        try {
                            if (structureStart != StructureStart.INVALID_START && structureStart.getBoundingBox().intersects(cubeBounds)) {
                                //The First Param is a SectionPos arg that is not used anywhere so we make it null.
                                featureManager.addReferenceForFeature(null, structureStart.getFeature(), cubePosAsLong, cube);
                                DebugPackets.sendStructurePacket(world, structureStart);
                            }
                        } catch (Exception e) {
                            CrashReport crashReport = CrashReport.forThrowable(e, "Generating structure reference");
                            CrashReportCategory crashReportCategory = crashReport.addCategory("Structure");

                            Registry<ConfiguredStructureFeature<?, ?>> registry = worldGenLevel.registryAccess().registryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY);

                            crashReportCategory.setDetail("Id", () -> registry.getKey(structureStart.getFeature()).toString());
                            //crashReportCategory.setDetail("Name", () -> structureStart.getFeature().getFeatureName());
                            crashReportCategory.setDetail("Class", () -> structureStart.getFeature().getClass().getCanonicalName());
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    @Inject(
        method = "findNearestMapFeature",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/HolderSet;iterator()Ljava/util/Iterator;",
            ordinal = 0
        ),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    //FIXME: Is this actually changing anything?
    public void findNearestMapFeature3D(ServerLevel serverLevel, HolderSet<ConfiguredStructureFeature<?, ?>> structures, BlockPos center, int radius, boolean skipExistingChunks,
                                        CallbackInfoReturnable<Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>>> cir, Set<Holder<Biome>> structureBiomes,
                                        Set<Holder<Biome>> possibleBiomes, Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>> nearestStructure,
                                        double nearestDistance, Map<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> structuresPerPlacement) {
        if (((CubicLevelHeightAccessor) serverLevel).generates2DChunks()) {
            return;
        }

        for (Holder<ConfiguredStructureFeature<?, ?>> structure : structures) {
            if (possibleBiomes.stream().anyMatch(structure.value().biomes()::contains)) {
                for (StructurePlacement structurePlacement : this.getPlacementsForFeature(structure)) {
                    structuresPerPlacement.computeIfAbsent(
                        structurePlacement,
                        (placement) -> new ObjectArraySet<>()
                    ).add(structure);
                }
            }
        }

        List<Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>>> randomSpreadEntries = new ArrayList<>(structuresPerPlacement.size());

        for (Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> structurePlacementSetEntry : structuresPerPlacement.entrySet()) {
            StructurePlacement structurePlacement = structurePlacementSetEntry.getKey();
            if (structurePlacement instanceof ConcentricRingsStructurePlacement concentricRingsStructurePlacement) {
                BlockPos nearestRingPos = this.getNearestGeneratedStructure(center, concentricRingsStructurePlacement);
                double distance = center.distSqr(nearestRingPos);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestStructure = Pair.of(nearestRingPos, structurePlacementSetEntry.getValue().iterator().next());
                }
            } else if (structurePlacement instanceof RandomSpreadStructurePlacement) {
                randomSpreadEntries.add(structurePlacementSetEntry);
            }
        }

        if (!randomSpreadEntries.isEmpty()) {
            int sectionX = SectionPos.blockToSectionCoord(center.getX());
            int sectionY = SectionPos.blockToSectionCoord(center.getY());
            int sectionZ = SectionPos.blockToSectionCoord(center.getZ());

            for (int distanceFromCenter = 0; distanceFromCenter <= radius; ++distanceFromCenter) {
                boolean found = false;

                for (Map.Entry<StructurePlacement, Set<Holder<ConfiguredStructureFeature<?, ?>>>> structurePlacementSetEntry : randomSpreadEntries) {
                    RandomSpreadStructurePlacement randomSpreadStructurePlacement = (RandomSpreadStructurePlacement) structurePlacementSetEntry.getKey();
                    Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>> potentialNearest =
                        getNearestGeneratedStructure3D(
                            structurePlacementSetEntry.getValue(),
                            serverLevel,
                            serverLevel.structureFeatureManager(),
                            sectionX,
                            sectionY,
                            sectionZ,
                            distanceFromCenter,
                            skipExistingChunks,
                            serverLevel.getSeed(),
                            randomSpreadStructurePlacement
                        );
                    if (potentialNearest != null) {
                        found = true;
                        double distance = center.distSqr(potentialNearest.getFirst());
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestStructure = potentialNearest;
                        }
                    }
                }

                if (found) {
                    cir.setReturnValue(nearestStructure);
                    return;
                }
            }
        }

        cir.setReturnValue(nearestStructure);
    }

    @Nullable
    private Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>> getNearestGeneratedStructure3D(
        Set<Holder<ConfiguredStructureFeature<?, ?>>> structures, ServerLevel serverLevel,
        StructureFeatureManager structureFeatureManager, int sectionX, int sectionY, int sectionZ,
        int distanceFromCenter, boolean skipExistingChunks, long seed,
        RandomSpreadStructurePlacement randomSpreadStructurePlacement
    ) {
        int spacing = randomSpreadStructurePlacement.spacing();

        int yRadius = Math.min(distanceFromCenter, 4);

        for (int xo = -distanceFromCenter; xo <= distanceFromCenter; xo++) {
            boolean isEdgeX = xo == -distanceFromCenter || xo == distanceFromCenter;

            for (int yo = -yRadius; yo <= yRadius; yo++) {
                boolean isEdgeY = yo == -yRadius || yo == yRadius;

                for (int zo = -distanceFromCenter; zo <= distanceFromCenter; zo++) {
                    boolean isEdgeZ = zo == -distanceFromCenter || zo == distanceFromCenter;

                    if (!isEdgeX && !isEdgeY && !isEdgeZ) {
                        continue;
                    }

                    int xPos = sectionX + spacing * xo;
                    int yPos = sectionY + spacing * yo;
                    int zPos = sectionZ + spacing * zo;

                    CubePos potentialCube = this.getPotentialFeatureCube(randomSpreadStructurePlacement, seed, xPos, yPos, zPos);

                    for (Holder<ConfiguredStructureFeature<?, ?>> structure : structures) {
                        //TODO: Don't turn potentialCube into a ChunkPos as below. Instead the structure checks should run for the actual cube
                        StructureCheckResult result = structureFeatureManager.checkStructurePresence(potentialCube.asChunkPos(), structure.value(), skipExistingChunks);

                        if (result != StructureCheckResult.START_NOT_PRESENT) {
                            if (!skipExistingChunks && result == StructureCheckResult.START_PRESENT) {
                                return Pair.of(StructureFeature.getLocatePos(randomSpreadStructurePlacement, potentialCube.asChunkPos()), structure);
                            }

                            CubeAccess cubeAccess = ((CubicLevelAccessor) serverLevel).getCube(potentialCube, ChunkStatus.STRUCTURE_STARTS);

                            StructureStart start = structureFeatureManager.getStartForFeature(null /*This isn't used in the method*/, structure.value(), cubeAccess);

                            if (start != null && start.isValid()) {
                                if (skipExistingChunks && start.canBeReferenced()) {
                                    structureFeatureManager.addReference(start);
                                    return Pair.of(StructureFeature.getLocatePos(randomSpreadStructurePlacement, potentialCube.asChunkPos()), structure);
                                }

                                if (!skipExistingChunks) {
                                    return Pair.of(StructureFeature.getLocatePos(randomSpreadStructurePlacement, potentialCube.asChunkPos()), structure);
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    protected CubePos getPotentialFeatureCube(RandomSpreadStructurePlacement spread, long seed, int sectionX, int sectionY, int sectionZ) {
        int spacing = spread.spacing();
        int separation = spread.separation();

        int baseX = Math.floorDiv(sectionX, spacing);
        //int baseY = Math.floorDiv(sectionY, spacing);
        int baseZ = Math.floorDiv(sectionZ, spacing);

        WorldgenRandom random = new NonAtomicWorldgenRandom(0L);
        random.setLargeFeatureWithSalt(seed, sectionX, sectionY, spread.salt());

        int diff = spacing - separation;

        int xShift = spread.spreadType().evaluate(random, diff);
        int zShift = spread.spreadType().evaluate(random, diff);

        int x = baseX * spacing + xShift;
        int z = baseZ * spacing + zShift;

        //TODO: 3D Structure Placement? (Also, if two cubes in the same column are a potential feature cube won't the structure generate twice?)
        return CubePos.of(
            Coords.sectionToCube(x),
            Coords.sectionToCube(sectionY),
            Coords.sectionToCube(z)
        );
    }

    @Override
    public void decorate(CubeWorldGenRegion region, StructureFeatureManager structureManager, ProtoCube cube) {
        int mainCubeX = region.getMainCubeX();
        int mainCubeY = region.getMainCubeY();
        int mainCubeZ = region.getMainCubeZ();

        int xStart = cubeToMinBlock(mainCubeX);
        int yStart = cubeToMinBlock(mainCubeY);
        int zStart = cubeToMinBlock(mainCubeZ);

        //Y value stays 32
        CubeWorldGenRandom worldgenRandom = new CubeWorldGenRandom();

        //Get each individual column from a given cube no matter the size. Where y height is the same per column.
        //Feed the given columnMinPos into the feature decorators.
        int cubeY = cube.getCubePos().getY();
        for (int columnX = 0; columnX < CubeAccess.DIAMETER_IN_SECTIONS; columnX++) {
            for (int columnZ = 0; columnZ < CubeAccess.DIAMETER_IN_SECTIONS; columnZ++) {
                cube.moveColumns(columnX, columnZ);
                if (CubicWorldGenUtils.areSectionsEmpty(cubeY, cube.getPos(), cube)) {
                    continue;
                }

                BlockPos columnMinPos = new BlockPos(xStart + (sectionToMinBlock(columnX)), yStart, zStart + (sectionToMinBlock(columnZ)));

                long seed = worldgenRandom.setDecorationSeed(region.getSeed(), columnMinPos.getX(), columnMinPos.getY(), columnMinPos.getZ());

                Holder<Biome> biome = ((ChunkGenerator) (Object) this).getBiomeSource().getNoiseBiome(
                    QuartPos.fromSection(cubeToSection(mainCubeX, columnX)) + BiomeManagerAccess.getChunkCenterQuart(),
                    QuartPos.fromSection(cubeToSection(mainCubeY, 0)) + BiomeManagerAccess.getChunkCenterQuart() * CubeAccess.DIAMETER_IN_SECTIONS,
                    QuartPos.fromSection(cubeToSection(mainCubeZ, columnZ)) + BiomeManagerAccess.getChunkCenterQuart(),
                    this.climateSampler()
                );
                try {
                    ((BiomeGetter) (Object) biome.value()).generate(structureManager, ((ChunkGenerator) (Object) this), region, seed, worldgenRandom, columnMinPos);
                } catch (Exception e) {
                    CrashReport crashReport = CrashReport.forThrowable(e, "Biome decoration");
                    crashReport.addCategory("Generation").setDetail("CubeX", mainCubeX).setDetail("CubeY", mainCubeY).setDetail("CubeZ", mainCubeZ).setDetail("Seed", seed)
                        .setDetail("Biome", biome);
                    throw new ReportedException(crashReport);
                }
            }
        }
    }

    // replace with non-atomic random for optimized random number generation
    /*@Redirect(method = "applyCarvers", at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/WorldgenRandom"))
    private WorldgenRandom createCarverRandom() {
        return new NonAtomicWorldgenRandom();
    }

    @Redirect(method = "applyCarvers", at = @At(value = "NEW", target = "net/minecraft/world/level/levelgen/carver/CarvingContext"))
    private CarvingContext cubicContext(ChunkGenerator chunkGenerator, LevelHeightAccessor accessor, long seed, BiomeManager access, ChunkAccess chunk, GenerationStep.Carving carver) {
        return new CubicCarvingContext(chunkGenerator, chunk);
    }*/
}
