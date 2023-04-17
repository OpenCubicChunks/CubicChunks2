package io.github.opencubicchunks.cubicchunks.mock;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.utils.Vector2i;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.Mockito;

public class TestHeightmap extends Heightmap {
    private static final ChunkAccess MOCK_CHUNK_ACCESS;
    static {
        MOCK_CHUNK_ACCESS = Mockito.mock(ChunkAccess.class);
        Mockito.when(MOCK_CHUNK_ACCESS.getHeight()).thenReturn(1);
    }

    public final Map<Vector2i, SortedArraySet<Integer>> inner = new HashMap<>();

    public TestHeightmap() {
        super(MOCK_CHUNK_ACCESS, Types.WORLD_SURFACE);
    }

    @Override public int getFirstAvailable(int x, int z) {
        SortedArraySet<Integer> heights = inner.get(new Vector2i(x, z));
        Integer first = heights.first();
        //noinspection ConstantValue
        if (first == null) {
            return Integer.MIN_VALUE;
        }
        return first;
    }

    @Override public boolean update(int x, int y, int z, BlockState state) {
        y += 1;
        Vector2i xz = new Vector2i(x, z);
        SortedArraySet<Integer> heights = inner.computeIfAbsent(xz, v -> (SortedArraySet<Integer>) SortedArraySet.create(Comparator.naturalOrder().reversed()));

        if (state.canOcclude()) {
            return heights.add(y);
        } else {
            if (heights.contains(y)) {
                return heights.remove(y);
            }
        }
        return false;
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
