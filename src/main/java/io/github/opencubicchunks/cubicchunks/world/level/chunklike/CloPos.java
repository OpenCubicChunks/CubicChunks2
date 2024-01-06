package io.github.opencubicchunks.cubicchunks.world.level.chunklike;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

public class CloPos {
    private static final int CLO_Y_COLUMN_INDICATOR = Integer.MAX_VALUE;
    // 22 bits including sign; 2^21-1 is the highest 22-bit signed integer
    private static final int CLO_PACKED_Y_COLUMN_INDICATOR = (1 << 21) - 1;
    // TODO might want to change this to asLong(Integer.MAX_VALUE, Integer.MAX_VALUE),
    //  but it requires overrides in some places - DynamicGraphMinFixedPoint defaults to Long.MAX_VALUE as source
    public static final long INVALID_CLO_POS = Long.MAX_VALUE;
    private final int x, y, z;

    private CloPos(CubePos cubePos) {
        if (cubePos.getY() == CLO_Y_COLUMN_INDICATOR) {
            throw new IllegalArgumentException("Invalid cube Y position");
        }
        this.x = cubePos.getX();
        this.y = cubePos.getY();
        this.z = cubePos.getZ();
    }

    private CloPos(ChunkPos columnPos) {
        this.x = columnPos.x;
        this.z = columnPos.z;
        this.y = CLO_Y_COLUMN_INDICATOR;
    }

    private CloPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static CloPos cube(int x, int y, int z) {
        if (y == CLO_Y_COLUMN_INDICATOR) {
            throw new IllegalArgumentException("Invalid cube Y position");
        }
        return new CloPos(x, y, z);
    }


    public static CloPos column(int x, int z) {
        return new CloPos(x, CLO_Y_COLUMN_INDICATOR, z);
    }

    public static CloPos section(SectionPos section) {
        return cube(
            Coords.sectionToCube(section.x()),
            Coords.sectionToCube(section.y()),
            Coords.sectionToCube(section.z())
        );
    }

    public static CloPos cube(CubePos cubePos) {
        return new CloPos(cubePos);
    }

    public static CloPos column(ChunkPos pos) {
        return new CloPos(pos);
    }

    public static CloPos cube(BlockPos pos) {
        return cube(
            Coords.blockToCube(pos.getX()),
            Coords.blockToCube(pos.getY()),
            Coords.blockToCube(pos.getZ())
        );
    }

    public static CloPos fromLong(long cloPos) {
        int x = extractRawX(cloPos);
        int y = extractRawY(cloPos);
        int z = extractRawZ(cloPos);
        if (isPackedColumnYMarker(y)) {
            int idx = CLO_PACKED_Y_COLUMN_INDICATOR - y;
            int localX = Coords.indexToColumnX(idx);
            int localZ = Coords.indexToColumnZ(idx);
            int colX = Coords.cubeToSection(x, localX);
            int colZ = Coords.cubeToSection(z, localZ);
            return CloPos.column(colX, colZ);
        } else {
            return CloPos.cube(x, y, z);
        }
    }

    public boolean isCube() {
        return this.y != CLO_Y_COLUMN_INDICATOR;
    }

    public boolean isColumn() {
        return this.y == CLO_Y_COLUMN_INDICATOR;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        if (!isCube()) {
            throw new UnsupportedOperationException("Calling getY() on column CloPos");
        }
        return y;
    }

    public int getZ() {
        return z;
    }

    public long asLong() {
        long i = 0L;
        if (isCube()) {
            i |= ((long) this.x & (1 << 21) - 1) << 43;
            i |= ((long) this.y & (1 << 22) - 1);
            i |= ((long) this.z & (1 << 21) - 1) << 22;
        } else {
            int yMarker = packedColumnYMarker(this.x, this.z);
            i |= ((long) Coords.sectionToCube(this.x) & (1 << 21) - 1) << 43;
            i |= ((long) yMarker & (1 << 22) - 1);
            i |= ((long) Coords.sectionToCube(this.z) & (1 << 21) - 1) << 22;
        }
        return i;
    }

    public static long asLong(int x, int y, int z) {
        if (isPackedColumnYMarker(y)) {
            throw new IllegalArgumentException("y coordinate " + y + " is a column marker but attempting to pack cube!");
        }
        return packRaw(x, y, z);
    }

    private static boolean isPackedColumnYMarker(int y) {
        return y > CLO_PACKED_Y_COLUMN_INDICATOR - CubicConstants.CHUNK_COUNT;
    }

    public static int packedColumnYMarker(int x, int z) {
        return CLO_PACKED_Y_COLUMN_INDICATOR - Coords.columnToIndex(x, z);
    }

    public static long packRaw(long x, long y, long z) {
        long i = 0L;
        i |= (x & (1L << 21) - 1) << 43;
        i |= (y & (1L << 22) - 1);
        i |= (z & (1L << 21) - 1) << 22;
        return i;
    }

    public static long setRawX(long packed, int x) {
        packed &= ~(((1L << 21) - 1) << 43);
        return packed | ((x & (1L << 21) - 1) << 43);
    }

    public static long setRawY(long packed, int y) {
        //noinspection PointlessBitwiseExpression: yes intellij, it *is* equivalent to "-(1L << 22)" but it's also not as obvious
        packed &= ~((1L << 22) - 1);
        return packed | (y & (1L << 22) - 1);
    }

