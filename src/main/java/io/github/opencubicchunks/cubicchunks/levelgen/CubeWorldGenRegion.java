

package io.github.opencubicchunks.cubicchunks.levelgen;

import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCube;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.WorldGenRegionAccess;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CubeWorldGenRegion extends WorldGenRegion implements CubicLevelAccessor {

    private static final Logger LOGGER = LogManager.getLogger();
    private final CubeAccess[] cubePrimers;

    private final CubePos centerCubePos;
    private final int mainCubeX;
    private final int mainCubeY;
    private final int mainCubeZ;

    private final int minCubeX;
    private final int minCubeY;
    private final int minCubeZ;

    private final int maxCubeX;
    private final int maxCubeY;
    private final int maxCubeZ;

    private final int diameter;
    private final long seed;
    private final int seaLevel;
    private final LevelData worldInfo;
    private final Random random;
    private final DimensionType dimension;
    private final ChunkAccess access;

    private final BiomeManager biomeManager;

    private int cubeCenterColumnCenterX = 0;
    private int cubeCenterColumnCenterZ = 0;

    private final WorldGenTickAccess<Block> blockTicks = new WorldGenTickAccess<>((pos) -> this.getCube(pos).getBlockTicks());
    private final WorldGenTickAccess<Fluid> fluidTicks = new WorldGenTickAccess<>((pos) -> this.getCube(pos).getFluidTicks());

    // TODO: the "main chunk" is an awful idea, who did this?
    public CubeWorldGenRegion(ServerLevel level, List<CubeAccess> cubes, ChunkStatus status, ChunkAccess mainChunk, int writeRadiusCutoff) {
        super(level, Collections.singletonList(cubes.get(0)), status, writeRadiusCutoff);

        int cubeRoot = Mth.floor(Math.cbrt(cubes.size()));
        if (cubeRoot * cubeRoot * cubeRoot != cubes.size()) {
            throw Util.pauseInIde(new IllegalStateException("Cube World Gen Region Cache size is not a cube."));
        } else {
            this.centerCubePos = cubes.get(cubes.size() / 2).getCubePos();
            this.cubePrimers = cubes.toArray(new CubeAccess[0]);

            this.mainCubeX = this.centerCubePos.getX();
            this.mainCubeY = this.centerCubePos.getY();
            this.mainCubeZ = this.centerCubePos.getZ();
            this.diameter = cubeRoot;

            this.seed = level.getSeed();
            this.seaLevel = level.getSeaLevel();
            this.worldInfo = level.getLevelData();
            this.random = level.getRandom();
            this.dimension = level.dimensionType();

            this.biomeManager = new BiomeManager(this, BiomeManager.obfuscateSeed(this.seed));

            CubeAccess minCube = this.cubePrimers[0];
            CubeAccess maxCube = this.cubePrimers[this.cubePrimers.length - 1];

            this.minCubeX = minCube.getCubePos().getX();
            this.minCubeY = minCube.getCubePos().getY();
            this.minCubeZ = minCube.getCubePos().getZ();

            this.maxCubeX = maxCube.getCubePos().getX();
            this.maxCubeY = maxCube.getCubePos().getY();
            this.maxCubeZ = maxCube.getCubePos().getZ();

            this.access = mainChunk;
        }
    }

    public void moveCenterCubeChunkPos(int newX, int newZ) {
        this.cubeCenterColumnCenterX = newX;
        this.cubeCenterColumnCenterZ = newZ;
    }

    @Override public ChunkPos getCenter() {
        return this.centerCubePos.asChunkPos(cubeCenterColumnCenterX, cubeCenterColumnCenterZ);
    }

    public int getMainCubeX() {
        return this.mainCubeX;
    }

    public int getMainCubeY() {
        return this.mainCubeY;
    }

    public int getMainCubeZ() {
        return this.mainCubeZ;
    }

    public int getMinCubeY() {
        return this.minCubeY;
    }

    public int getMaxCubeY() {
        return this.maxCubeY;
    }


    public CubePos getCenterCubePos() {
        return this.getCenterCubePos();
    }

    public CubeAccess getCube(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.EMPTY, true);
    }

    public CubeAccess getCube(BlockPos blockPos) {
        return this.getCube(blockToCube(blockPos.getX()), blockToCube(blockPos.getY()), blockToCube(blockPos.getZ()), ChunkStatus.EMPTY,
            true);
    }

    public CubeAccess getCube(CubePos cubePos) {
        return this.getCube(cubePos.getX(), cubePos.getY(), cubePos.getZ(), ChunkStatus.EMPTY,
            true);
    }

    public CubeAccess getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status) {
        return this.getCube(cubeX, cubeY, cubeZ, status, true);
    }

    public CubeAccess getCube(int x, int y, int z, ChunkStatus requiredStatus, boolean nonnull) {
        CubeAccess icube;
        if (this.cubeExists(x, y, z)) {
            int dx = x - this.minCubeX;
            int dy = y - this.minCubeY;
            int dz = z - this.minCubeZ;
            icube = this.cubePrimers[this.diameter * (dx * this.diameter + dy) + dz];
            if (icube.getStatus().isOrAfter(requiredStatus)) {
                return icube;
            }
        } else {
            icube = null;
        }

        if (!nonnull) {
            return null;
        } else {
            CubeAccess cornerCube1 = this.cubePrimers[0];
            CubeAccess cornerCube2 = this.cubePrimers[this.cubePrimers.length - 1];
            LOGGER.error("Requested section : {} {} {}", x, y, z);
            LOGGER.error("Region bounds : {} {} {} | {} {} {}",
                cornerCube1.getCubePos().getX(), cornerCube1.getCubePos().getY(), cornerCube1.getCubePos().getZ(),
                cornerCube2.getCubePos().getX(), cornerCube2.getCubePos().getY(), cornerCube2.getCubePos().getZ());
            if (icube != null) {
                throw Util.pauseInIde(new RuntimeException(String.format("Section is not of correct status. Expecting %s, got %s "
                    + "| %s %s %s", requiredStatus, icube.getStatus(), x, y, z)));
            } else {
                throw Util.pauseInIde(new RuntimeException(String.format("We are asking a region for a section out of bound | " + "%s %s %s", x, y, z) + "\n" +
                    String.format("Bound | " + "(%s %s %s), (%s %s %s)", this.minCubeX, this.minCubeY, this.minCubeZ, this.maxCubeX, this.maxCubeY, this.maxCubeZ)));
            }
        }
    }

    public boolean cubeExists(int x, int y, int z) {
        return x >= this.minCubeX && x <= this.maxCubeX &&
            y >= this.minCubeY && y <= this.maxCubeY &&
            z >= this.minCubeZ && z <= this.maxCubeZ;
    }


    @Override public long getSeed() {
        return this.seed;
    }

    @Override public WorldGenTickAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override public WorldGenTickAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override public LevelData getLevelData() {
        return this.worldInfo;
    }

    @Override public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        if (!this.cubeExists(blockToCube(pos.getX()), blockToCube(pos.getY()), blockToCube(pos.getZ()))) {
            throw new RuntimeException("We are asking a region for a chunk out of bound");
        } else {
            return new DifficultyInstance(this.getLevel().getDifficulty(), this.getLevel().getDayTime(), 0L, this.getLevel().getMoonBrightness());
        }
    }

    @Override public ChunkSource getChunkSource() {
        return getLevel().getChunkSource();
    }

    @Override public Random getRandom() {
        return this.random;
    }

    @Override
    public void playSound(@Nullable Player player, BlockPos pos, SoundEvent soundIn, SoundSource category, float volume, float pitch) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override public void addParticle(ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override public void gameEvent(@Nullable Entity entity, GameEvent gameEvent, BlockPos blockPos) {
    }

    @Override public WorldBorder getWorldBorder() {
        return getLevel().getWorldBorder();
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        CubeAccess icube = this.getCube(pos);
        BlockEntity tileentity = icube.getBlockEntity(pos);
        if (tileentity != null) {
            return tileentity;
        } else {
            CompoundTag compoundnbt = icube.getBlockEntityNbt(pos);
            BlockState state = this.getBlockState(pos);
            if (compoundnbt != null) {
                if ("DUMMY".equals(compoundnbt.getString("id"))) {
                    if (!state.hasBlockEntity()) {
                        return null;
                    }
                    tileentity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
                } else {
                    tileentity = BlockEntity.loadStatic(pos, state, compoundnbt);
                }

                if (tileentity != null) {
                    icube.setBlockEntity(tileentity);
                    return tileentity;
                }
            }

            if (icube.getBlockState(pos).hasBlockEntity()) {
                LOGGER.warn("Tried to access a block entity before it was created. {}", pos);
            }

            return null;
        }
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        return this.getCube(pos).getBlockState(pos);
    }

    @Override
    public boolean isEmptyBlock(BlockPos pos) {
        return this.getCube(pos).getBlockState(pos).isAir();
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return this.getCube(pos).getFluidState(pos);
    }

    @Override public List<Entity> getEntities(@Nullable Entity entityIn, AABB boundingBox,
                                              @Nullable Predicate<? super Entity> predicate) {
        return Collections.emptyList();
    }

    @Override public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB aABB, Predicate<? super T> predicate) {
        return Collections.emptyList();
    }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> clazz, AABB aabb, @Nullable Predicate<? super T> filter) {
        return Collections.emptyList();
    }

    @Override public List<Player> players() {
        return /*level.players()*/ Collections.emptyList();
    }

    @Deprecated
    @Nullable @Override public ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull) {
        return this.access; //TODO: Do not do this.
    }

    @Override public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        int yStart = Coords.cubeToMinBlock(mainCubeY + 1);
        int yEnd = Coords.cubeToMinBlock(mainCubeY);


        if (maxCubeY == mainCubeY) {
            CubeAccess cube1 = getCube(new BlockPos(x, yEnd, z));
            int height = cube1.getCubeLocalHeight(heightmapType, x, z);

            if (cube1.getCubeLocalHeight(heightmapType, x, z) >= yStart) {
                return yStart + 2;
            }

            if (height <= getLevel().getMinBuildHeight()) {
                return yEnd - 1;
            }
            return height + 1;
        }


        CubeAccess cube1 = getCube(new BlockPos(x, yStart, z));
        if (cube1.getCubeLocalHeight(heightmapType, x, z) >= yStart) {
            return yStart + 2;
        }
        CubeAccess cube2 = getCube(new BlockPos(x, yEnd, z));
        int height = cube2.getCubeLocalHeight(heightmapType, x, z);

        //Check whether or not height was found for this cube. If height wasn't found, move to the next cube under the current cube
        if (height <= getLevel().getMinBuildHeight()) {
            return yEnd - 1;
        }
        return height + 1;
    }

    @Override public int getSkyDarken() {
        return 0;
    }

    @Override public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        CubeAccess cube = this.getCube(Coords.blockToCube(QuartPos.toBlock(x)), Coords.blockToCube(QuartPos.toBlock(y)), Coords.blockToCube(QuartPos.toBlock(z)), ChunkStatus.BIOMES, false);
        if (cube != null) {
            return cube.getNoiseBiome(x, y, z);
        }
        return this.getUncachedNoiseBiome(x, y, z);
    }

    @Override public boolean isClientSide() {
        return false;
    }

    @Override public int getSeaLevel() {
        return this.seaLevel;
    }

    @Override public DimensionType dimensionType() {
        return this.dimension;
    }

    @Override public float getShade(Direction direction, boolean b) {
        return 1f;
    }

    @Override public LevelLightEngine getLightEngine() {
        return getLevel().getLightEngine();
    }

    // setBlockState
    @Override public boolean setBlock(BlockPos pos, BlockState newState, int flags, int recursionLimit) {
        if (!this.ensureCanWrite(pos)) {
            return false;
        }

        CubeAccess icube = this.getCube(pos);

        if (!icube.getStatus().isOrAfter(ChunkStatus.LIQUID_CARVERS)) {
            icube.setFeatureBlocks(pos, newState);
            return true;
        }

        BlockState blockstate = icube.setBlockState(pos, newState, false);

        if (blockstate != null) {
            this.getLevel().onBlockStateChange(pos, blockstate, newState);
        }
        if (newState.hasBlockEntity()) {
            if (icube.getStatus().getChunkType() == ChunkStatus.ChunkType.LEVELCHUNK) {
                BlockEntity tileEntity = ((EntityBlock) newState.getBlock()).newBlockEntity(pos, newState);
                if (tileEntity != null) {
                    icube.setBlockEntity(tileEntity);
                } else {
                    icube.removeBlockEntity(pos);
                }
            } else {
                CompoundTag compoundnbt = new CompoundTag();
                compoundnbt.putInt("x", pos.getX());
                compoundnbt.putInt("y", pos.getY());
                compoundnbt.putInt("z", pos.getZ());
                compoundnbt.putString("id", "DUMMY");
                icube.setBlockEntityNbt(compoundnbt);
            }
        } else if (blockstate != null && blockstate.hasBlockEntity()) {
            icube.removeBlockEntity(pos);
        }

        if (newState.hasPostProcess(this, pos)) {
            this.markPosForPostprocessing(pos);
        }

        return true;
    }

    private void markPosForPostprocessing(BlockPos pos) {
        this.getCube(pos).markPosForPostprocessing(pos);
    }

    @Override public boolean ensureCanWrite(BlockPos blockPos) {
        int cubeX = Coords.blockToCube(blockPos.getX());
        int cubeY = Coords.blockToCube(blockPos.getY());
        int cubeZ = Coords.blockToCube(blockPos.getZ());

        int xDiff = Math.abs(this.centerCubePos.getX() - cubeX);
        int zDiff = Math.abs(this.centerCubePos.getZ() - cubeZ);
        int writeRadiusCutoff = ((WorldGenRegionAccess) this).getWriteRadiusCutoff();
        if (xDiff <= writeRadiusCutoff && zDiff <= writeRadiusCutoff) {
            return true;
        } else {
            Supplier<String> currentlyGenerating = ((WorldGenRegionAccess) this).getCurrentlyGenerating();
            Util.logAndPauseIfInIde(
                "Detected setBlock in a far cube [" + cubeX + ", " + cubeY + ", " + cubeZ + "], pos: " + blockPos + ", status: " + ((WorldGenRegionAccess) this).getGeneratingStatus() + (
                    currentlyGenerating == null ? "" : ", currently generating: " + currentlyGenerating.get()));
            return false;
        }

    }

    @Override public boolean removeBlock(BlockPos pos, boolean isMoving) {
        return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    // destroyBlock
    @Override public boolean destroyBlock(BlockPos pos, boolean isPlayerInCreative, @Nullable Entity droppedEntities, int recursionLimit) {
        BlockState blockstate = this.getBlockState(pos);
        if (blockstate.isAir()) {
            return false;
        } else {
            if (isPlayerInCreative) {
                BlockEntity tileentity = blockstate.hasBlockEntity() ? this.getBlockEntity(pos) : null;
                Block.dropResources(blockstate, this.getLevel(), pos, tileentity, droppedEntities, ItemStack.EMPTY);
            }

            return this.setBlock(pos, Blocks.AIR.defaultBlockState(), 3, recursionLimit);
        }
    }

    @Override public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> blockstate) {
        return blockstate.test(this.getBlockState(pos));
    }

    //TODO: DOUBLE CHECK THESE

    @Override
    public RegistryAccess registryAccess() {
        return this.getLevel().registryAccess();
    }

    @Override public int getMinBuildHeight() {
        return getLevel().getMinBuildHeight();
    }

    @Override public int getHeight() {
        return getLevel().getHeight();
    }

    public boolean insideCubeHeight(int blockY) {
        return Coords.cubeToMinBlock(this.getMainCubeY()) <= blockY && blockY <= Coords.cubeToMaxBlock(this.getMainCubeY());
    }
}

