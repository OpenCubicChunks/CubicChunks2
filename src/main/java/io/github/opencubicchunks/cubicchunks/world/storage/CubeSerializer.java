package io.github.opencubicchunks.cubicchunks.world.storage;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.ChunkIoMainThreadTaskUtils;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkSerializerAccess;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ProtoChunkTicksAccess;
import io.github.opencubicchunks.cubicchunks.world.CubicServerTickList;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ImposterProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.storage.AsyncSaveData;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.storage.PoiDeserializationContext;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLevelLightEngine;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.slf4j.Logger;

public class CubeSerializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static ProtoCube read(ServerLevel serverLevel, StructureManager structureManager, PoiManager poiManager, CubePos expectedCubePos, CompoundTag root) {
        CubePos cubePos = CubePos.of(root.getInt("xPos"), root.getInt("yPos"), root.getInt("zPos"));
        if (!Objects.equals(cubePos, expectedCubePos)) {
            CubicChunks.LOGGER.error("LevelCube file at {} is in the wrong location; relocating. (Expected {}, got {})", cubePos, expectedCubePos, cubePos);
        }

        UpgradeData upgradeData = UpgradeData.EMPTY; //TODO: read upgrade data

        boolean isLightOn = root.getBoolean("isLightOn");
        boolean hasSkyLight = serverLevel.dimensionType().hasSkyLight();

        ListTag sectionsData = root.getList("sections", 10);
        final int numSections = CubeAccess.SECTION_COUNT;
        LevelChunkSection[] sections = new LevelChunkSection[numSections];

        ChunkSource chunkSource = serverLevel.getChunkSource();
        LevelLightEngine lightEngine = chunkSource.getLightEngine();

        if (isLightOn) {
            ((CubicLevelLightEngine) lightEngine).retainData(cubePos, true);
        }

        Registry<Biome> biomeRegistry = serverLevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        Codec<PalettedContainer<Holder<Biome>>> biomePaletteCodec = makeBiomePaletteCodec(biomeRegistry);

        for (int i = 0; i < sectionsData.size(); ++i) {
            CompoundTag sectionData = sectionsData.getCompound(i);
            int sectionIndex = sectionData.getInt("i");

            SectionPos sectionPos = Coords.sectionPosByIndex(cubePos, sectionIndex);
            if (sectionIndex < 0 || sectionIndex >= numSections) {
                CubicChunks.LOGGER.error("Invalid section index {} in chunk at {}", sectionIndex, cubePos);
            } else {
                PalettedContainer<BlockState> blocks;
                if (sectionData.contains("block_states")) {
                    blocks = ChunkSerializerAccess.getBLOCK_STATE_CODEC().parse(NbtOps.INSTANCE, sectionData.getCompound("block_states"))
                        .promotePartial((error) -> logErrors(cubePos, sectionIndex, error))
                        .getOrThrow(false, LOGGER::error);
                } else {
                    blocks = new PalettedContainer<>(
                        Block.BLOCK_STATE_REGISTRY,
                        Blocks.AIR.defaultBlockState(),
                        PalettedContainer.Strategy.SECTION_STATES
                    );
                }

                PalettedContainer<Holder<Biome>> biomes;
                if (sectionData.contains("biomes")) {
                    biomes = biomePaletteCodec.parse(NbtOps.INSTANCE, sectionData.getCompound("biomes"))
                        .promotePartial((error) -> logErrors(cubePos, sectionIndex, error))
                        .getOrThrow(false, LOGGER::error);
                } else {
                    biomes = new PalettedContainer<>(
                        biomeRegistry.asHolderIdMap(),
                        biomeRegistry.getHolderOrThrow(Biomes.PLAINS),
                        PalettedContainer.Strategy.SECTION_BIOMES
                    );
                }

                LevelChunkSection section = new LevelChunkSection(
                    sectionPos.getY(),
                    blocks,
                    biomes
                );

                sections[sectionIndex] = section;
                ChunkIoMainThreadTaskUtils.executeMain(
                    () -> ((PoiDeserializationContext) poiManager).checkConsistencyWithBlocksForCube(sectionPos, section)
                );
            }

            if (isLightOn) {
                if (sectionData.contains("BlockLight")) {
                    lightEngine.queueSectionData(
                        LightLayer.BLOCK,
                        sectionPos,
                        new DataLayer(sectionData.getByteArray("BlockLight")),
                        true
                    );
                }

                if (hasSkyLight && sectionData.contains("SkyLight")) {
                    lightEngine.queueSectionData(
                        LightLayer.SKY,
                        sectionPos,
                        new DataLayer(sectionData.getByteArray("SkyLight")),
                        true
                    );
                }
            }
        }

