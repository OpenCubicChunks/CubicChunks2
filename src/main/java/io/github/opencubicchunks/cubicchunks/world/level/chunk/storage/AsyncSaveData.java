package io.github.opencubicchunks.cubicchunks.world.level.chunk.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeProtoTickList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkTickList;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.material.Fluid;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class AsyncSaveData {

    public final HashMap<SectionPos, DataLayer> blockLight;
    public final HashMap<SectionPos, DataLayer> skyLight;
    public final Optional<Tag> serverBlockTicks;
    public final Optional<Tag> serverLiquidTicks;
    public final Map<BlockPos, BlockEntity> blockEntities;
    public final Map<BlockPos, CompoundTag> blockEntitiesDeferred;

    public AsyncSaveData(ServerLevel level, CubeAccess cube) {
        this.blockLight = new HashMap<>(CubeAccess.SECTION_COUNT, 1f);
        this.skyLight = new HashMap<>(CubeAccess.SECTION_COUNT, 1f);
        for (int i = 0; i < CubeAccess.SECTION_COUNT; i++) {
            final SectionPos sectionPos = Coords.sectionPosByIndex(cube.getCubePos(), i);
            DataLayer blockData = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
            DataLayer skyData = level.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
            if (blockData != null) blockData = blockData.copy();
            if (skyData != null) skyData = skyData.copy();
            this.blockLight.put(sectionPos, blockData);
            this.skyLight.put(sectionPos, skyData);
        }
        final TickList<Block> blockTicks = cube.getBlockTicks();
        if (!(blockTicks instanceof CubeProtoTickList) && !(blockTicks instanceof ChunkTickList)) {
            this.serverBlockTicks = Optional.of(level.getBlockTicks().save(new ImposterChunkPos(cube.getCubePos())));
        } else {
            this.serverBlockTicks = Optional.empty();
        }
        final TickList<Fluid> liquidTicks = cube.getLiquidTicks();
        if (!(liquidTicks instanceof CubeProtoTickList) && !(liquidTicks instanceof ChunkTickList)) {
            this.serverLiquidTicks = Optional.of(level.getLiquidTicks().save(new ImposterChunkPos(cube.getCubePos())));
        } else {
            this.serverLiquidTicks = Optional.empty();
        }
        this.blockEntities = cube.getCubeBlockEntitiesPos().stream()
            .map(cube::getBlockEntity)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(BlockEntity::getBlockPos, Function.identity()));
        if (cube instanceof LevelCube) {
            this.blockEntitiesDeferred = new HashMap<>(((LevelCube) cube).getPendingBlockEntities());
        } else if (cube instanceof ProtoCube) {
            this.blockEntitiesDeferred = new HashMap<>(((ProtoCube) cube).getCubeBlockEntityNbts());
        } else {
            this.blockEntitiesDeferred = new HashMap<>();
        }
    }

}
