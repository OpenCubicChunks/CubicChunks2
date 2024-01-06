package io.github.opencubicchunks.cubicchunks.test.server.level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.MarkableAsCubic;
import io.github.opencubicchunks.cubicchunks.mixin.test.common.server.level.ChunkTrackerTestAccess;
import io.github.opencubicchunks.cubicchunks.testutils.Misc;
import io.github.opencubicchunks.cubicchunks.world.level.chunklike.CloPos;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkTracker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCubicChunkTracker {
    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SharedConstants.IS_RUNNING_IN_IDE = true;
    }

    static class TestCubicTracker extends ChunkTracker {
        protected final int maxLevel;
        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        private final Long2ByteMap sources = new Long2ByteOpenHashMap();

        protected TestCubicTracker(int levelCount, int expectedUpdatesByLevel, int expectedPropagationLevels) {
            super(levelCount, expectedUpdatesByLevel, expectedPropagationLevels);
            this.maxLevel = levelCount-1;
            this.chunks.defaultReturnValue((byte) (levelCount-1));
        }

        @Override protected int getLevelFromSource(long pos) {
            return sources.containsKey(pos) ? sources.get(pos) : Integer.MAX_VALUE;
        }

        @Override protected int getLevel(long pos) {
            return chunks.get(pos);
        }

        @Override protected void setLevel(long pos, int level) {
            ((ChunkTrackerTestAccess) this).invoke_cc_onSetLevel(pos, level);
            if (level >= maxLevel) chunks.remove(pos);
            else chunks.put(pos, (byte) level);
        }

        public void runAllUpdates() {
            super.runUpdates(Integer.MAX_VALUE);
        }

        public void addSource(long pos, int level) {
            this.sources.put(pos, (byte) level);
            this.update(pos, level, true);
        }

        public void removeSource(long pos) {
            this.sources.remove(pos);
            this.update(pos, maxLevel+1, false);
        }
    }

    @Test public void testSingleCubeSource() {
        var tracker = new TestCubicTracker(8, 16, 256);
        ((MarkableAsCubic) tracker).cc_setCubic();
        var srcPos = CloPos.cube(5, 5, -5);
        tracker.addSource(srcPos.asLong(), 0);
        tracker.runAllUpdates();
        var rand = new Random(727);
        assertEquals(0, tracker.getLevel(srcPos.asLong()), "level should be zero at source cube");
        for (int i = 0; i < 1000; i++) {
            var testPos1 = srcPos.cubePos().offset(rand.nextInt(0, 20)-10, rand.nextInt(0, 20)-10, rand.nextInt(0, 20)-10);
            var testPos = CubePos.of(testPos1.getX(), testPos1.getY(), testPos1.getZ());
            var dist = Misc.chebyshevDistance(srcPos.cubePos(), testPos);
            assertEquals(Math.min(7, dist), tracker.getLevel(CloPos.cube(testPos).asLong()),
                String.format("level at cube %d %d %d", testPos.getX(), testPos.getY(), testPos.getZ()));
        }
        for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                var srcChunk = srcPos.correspondingColumnCloPos(dx, dz);
                assertEquals(0, tracker.getLevel(srcChunk.asLong()), "level should be zero at chunks intersecting source cube");
            }
        }
        var srcPosY0 = CubePos.of(srcPos.getX(), 0, srcPos.getZ());
        for (int i = 0; i < 1000; i++) {
            var testPos = CloPos.cube(srcPos.getX() + rand.nextInt(0, 20) - 10, 0, srcPos.getZ() + rand.nextInt(0, 20) - 10);
            var testCol = testPos.correspondingColumnCloPos(rand.nextInt(0, CubicConstants.DIAMETER_IN_SECTIONS), rand.nextInt(0, CubicConstants.DIAMETER_IN_SECTIONS));
            var dist = Misc.chebyshevDistance(srcPosY0, testPos.cubePos());
            assertEquals(Math.min(7, dist), tracker.getLevel(testCol.asLong()), String.format("level at chunk %d %d", testCol.getX(), testCol.getZ()));
        }

        tracker.removeSource(srcPos.asLong());
        tracker.runAllUpdates();

        assertThat(tracker.chunks).withFailMessage("tracker should be empty after removing all sources").isEmpty();
    }

    // this kinda violates the principle of not testing implementation details, but important to make sure this doesn't leak memory so :shrug:
    @Test public void testExistingCubesForCubeColumns() {
        var tracker = new TestCubicTracker(8, 16, 256);
        ((MarkableAsCubic) tracker).cc_setCubic();
        var existingCubesForCubeColumns = ((ChunkTrackerTestAccess) tracker).get_cc_existingCubesForCubeColumns();
        var srcPos = CloPos.cube(1, 0, 0);
        tracker.addSource(srcPos.asLong(), 0);
        tracker.runAllUpdates();
        assertThat(existingCubesForCubeColumns.size()).isEqualTo(13*13);
        var srcPos2 = CloPos.cube(1, 1, 0);
        tracker.addSource(srcPos2.asLong(), 0);
        tracker.runAllUpdates();
        assertThat(existingCubesForCubeColumns.size()).isEqualTo(13*13);
        var srcPos3 = CloPos.cube(1, 1, 0);
        var srcPos4 = CloPos.cube(2, 1, 0);
        tracker.addSource(srcPos3.asLong(), 0);
        tracker.addSource(srcPos4.asLong(), 0);
        tracker.runAllUpdates();
        assertThat(existingCubesForCubeColumns.size()).isEqualTo(14*13);
        tracker.removeSource(srcPos.asLong());
        tracker.removeSource(srcPos2.asLong());
        tracker.removeSource(srcPos3.asLong());
        tracker.runAllUpdates();
        assertThat(existingCubesForCubeColumns.size()).isEqualTo(13*13);
        tracker.removeSource(srcPos4.asLong());
        tracker.runAllUpdates();
        assertThat(existingCubesForCubeColumns).withFailMessage("tracker should be empty after removing all sources").isEmpty();
    }

    @Test public void testSingleChunkSource() {
        var tracker = new TestCubicTracker(8, 16, 256);
        ((MarkableAsCubic) tracker).cc_setCubic();
        var srcPos = CloPos.column(-4, 3);
        tracker.addSource(srcPos.asLong(), 0);
        tracker.runAllUpdates();
        var rand = new Random(333);
        assertEquals(0, tracker.getLevel(srcPos.asLong()), "level should be zero at source chunk");
        assertThat(tracker.chunks.keySet()).withFailMessage("no cubes should be loaded by columns").allSatisfy(CloPos::isColumn);
        for (int i = 0; i < 1000; i++) {
            var testPos = CloPos.column(srcPos.getX() + rand.nextInt(0, 20) - 10, srcPos.getZ() + rand.nextInt(0, 20) - 10);
            var dist = Misc.chebyshevDistance(srcPos.chunkPos(), testPos.chunkPos());
            assertEquals(Math.min(7, dist), tracker.getLevel(testPos.asLong()), String.format("level at chunk %d %d", testPos.getX(), testPos.getZ()));
        }
        tracker.removeSource(srcPos.asLong());
        tracker.runAllUpdates();
        assertThat(tracker.chunks.keySet()).withFailMessage("tracker should be empty after removing all sources").isEmpty();
    }

    // done to ensure that we don't break vanilla functionality. would probably be best to test more rigorously, but this is a start.
    @Test public void testSingleChunkSourceVanilla() {
        var tracker = new TestCubicTracker(8, 16, 256);
        var srcPos = CloPos.column(3, 0).chunkPos();
        tracker.addSource(srcPos.toLong(), 0);
        tracker.runAllUpdates();
        var rand = new Random(333);
        assertEquals(0, tracker.getLevel(srcPos.toLong()), "level should be zero at source chunk");
        for (int i = 0; i < 1000; i++) {
            var testPos = CloPos.column(srcPos.x + rand.nextInt(0, 20) - 10, srcPos.z + rand.nextInt(0, 20) - 10).chunkPos();
            var dist = Misc.chebyshevDistance(srcPos, testPos);
            assertEquals(Math.min(7, dist), tracker.getLevel(testPos.toLong()), String.format("level at chunk %d %d", testPos.x, testPos.z));
        }
        tracker.removeSource(srcPos.toLong());
        tracker.runAllUpdates();
        assertThat(tracker.chunks.keySet()).withFailMessage("tracker should be empty after removing all sources").isEmpty();
    }
}