    public static long setRawZ(long packed, int z) {
        packed &= ~(((1L << 21) - 1) << 22);
        return packed | ((z & (1L << 21) - 1) << 22);
    }

    public static long asLong(int x, int z) {
        return packRaw(Coords.sectionToCube(x), packedColumnYMarker(x, z), Coords.sectionToCube(z));
    }

    public static int extractRawX(long packed) {
        return (int) (packed >> 43);
    }

    public static int extractRawY(long packed) {
        return (int) (packed << 42 >> 42);
    }

    public static int extractRawZ(long packed) {
        return (int) (packed << 21 >> 43);
    }

    public static int extractX(long packed) {
        int x = extractRawX(packed);
        int y = extractRawY(packed);
        if (isPackedColumnYMarker(y)) {
            int idx = CLO_PACKED_Y_COLUMN_INDICATOR - y;
            int localX = Coords.indexToColumnX(idx);
            return Coords.cubeToSection(x, localX);
        } else {
            return x;
        }
    }

    public static int extractY(long packed) {
        int y = extractRawY(packed);
        if (isPackedColumnYMarker(y)) {
            throw new IllegalArgumentException("Column CloPos doesn't have a Y coordinate!");
        }
        return y;
    }

    public static int extractZ(long packed) {
        int y = extractRawY(packed);
        int z = extractRawZ(packed);
        if (isPackedColumnYMarker(y)) {
            int idx = CLO_PACKED_Y_COLUMN_INDICATOR - y;
            int localZ = Coords.indexToColumnZ(idx);
            return Coords.cubeToSection(z, localZ);
        } else {
            return z;
        }
    }

    public static boolean isCube(long packed) {
        return !isPackedColumnYMarker(extractRawY(packed));
    }

    public static boolean isColumn(long packed) {
        return isPackedColumnYMarker(extractRawY(packed));
    }

    public CubePos cubePos() {
        if (isColumn()) {
            throw new UnsupportedOperationException("Calling getY() on column CloPos");
        }
        return CubePos.of(this.x, this.y, this.z);
    }

    public ChunkPos chunkPos() {
        if (isCube()) {
            throw new UnsupportedOperationException("Calling getY() on cube CloPos");
        }
        return new ChunkPos(this.x, this.z);
    }

    public CloPos correspondingCubeCloPos(int y) {
        if (isCube()) {
            return this;
        } else {
            return CloPos.cube(Coords.sectionToCube(this.x), y, Coords.sectionToCube(this.z));
        }
    }

    public CubePos correspondingCubePos(int y) {
        if (isCube()) {
            return cubePos();
        } else {
            throw new UnsupportedOperationException(); // TODO
//            return CubePos.fromColumn(this.x, this.z, y);
        }
    }

    public ChunkPos correspondingChunkPos() {
        return correspondingChunkPos(0, 0);
    }

    public ChunkPos correspondingChunkPos(int localX, int localZ) {
        if (isCube()) {
            return new ChunkPos(Coords.cubeToSection(this.x, localX), Coords.cubeToSection(this.z, localZ));
        } else {
            return new ChunkPos(this.x, this.z);
        }
    }

    public CloPos correspondingColumnCloPos() {
        return correspondingColumnCloPos(0, 0);
    }

    public CloPos correspondingColumnCloPos(int localX, int localZ) {
        if (isCube()) {
            return CloPos.column(Coords.cubeToSection(this.x, localX), Coords.cubeToSection(this.z, localZ));
        } else {
            return CloPos.column(this.x, this.z);
        }
    }

    public void forEachNeighbor(Consumer<? super CloPos> consumer) {
        if (isCube()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0 || dz != 0)
                            consumer.accept(CloPos.cube(this.x + dx, this.y + dy, this.z + dz));
                    }
                }
            }
            for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    consumer.accept(correspondingColumnCloPos(dx, dz));
                }
            }
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0)
                        consumer.accept(CloPos.column(this.x + dx, this.z + dz));
                }
            }
        }
    }






    public static void forEachNeighbor(long packed, LongConsumer consumer) {
        if (isCube(packed)) {
            int x = extractRawX(packed);
            int y = extractRawY(packed);
            int z = extractRawZ(packed);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0 || dz != 0)
                            consumer.accept(CloPos.asLong(x + dx, y + dy, z + dz));
                    }
                }
            }
            for (int dx = 0; dx < CubicConstants.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < CubicConstants.DIAMETER_IN_SECTIONS; dz++) {
                    int markY = packedColumnYMarker(x + dx, z + dz);
                    consumer.accept(setRawY(packed, markY));
                }
            }
        } else {
            int x = extractX(packed);
            int z = extractZ(packed);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0)
                        consumer.accept(CloPos.asLong(x + dx, z + dz));
                }
            }
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloPos cloPos = (CloPos) o;
        return x == cloPos.x && y == cloPos.y && z == cloPos.z;
    }

    @Override public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override public String toString() {
        if (isCube()) {
            return "CloPos{" +
                "cube" +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
        }
        return "CloPos{" +
            "column" +
            ", x=" + x +
            ", z=" + z +
            '}';
    }
}
