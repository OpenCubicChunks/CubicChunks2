package io.github.opencubicchunks.cubicchunks.test.world.level.chunklike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCloPos {
    // TODO proper tests
    @Test public void testRawCoords() {
        var pos = CloPos.cube(3, 4, 5).asLong();
        pos = CloPos.setRawX(pos, -77);
        pos = CloPos.setRawY(pos, 543);
        pos = CloPos.setRawZ(pos, -1743);
        assertEquals(-77, CloPos.extractRawX(pos));
        assertEquals(543, CloPos.extractRawY(pos));
        assertEquals(-1743, CloPos.extractRawZ(pos));

    }

    @Test public void testChunks() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                var pos = CloPos.column(x, z);
                assertTrue(pos.isColumn());
                assertTrue(CloPos.isColumn(pos.asLong()));
            }
        }
    }

    @Test public void forEachNeighbor() {
        var pos = CloPos.cube(3, 4, 5);
        var cubesCols = new int[] { 0,  0 };
        pos.forEachNeighbor(p -> cubesCols[p.isCube() ? 0 : 1]++);
        assertEquals(26, cubesCols[0]);
        assertEquals(CubicConstants.CHUNK_COUNT, cubesCols[1]);
        cubesCols[0] = 0;
        cubesCols[1] = 0;
        CloPos.forEachNeighbor(pos.asLong(), p -> cubesCols[CloPos.isCube(p) ? 0 : 1]++);
        assertEquals(26, cubesCols[0]);
        assertEquals(CubicConstants.CHUNK_COUNT, cubesCols[1]);
    }
}