        long inhabitedTime = root.getLong("InhabitedTime");

        ChunkStatus.ChunkType chunkType = ChunkSerializer.getChunkTypeFromTag(root);

        BlendingData blendingData = null; //TODO: Blending? Probably not.

        CubeAccess cube;

        if (chunkType == ChunkStatus.ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> blockTicks = loadLevelCubeTicks(root.getList("block_ticks", 10), (id) -> {
                return Registry.BLOCK.getOptional(ResourceLocation.tryParse(id));
            }, cubePos);

            LevelChunkTicks<Fluid> fluidTicks = loadLevelCubeTicks(root.getList("fluid_ticks", 10), (id) -> {
                return Registry.FLUID.getOptional(ResourceLocation.tryParse(id));
            }, cubePos);

            cube = new LevelCube(
                serverLevel.getLevel(),
                cubePos,
                upgradeData,
                blockTicks,
                fluidTicks,
                inhabitedTime,
                sections,
                blendingData,
                c -> readEntities(serverLevel, root, c)
            );

            //TODO: reimplement forge capabilities in save format
//                if (level.contains("ForgeCaps")) ((LevelChunk)cube).readCapsFromNBT(level.getCompound("ForgeCaps"));
        } else {
            ProtoChunkTicks<Block> blockTicks = loadProtoCubeTicks(root.getList("block_ticks", 10), (id) -> {
                return Registry.BLOCK.getOptional(ResourceLocation.tryParse(id));
            }, cubePos);

            ProtoChunkTicks<Fluid> fluidTicks = loadProtoCubeTicks(root.getList("fluid_ticks", 10), (id) -> {
                return Registry.FLUID.getOptional(ResourceLocation.tryParse(id));
            }, cubePos);

            ProtoCube protoCube = new ProtoCube(
                cubePos,
                upgradeData,
                sections,
                blockTicks,
                fluidTicks,
                serverLevel,
                biomeRegistry,
                blendingData
            );
            cube = protoCube;

            protoCube.setInhabitedTime(inhabitedTime);

            //No below zero retrogen because this is CC

            protoCube.setCubeStatus(ChunkStatus.byName(root.getString("Status")));

            if (protoCube.getCubeStatus().isOrAfter(ChunkStatus.FEATURES)) {
                protoCube.setCubeLightEngine(lightEngine);
            }

            //if (!isLightOn && cubePrimer.getCubeStatus().isOrAfter(ChunkStatus.LIGHT)) {
            //    for (BlockPos blockpos : BlockPos.betweenClosed(cubePos.minCubeX(), cubePos.minCubeY(), cubePos.minCubeZ(), cubePos.maxCubeX(), cubePos.maxCubeY(), cubePos.maxCubeZ())) {
            //        if (cube.getBlockState(blockpos).getLightEmission() != 0) {
            //            //TODO: reimplement light positions in save format
            //                cubePrimer.addLightPosition(blockpos);
            //        }
            //    }
            //}
        }

        cube.setCubeLight(isLightOn);

        CompoundTag structures = root.getCompound("structures");
        cube.setAllStarts(ChunkSerializerAccess.invokeUnpackStructureStart(
            StructurePieceSerializationContext.fromLevel(serverLevel),
            structures,
            serverLevel.getSeed()
        ));
        cube.setAllReferences(unpackCubeStructureReferences(serverLevel.registryAccess(), new ImposterChunkPos(cubePos), structures));
        if (root.getBoolean("shouldSave")) {
            cube.setDirty(true);
        }

        ListTag postProcessingNBTList = root.getList("PostProcessing", 9);

        for (int l1 = 0; l1 < postProcessingNBTList.size(); ++l1) {
            ListTag listTag1 = postProcessingNBTList.getList(l1);

            for (int l = 0; l < listTag1.size(); ++l) {
                cube.addPackedPostProcess(listTag1.getShort(l), l1);
            }
        }

