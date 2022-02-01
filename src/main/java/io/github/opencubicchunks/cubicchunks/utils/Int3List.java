package io.github.opencubicchunks.cubicchunks.utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.LongConsumer;

import io.netty.util.internal.PlatformDependent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public class Int3List implements AutoCloseable {
    protected static final long X_VALUE_OFFSET = 0;
    protected static final long Y_VALUE_OFFSET = X_VALUE_OFFSET + Integer.BYTES;
    protected static final long Z_VALUE_OFFSET = Y_VALUE_OFFSET + Integer.BYTES;

    protected static final long VALUE_SIZE = Z_VALUE_OFFSET + Integer.BYTES;

    protected static final int DEFAULT_CAPACITY = 16;

    static {
        if (!PlatformDependent.isUnaligned()) {
            throw new AssertionError("your CPU doesn't support unaligned memory access!");
        }
    }

    protected long arrayAddr = 0;
    protected boolean closed = false;

    protected int capacity;
    protected int size;

    public Int3List() {
        this.capacity = DEFAULT_CAPACITY;
        this.size = 0;
    }

    public boolean add(int x, int y, int z) {
        long arrayAddr = this.arrayAddr;
        if (this.arrayAddr == 0) {
            arrayAddr = this.arrayAddr = allocateTable(capacity);
        }

        if (this.size >= this.capacity) {
            arrayAddr = resize();
        }

        long putAt = arrayAddr + this.size * VALUE_SIZE;
        PlatformDependent.putInt(putAt + X_VALUE_OFFSET, x);
        PlatformDependent.putInt(putAt + Y_VALUE_OFFSET, y);
        PlatformDependent.putInt(putAt + Z_VALUE_OFFSET, z);

        this.size++;

        return true;
    }

    public void set(int index, int x, int y, int z) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        long arrayAddr = this.arrayAddr;
        if (this.arrayAddr == 0) {
            arrayAddr = this.arrayAddr = allocateTable(capacity);
        }

        long putAt = arrayAddr + index * VALUE_SIZE;
        PlatformDependent.putInt(putAt + X_VALUE_OFFSET, x);
        PlatformDependent.putInt(putAt + Y_VALUE_OFFSET, y);
        PlatformDependent.putInt(putAt + Z_VALUE_OFFSET, z);
    }

    public void insert(int index, int x, int y, int z) {
        if (index > this.size) {
            throw new IndexOutOfBoundsException();
        }

        long arrayAddr = this.arrayAddr;
        if (this.arrayAddr == 0) {
            arrayAddr = this.arrayAddr = allocateTable(capacity);
        }

        if (size >= capacity) {
            resizeAndInsert(index, x, y, z);
            return;
        }

        //Shift all values that come after it
        for (int j = size - 1; j >= index; j--) {
            long copyFrom = arrayAddr + j * VALUE_SIZE;
            long copyTo = copyFrom + VALUE_SIZE;

            PlatformDependent.copyMemory(copyFrom, copyTo, VALUE_SIZE);
        }

        this.size++;
        set(index, x, y, z);
    }

    public void remove(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        long arrayAddr = this.arrayAddr;
        if (this.arrayAddr == 0) {
            arrayAddr = this.arrayAddr = allocateTable(capacity);
        }

        //Shift all values back one
        for (int j = index + 1; j < size; j++) {
            long copyFrom = arrayAddr + j * VALUE_SIZE;
            long copyTo = copyFrom - VALUE_SIZE;

            PlatformDependent.copyMemory(copyFrom, copyTo, VALUE_SIZE);
        }

        this.size--;
    }

    public boolean remove(int x, int y, int z) {
        for (int index = 0; index < size; index++) {
            if (getX(index) == x && getY(index) == y && getZ(index) == z) {
                remove(index);
                return true;
            }
        }
        return false;
    }

    public void addAll(Collection<Vec3i> positions) {
        int necessaryCapacity = this.size + positions.size();

        if (necessaryCapacity > capacity) {
            resizeToFit((int) (necessaryCapacity * 1.5f));
        }

        int start = this.size;
        this.size += positions.size();

        Iterator<Vec3i> iterator = positions.iterator();

        for (; start < this.size; start++) {
            Vec3i item = iterator.next();
            set(start, item.getX(), item.getY(), item.getZ());
        }
    }

    public Vec3i[] toArray() {
        Vec3i[] array = new Vec3i[size];

        for (int i = 0; i < size; i++) {
            array[i] = getVec3i(i);
        }

        return array;
    }

    public long[] toLongArray() {
        long[] array = new long[size];

        for (int i = 0; i < size; i++) {
            array[i] = getAsBlockPos(i);
        }

        return array;
    }

    public int getX(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        if (this.arrayAddr == 0) {
            this.arrayAddr = allocateTable(capacity);
        }

        return PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + X_VALUE_OFFSET);
    }

    public int getY(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        if (this.arrayAddr == 0) {
            this.arrayAddr = allocateTable(capacity);
        }

        return PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + Y_VALUE_OFFSET);
    }

    public int getZ(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        if (this.arrayAddr == 0) {
            this.arrayAddr = allocateTable(capacity);
        }

        return PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + Z_VALUE_OFFSET);
    }

    public long getAsBlockPos(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        if (this.arrayAddr == 0) {
            this.arrayAddr = allocateTable(capacity);
        }

        return BlockPos.asLong(
            PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + X_VALUE_OFFSET),
            PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + Y_VALUE_OFFSET),
            PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + Z_VALUE_OFFSET)
        );
    }

    public Vec3i getVec3i(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException();
        }

        if (this.arrayAddr == 0) {
            this.arrayAddr = allocateTable(capacity);
        }

        return new Vec3i(
            PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + X_VALUE_OFFSET),
            PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + Y_VALUE_OFFSET),
            PlatformDependent.getInt(arrayAddr + index * VALUE_SIZE + Z_VALUE_OFFSET)
        );
    }

    public void forEach(LongConsumer consumer) {
        for (int i = 0; i < size; i++) {
            consumer.accept(getAsBlockPos(i));
        }
    }

    public void forEach(XYZConsumer consumer) {
        for (int i = 0; i < size; i++) {
            consumer.accept(getX(i), getY(i), getZ(i));
        }
    }

    public int size() {
        return size;
    }

    private long resizeToFit(int capacity) {
        this.capacity = capacity;
        return this.arrayAddr = PlatformDependent.reallocateMemory(this.arrayAddr, capacity * VALUE_SIZE);
    }

    private void resizeAndInsert(int index, int x, int y, int z) {
        while (this.capacity <= this.size) {
            this.capacity <<= 1;
        }

        long newArrayAddr = allocateTable(this.capacity);

        PlatformDependent.copyMemory(this.arrayAddr, newArrayAddr, index * VALUE_SIZE);
        PlatformDependent.copyMemory(this.arrayAddr + index * VALUE_SIZE, newArrayAddr + (index + 1) * VALUE_SIZE, (size - index) * VALUE_SIZE);

        PlatformDependent.freeMemory(this.arrayAddr);
        this.arrayAddr = newArrayAddr;

        this.size++;
        set(index, x, y, z);
    }

    private long resize() {
        while (this.capacity <= this.size) {
            this.capacity <<= 1;
        }

        return this.arrayAddr = PlatformDependent.reallocateMemory(this.arrayAddr, this.capacity * VALUE_SIZE);
    }

    protected static long allocateTable(int capacity) {
        long addr = PlatformDependent.allocateMemory(capacity * VALUE_SIZE);
        PlatformDependent.setMemory(addr, capacity * VALUE_SIZE, (byte) 0);
        return addr;
    }

    @Override
    public void close() {
        if (closed) return;

        closed = true;
        if (arrayAddr != 0L) {
            PlatformDependent.freeMemory(arrayAddr);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finalize() {
        close();
    }

    public void clear() {
        this.size = 0;
    }
}
