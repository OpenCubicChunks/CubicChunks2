package io.github.opencubicchunks.cubicchunks.mock;

import java.util.HashMap;
import java.util.stream.Stream;

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
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

public class TestBlockGetter extends CubeAccess implements BlockGetter {
    private static final CubicVanillaLevelHeightAccessor MOCK_LEVEL_HEIGHT_ACCESSOR;
    static {
        MOCK_LEVEL_HEIGHT_ACCESSOR = Mockito.mock(CubicVanillaLevelHeightAccessor.class);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.generates2DChunks()).thenReturn(false);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.isCubic()).thenReturn(true);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.worldStyle()).thenReturn(WorldStyle.CUBIC);
        Mockito.when(MOCK_LEVEL_HEIGHT_ACCESSOR.getSectionsCount()).thenReturn(0);
    }

    private final HashMap<BlockPos, BlockState> blockStates = new HashMap<>();
    private final TestHeightmap heightmap = new TestHeightmap();

    public TestBlockGetter() {
        super(null, MOCK_LEVEL_HEIGHT_ACCESSOR, null, MOCK_LEVEL_HEIGHT_ACCESSOR, null, 0, null, null);
    }

    public TestHeightmap getHeightmap() {
        return this.heightmap;
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        this.blockStates.put(pos, state);
        this.heightmap.update(pos.getX(), pos.getY(), pos.getZ(), state);
    }

    @Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        throw new NotImplementedException();
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        throw new NotImplementedException(TestBlockGetter.class.toString());
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        BlockState state = blockStates.get(pos);
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

    interface CubicVanillaLevelHeightAccessor extends CubicLevelHeightAccessor, LevelHeightAccessor {

    }
}