        if (chunkType == ChunkStatus.ChunkType.LEVELCHUNK) {
            //TODO: reimplement forge chunk load event
//                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.serverLevel.ChunkDataEvent.Load(cube, level, chunkstatus$type));
            return new ImposterProtoCube((LevelCube) cube, false);
        } else {
            ProtoCube protoCube = (ProtoCube) cube;
            ListTag entitiesNBT = root.getList("entities", 10);

            for (int i2 = 0; i2 < entitiesNBT.size(); ++i2) {
                protoCube.addCubeEntity(entitiesNBT.getCompound(i2));
            }

            ListTag tileEntitiesNBTList = root.getList("block_entities", 10);

            for (int i1 = 0; i1 < tileEntitiesNBTList.size(); ++i1) {
                CompoundTag tileEntityNBT = tileEntitiesNBTList.getCompound(i1);
                cube.setCubeBlockEntity(tileEntityNBT);
            }

            ListTag lightsNBTList = root.getList("Lights", 9);

            for (int j2 = 0; j2 < lightsNBTList.size(); ++j2) {
                ListTag lightList = lightsNBTList.getList(j2);

                for (int j1 = 0; j1 < lightList.size(); ++j1) {
                    protoCube.addCubeLight(lightList.getShort(j1), j2);
                }
            }

            CompoundTag carvingMasksNbt = root.getCompound("CarvingMasks");
            for (String key : carvingMasksNbt.getAllKeys()) {
                GenerationStep.Carving carvingStage = GenerationStep.Carving.valueOf(key);
                protoCube.setCarvingMask(
                    carvingStage,
                    new CarvingMask(
                        carvingMasksNbt.getLongArray(key),
                        cube.getMinBuildHeight()
                    )
                );
            }

            ListTag featurestates = root.getList("featurestates", CompoundTag.TAG_COMPOUND);
            featurestates.forEach((tag) -> {
                CompoundTag compoundTag = (CompoundTag) tag;
                protoCube.setFeatureBlocks(new BlockPos(compoundTag.getInt("x"), compoundTag.getInt("y"), compoundTag.getInt("z")),
                    Block.BLOCK_STATE_REGISTRY.byId(compoundTag.getInt("s")));
            });

            //TODO: reimplement forge ChunkDataEvent
//                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.serverLevel.ChunkDataEvent.Load(cube, level, chunkstatus$type));

            return protoCube;
        }
    }

    private static Codec<PalettedContainer<Holder<Biome>>> makeBiomePaletteCodec(Registry<Biome> registry) {
        return PalettedContainer.codec(registry.asHolderIdMap(), registry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, registry.getHolderOrThrow(Biomes.PLAINS));
    }

    private static void logErrors(CubePos cubePos, int i, String string) {
        LOGGER.error("Recoverable errors when loading section [" + cubePos.getX() + ", " + cubePos.getY() + ", " + cubePos.getZ() + ", " + i + "]: " + string);
    }

    public static CompoundTag write(ServerLevel serverLevel, CubeAccess cube, AsyncSaveData data) {
        CubePos pos = cube.getCubePos();

        CompoundTag root = new CompoundTag();

        root.putInt("xPos", pos.getX());
        root.putInt("yPos", pos.getY());
        root.putInt("zPos", pos.getZ());

        root.putLong("LastUpdate", serverLevel.getGameTime());
        root.putLong("InhabitedTime", cube.getCubeInhabitedTime());
        root.putString("Status", cube.getCubeStatus().getName());

        LevelChunkSection[] sections = cube.getCubeSections();
        ListTag sectionsNBTList = new ListTag();
        LevelLightEngine lightEngine = serverLevel.getChunkSource().getLightEngine();
        Registry<Biome> biomeRegistry = serverLevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        Codec<PalettedContainer<Holder<Biome>>> palettedBiomeContainerCodec = makeBiomePaletteCodec(biomeRegistry);
        boolean cubeHasLight = cube.hasCubeLight();

        for (int i = 0; i < CubeAccess.SECTION_COUNT; ++i) {
            LevelChunkSection section = sections[i];

            DataLayer blockData = data != null ? data.blockLight.get(Coords.sectionPosByIndex(pos, i)) :
                lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(Coords.sectionPosByIndex(pos, i));
            DataLayer skyData = data != null ? data.skyLight.get(Coords.sectionPosByIndex(pos, i)) :
                lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(Coords.sectionPosByIndex(pos, i));
            CompoundTag sectionNBT = new CompoundTag();
            if (blockData != null || skyData != null) {
                if (blockData != null && !blockData.isEmpty()) {
                    sectionNBT.putByteArray("BlockLight", blockData.getData());
                }

                if (skyData != null && !skyData.isEmpty()) {
                    sectionNBT.putByteArray("SkyLight", skyData.getData());
                }
            }

            sectionNBT.put(
                "block_states",
                ChunkSerializerAccess.getBLOCK_STATE_CODEC().encodeStart(
                    NbtOps.INSTANCE,
                    section.getStates()
                ).getOrThrow(
                    false,
                    LOGGER::error
                )
            );

            sectionNBT.put(
                "biomes",
                palettedBiomeContainerCodec.encodeStart(
                    NbtOps.INSTANCE,
                    section.getBiomes()
                ).getOrThrow(
                    false,
                    LOGGER::error
                )
            );

            sectionNBT.putShort("i", (byte) (i));

            sectionsNBTList.add(sectionNBT);
        }

        root.put("sections", sectionsNBTList);

        if (cubeHasLight) {
            root.putBoolean("isLightOn", true);
        }

        ListTag tileEntitiesNBTList = new ListTag();

        if (data != null) {
            data.blockEntities.forEach((blockPos, blockEntity) -> {
                CompoundTag tileEntityNBT = cube.getCubeBlockEntityNbtForSaving(blockPos);
                if (tileEntityNBT != null) {
                    tileEntitiesNBTList.add(tileEntityNBT);
                }
            });
            data.blockEntitiesDeferred.forEach((blockPos, tag) -> {
                if (cube instanceof LevelCube) tag.putBoolean("keepPacked", true);
                tileEntitiesNBTList.add(tag);
            });
        } else {
            for (BlockPos blockpos : cube.getCubeBlockEntitiesPos()) {
                CompoundTag tileEntityNBT = cube.getCubeBlockEntityNbtForSaving(blockpos);
                if (tileEntityNBT != null) {
                    CubicChunks.LOGGER.debug("Saving block entity at " + blockpos.toString());
                    tileEntitiesNBTList.add(tileEntityNBT);
                }
            }
        }

        root.put("block_entities", tileEntitiesNBTList);
        if (cube.getCubeStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK) {
            ProtoCube protoCube = (ProtoCube) cube;
            ListTag listTag3 = new ListTag();
            listTag3.addAll(protoCube.getCubeEntities());
            root.put("entities", listTag3);
//            level.put("Lights", packOffsets(cubePrimer.getPackedLights()));

            CompoundTag carvingMasksNBT = new CompoundTag();
            GenerationStep.Carving[] carvingSteps = GenerationStep.Carving.values();

            for (GenerationStep.Carving carving : carvingSteps) {
                CarvingMask carvingMask = protoCube.getCarvingMask(carving);
                if (carvingMask != null) {
                    carvingMasksNBT.putLongArray(carving.toString(), carvingMask.toArray());
                }
            }

            root.put("CarvingMasks", carvingMasksNBT);

            ListTag featuresStates = new ListTag();
            protoCube.getFeaturesStateMap().forEach(((pos1, state) -> {
                CompoundTag tag = new CompoundTag();
                tag.putInt("x", pos.getX());
                tag.putInt("y", pos.getY());
                tag.putInt("z", pos.getZ());
                tag.putInt("s", Block.BLOCK_STATE_REGISTRY.getId(state));
                featuresStates.add(tag);
            }));
            root.put("featurestates", featuresStates);
        }

        long gameTime = serverLevel.getLevelData().getGameTime();
        root.put(
            "block_ticks",
            cube.getTicksForSerialization().blocks().save(gameTime, (block) -> Registry.BLOCK.getKey(block).toString())
        );
        root.put(
            "fluid_ticks",
            cube.getTicksForSerialization().fluids().save(gameTime, (fluid) -> Registry.FLUID.getKey(fluid).toString())
        );

        root.put("PostProcessing", ChunkSerializer.packOffsets(cube.getPostProcessing()));
//        CompoundTag compoundnbt6 = new CompoundTag();
//
        //TODO: reimplement heightmaps
//        for(Map.Entry<Heightmap.Type, Heightmap> entry : cube.getHeightmaps()) {
//            if (cube.getCubeStatus().getHeightMaps().contains(entry.getKey())) {
//                compoundnbt6.put(entry.getKey().getId(), new LongArrayNBT(entry.getValue().getDataArray()));
//            }
//        }
//
//        level.put("Heightmaps", compoundnbt6);
        root.put(
            "structures",
            ChunkSerializerAccess.invokePackStructureData(
                StructurePieceSerializationContext.fromLevel(serverLevel),
                new ImposterChunkPos(cube.getCubePos()),
                cube.getAllStarts(),
                cube.getAllReferences())
        );

        return root;
    }

    public static ChunkStatus.ChunkType getChunkStatus(@Nullable CompoundTag chunkNBT) {
        if (chunkNBT != null) {
            ChunkStatus chunkstatus = ChunkStatus.byName(chunkNBT.getCompound("Level").getString("Status"));
            if (chunkstatus != null) {
                return chunkstatus.getChunkType();
            }
        }
        return ChunkStatus.ChunkType.PROTOCHUNK;
    }

    private static void readEntities(ServerLevel serverLevel, CompoundTag compound, LevelCube cube) {
        if (compound.contains("entities", 9)) {
            ListTag entitiesTag = compound.getList("entities", 10);
            if (!entitiesTag.isEmpty()) {
                serverLevel.addLegacyChunkEntities(EntityType.loadEntitiesRecursive(entitiesTag, serverLevel));
            }
        }

        ListTag blockEntitiesNbt = compound.getList("block_entities", 10);
        for (int j = 0; j < blockEntitiesNbt.size(); ++j) {
            CompoundTag beNbt = blockEntitiesNbt.getCompound(j);
            boolean flag = beNbt.getBoolean("keepPacked");
            if (flag) {
                cube.setCubeBlockEntity(beNbt);
            } else {
                BlockPos blockpos = new BlockPos(beNbt.getInt("x"), beNbt.getInt("y"), beNbt.getInt("z"));
                BlockEntity blockEntity = BlockEntity.loadStatic(blockpos, cube.getBlockState(blockpos), beNbt);
                if (blockEntity != null) {
                    cube.setCubeBlockEntity(blockEntity);
                }
            }
        }
    }

    private static Map<ConfiguredStructureFeature<?, ?>, LongSet> unpackCubeStructureReferences(RegistryAccess registryAccess, ChunkPos pos, CompoundTag nbt) {
        Map<ConfiguredStructureFeature<?, ?>, LongSet> map = Maps.newHashMap();
        CompoundTag compoundTag = nbt.getCompound("References");
        Registry<ConfiguredStructureFeature<?, ?>> registry = registryAccess.registryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY);

        for (String nbtKey : compoundTag.getAllKeys()) {
            ResourceLocation key = ResourceLocation.tryParse(nbtKey);
            map.put(registry.get(key), new LongOpenHashSet(Arrays.stream(compoundTag.getLongArray(nbtKey)).filter((packedPos) -> {
                ChunkPos chunkPos2 = new ImposterChunkPos(CubePos.from(packedPos));
                if (chunkPos2.getChessboardDistance(pos) > 8) {
                    CubicChunks.LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", nbtKey, chunkPos2, pos);
                    return false;
                } else {
                    return true;
                }
            }).toArray()));
        }

        return map;
    }

    public static <T> void loadCubeTickList(ListTag listTag, Function<String, Optional<T>> lookup, CubePos cubePos, Consumer<SavedTick<T>> consumer) {
        long target = cubePos.asLong();

        for (int i = 0; i < listTag.size(); ++i) {
            CompoundTag compoundTag = listTag.getCompound(i);
            loadCubeTick(compoundTag, lookup).ifPresent((tick) -> {
                if (CubePos.asLong(tick.pos()) == target) {
                    consumer.accept(tick);
                }
            });
        }
    }

    private static <T> Optional<SavedTick<T>> loadCubeTick(CompoundTag tag, Function<String, Optional<T>> function) {
        return SavedTick.loadTick(tag, function);
    }

    private static <T> LevelChunkTicks<T> loadLevelCubeTicks(ListTag listTag, Function<String, Optional<T>> lookup, CubePos cubePos) {
        ImmutableList.Builder<SavedTick<T>> builder = ImmutableList.builder();

        loadCubeTickList(listTag, lookup, cubePos, builder::add);

        return new LevelChunkTicks<>(builder.build());
    }

    private static <T> ProtoChunkTicks<T> loadProtoCubeTicks(ListTag listTag, Function<String, Optional<T>> lookup, CubePos cubePos) {
        ProtoChunkTicks<T> ticks = new ProtoChunkTicks<>();

        loadCubeTickList(listTag, lookup, cubePos, (tick) -> ((ProtoChunkTicksAccess<T>) ticks).callSchedule(tick));

        return ticks;
    }

    public static class CubeBoundsLevelHeightAccessor implements LevelHeightAccessor, CubicLevelHeightAccessor {

        private final int height;
        private final int minBuildHeight;
        private final WorldStyle worldStyle;
        private final boolean isCubic;
        private final boolean generates2DChunks;

        public CubeBoundsLevelHeightAccessor(int height, int minBuildHeight, CubicLevelHeightAccessor accessor) {
            this.height = height;
            this.minBuildHeight = minBuildHeight;
            this.worldStyle = accessor.worldStyle();
            this.isCubic = accessor.isCubic();
            this.generates2DChunks = accessor.generates2DChunks();
        }


        @Override public int getHeight() {
            return this.height;
        }

        @Override public int getMinBuildHeight() {
            return this.minBuildHeight;
        }

        @Override public WorldStyle worldStyle() {
            return worldStyle;
        }

        @Override public boolean isCubic() {
            return isCubic;
        }

        @Override public boolean generates2DChunks() {
            return generates2DChunks;
        }
    }
}