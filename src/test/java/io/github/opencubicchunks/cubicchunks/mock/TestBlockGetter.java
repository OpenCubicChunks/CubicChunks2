package io.github.opencubicchunks.cubicchunks.mock;

import static io.github.opencubicchunks.cc_core.api.CubicConstants.SECTION_DIAMETER;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.TickContainerAccess;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

public class TestBlockGetter implements BlockGetter {
    private static final CubicVanillaLevelHeightAccessor MOCK_LEVEL_HEIGHT_ACCESSOR;
    static {
        MOCK_LEVEL_HEIGHT_ACCESSOR = Mockito.mock(CubicVanillaLevelHeightAccessor.class);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.generates2DChunks()).thenReturn(false);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.isCubic()).thenReturn(true);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.worldStyle()).thenReturn(CubicLevelHeightAccessor.WorldStyle.CUBIC);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.getSectionsCount()).thenReturn(0);
    }

    private final Map<CubePos, TestCube> cubes = new HashMap<>();
    private final TestHeightmap heightmap = new TestHeightmap();

    public void addCube(TestCube cube) {
        // update the global heightmap with the cube's blocks
        for (int blockX = cube.getCubePos().minCubeX(), maxX = blockX + CubicConstants.DIAMETER_IN_BLOCKS; blockX < maxX; blockX++) {
            for (int blockZ = cube.getCubePos().minCubeZ(), maxZ = blockZ + CubicConstants.DIAMETER_IN_BLOCKS; blockZ < maxZ; blockZ++) {
                for (int blockY = cube.getCubePos().minCubeY(), maxY = blockY + CubicConstants.DIAMETER_IN_BLOCKS; blockY < maxY; blockY++) {
                    heightmap.update(blockX, blockY, blockZ, cube.getBlockState(new BlockPos(blockX, blockY, blockZ)));
                }
            }
        }
        // add the cube
        this.cubes.put(cube.getCubePos(), cube);
    }

    public TestHeightmap getHeightmap() {
        return this.heightmap;
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        TestCube cube = this.cubes.get(CubePos.from(pos));
        cube.setBlockStateLocal(pos, state);
        this.heightmap.update(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        throw new NotImplementedException(TestBlockGetter.class.toString());
    }

    @Nullable public BlockState getNullableBlockState(BlockPos pos) {
        TestCube cube = this.cubes.get(CubePos.from(pos));
        if (cube == null) {
            return null;
        }
        return cube.getBlockState(pos);
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        TestCube cube = this.cubes.get(CubePos.from(pos));
        if (cube == null) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        return cube.getBlockState(pos);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        throw new NotImplementedException(TestBlockGetter.class.toString());
    }

    @Override public int getHeight() {
        throw new NotImplementedException(TestBlockGetter.class.toString());
    }

    @Override public int getMinBuildHeight() {
        throw new NotImplementedException(TestBlockGetter.class.toString());
    }

    public static class TestCube extends CubeAccess implements BlockGetter {
        private final BlockState[][] sections = new BlockState[CubicConstants.SECTION_COUNT][];

        public TestCube(CubePos cubePos) {
            super(cubePos, MOCK_LEVEL_HEIGHT_ACCESSOR, null, MOCK_LEVEL_HEIGHT_ACCESSOR, null, 0, null, null);
            for (int i = 0; i < this.sections.length; i++) {
                this.sections[i] = new BlockState[SECTION_DIAMETER * SECTION_DIAMETER * SECTION_DIAMETER];
            }
        }

        private static int blockIndex(int x, int y, int z) {
            return x + SECTION_DIAMETER * (y + z * SECTION_DIAMETER);
        }

        public void setBlockStateLocal(BlockPos pos, BlockState state) {
            int index = Coords.blockToIndex(pos);
            this.sections[index][
                blockIndex(
                    pos.getX() & 15,
                    pos.getY() & 15,
                    pos.getZ() & 15)
                ] = state;
        }

        @Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
            throw new NotImplementedException();
        }

        @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
            throw new NotImplementedException(TestBlockGetter.class.toString());
        }

        @NotNull @Override public BlockState getBlockState(BlockPos pos) {
            int index = Coords.blockToIndex(pos);
            BlockState state = this.sections[index][
                blockIndex(
                    pos.getX() & 15,
                    pos.getY() & 15,
                    pos.getZ() & 15)
                ];
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        @Override public FluidState getFluidState(BlockPos pos) {
            throw new NotImplementedException(TestBlockGetter.class.toString());
        }

        @Override public int getHeight() {
            throw new NotImplementedException(TestBlockGetter.class.toString());
        }

        @Override public void setBlockEntity(BlockEntity blockEntity) {
            throw new NotImplementedException();
        }

        @Override public void addEntity(Entity entity) {
            throw new NotImplementedException();
        }

        @Override public ChunkStatus getStatus() {
            return ChunkStatus.LIGHT;
        }

        @Override public void removeBlockEntity(BlockPos pos) {
            throw new NotImplementedException();
        }

        @Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
            throw new NotImplementedException();
        }

        @Override public Stream<BlockPos> getLights() {
            throw new NotImplementedException();
        }

        @Override public TickContainerAccess<Block> getBlockTicks() {
            throw new NotImplementedException();
        }

        @Override public TickContainerAccess<Fluid> getFluidTicks() {
            throw new NotImplementedException();
        }

        @Override public TicksToSave getTicksForSerialization() {
            throw new NotImplementedException();
        }

        @Override public int getMinBuildHeight() {
            throw new NotImplementedException(TestBlockGetter.class.toString());
        }
    }

    interface CubicVanillaLevelHeightAccessor extends CubicLevelHeightAccessor, LevelHeightAccessor {

    }
}
