package io.github.opencubicchunks.cubicchunks.mock;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.utils.ColumnPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.Mockito;

public class TestHeightmap {
    private static final ChunkAccess MOCK_CHUNK_ACCESS;
    static {
        MOCK_CHUNK_ACCESS = Mockito.mock(ChunkAccess.class);
        Mockito.when(MOCK_CHUNK_ACCESS.getHeight()).thenReturn(1);
    }

    public final Map<ColumnPos, SortedArraySet<Integer>> inner = new HashMap<>();

    public OffsetTestHeightmap withOffset(int sectionX, int sectionZ) {
        return new OffsetTestHeightmap(sectionX, sectionZ);
    }

    public int getFirstAvailable(int x, int z) {
        SortedArraySet<Integer> heights = inner.get(new ColumnPos(x, z));
        Integer first = heights.first();
        //noinspection ConstantValue
        if (first == null) {
            return Integer.MIN_VALUE;
        }
        return first;
    }

    public boolean update(int x, int y, int z, BlockState state) {
        y += 1;
        ColumnPos xz = new ColumnPos(x, z);
        SortedArraySet<Integer> heights = inner.computeIfAbsent(xz, v -> (SortedArraySet<Integer>) SortedArraySet.create(Comparator.naturalOrder().reversed()));

        // TODO: implement occlusion properly
        if (state.canOcclude()) {
            return heights.add(y);
        } else {
            if (heights.contains(y)) {
                return heights.remove(y);
            }
        }
        return false;
    }

    public class OffsetTestHeightmap extends Heightmap {
        private final int xOffset;
        private final int zOffset;
        public OffsetTestHeightmap(int xSectionOffset, int zSectionOffset) {
            super(MOCK_CHUNK_ACCESS, Types.WORLD_SURFACE);
            this.xOffset = SectionPos.sectionToBlockCoord(xSectionOffset);
            this.zOffset = SectionPos.sectionToBlockCoord(zSectionOffset);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    this.update(x, Integer.MIN_VALUE, z, Blocks.STONE.defaultBlockState());
                }
            }
        }
        @Override public int getFirstAvailable(int x, int z) {
            return TestHeightmap.this.getFirstAvailable(
                x + this.xOffset,
                z + this.zOffset
            );
        }

        @Override public boolean update(int x, int y, int z, BlockState state) {
            return TestHeightmap.this.update(
                x + this.xOffset,
                y,
                z + this.zOffset,
                state
            );
        }

        @Override public int getHighestTaken(int x, int z) {
            throw new NotImplementedException();
        }

        @Override public void setRawData(ChunkAccess chunk, Types type, long[] data) {
            throw new NotImplementedException();
        }

        @Override public long[] getRawData() {
            throw new NotImplementedException();
        }
    }
}
