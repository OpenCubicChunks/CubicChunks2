package io.github.opencubicchunks.cubicchunks.utils;

import io.netty.util.internal.PlatformDependent;

/**
 * A fast hash-map implementation for 3-dimensional vectors with {@code int} components, mapped to unsigned {@code byte} values.
 * <p>
 * Optimized for the case where queries will be close to each other.
 * <p>
 * Not thread-safe. Attempting to use this concurrently from multiple threads will likely have catastrophic results (read: JVM crashes).
 *
 * @author DaPorkchop_
 */
public class Int3UByteLinkedHashMap implements AutoCloseable {
    public static final int DEFAULT_RETURN_VALUE = -1;

    protected static final int BUCKET_AXIS_BITS = 2; //the number of bits per axis which are used inside of the bucket rather than identifying the bucket
    protected static final int BUCKET_AXIS_MASK = (1 << BUCKET_AXIS_BITS) - 1;
    protected static final int BUCKET_SIZE = 1 << (BUCKET_AXIS_BITS * 3); //the number of entries per bucket

    /*
     * struct key_t {
     *   int x;
     *   int y;
     *   int z;
     * };
     */

    protected static final long KEY_X_OFFSET = 0L;
    protected static final long KEY_Y_OFFSET = KEY_X_OFFSET + Integer.BYTES;
    protected static final long KEY_Z_OFFSET = KEY_Y_OFFSET + Integer.BYTES;
    protected static final long KEY_BYTES = KEY_Z_OFFSET + Integer.BYTES;

    /*
     * struct value_t {
     *   long flags;
     *   byte vals[BUCKET_SIZE];
     * };
     */

    protected static final long VALUE_FLAGS_OFFSET = 0L;
    protected static final long VALUE_VALS_OFFSET = VALUE_FLAGS_OFFSET + Long.BYTES;
    protected static final long VALUE_BYTES = VALUE_VALS_OFFSET + BUCKET_SIZE * Byte.BYTES;

    /*
     * struct bucket_t {
     *   key_t key;
     *   value_t value;
     *   bucket_t* prev;
     *   bucket_t* next;
     * };
     */

    protected static final long BUCKET_KEY_OFFSET = 0L;
    protected static final long BUCKET_VALUE_OFFSET = BUCKET_KEY_OFFSET + KEY_BYTES;
    protected static final long BUCKET_PREV_OFFSET = BUCKET_VALUE_OFFSET + VALUE_BYTES;
    protected static final long BUCKET_NEXT_OFFSET = BUCKET_PREV_OFFSET + PlatformDependent.addressSize();
    protected static final long BUCKET_BYTES = BUCKET_NEXT_OFFSET + PlatformDependent.addressSize();

    protected static final long DEFAULT_TABLE_SIZE = 16L;

    static {
        if (!PlatformDependent.isUnaligned()) {
            throw new AssertionError("your CPU doesn't support unaligned memory access!");
        }
    }

    protected long tableAddr = 0L; //the address of the table in memory
    protected long tableSize = 0L; //the physical size of the table (in buckets). always a non-zero power of two
    protected long resizeThreshold = 0L;
    protected long usedBuckets = 0L;

    protected long size = 0L; //the number of values stored in the set

    protected long firstBucket = 0L; //pointer to the first known assigned bucket in the list
    protected long lastBucket = 0L; //pointer to the last known assigned bucket in the list

    protected boolean closed = false;

    public Int3UByteLinkedHashMap() {
        this.setTableSize(DEFAULT_TABLE_SIZE);
    }

    public Int3UByteLinkedHashMap(int initialCapacity) {
        initialCapacity = (int) Math.ceil(initialCapacity * (1.0d / 0.75d)); //scale according to resize threshold
        initialCapacity = 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(initialCapacity - 1)); //round up to next power of two
        this.setTableSize(Math.max(initialCapacity, DEFAULT_TABLE_SIZE));
    }

    protected static long getAddress(long addr) {
        if (PlatformDependent.addressSize() == Integer.BYTES) {
            return Integer.toUnsignedLong(PlatformDependent.getInt(addr));
        } else {
            return PlatformDependent.getLong(addr);
        }
    }

    protected static void putAddress(long addr, long value) {
        if (PlatformDependent.addressSize() == Integer.BYTES) {
            PlatformDependent.putInt(addr, (int) value);
        } else {
            PlatformDependent.putLong(addr, value);
        }
    }

    /**
     * Faster memset routine (for small ranges) which JIT can optimize specifically for the range size.
     *
     * @param dstAddr the destination address
     */
    protected static void memsetZero(long dstAddr, long size) {
        long offset = 0L;

        while (size - offset >= Long.BYTES) { //copy as many longs as possible
            PlatformDependent.putLong(dstAddr + offset, 0L);
            offset += Long.BYTES;
        }

        while (size - offset >= Integer.BYTES) { //pad with ints
            PlatformDependent.putInt(dstAddr + offset, 0);
            offset += Integer.BYTES;
        }

        while (size - offset >= Byte.BYTES) { //pad with bytes
            PlatformDependent.putByte(dstAddr + offset, (byte) 0);
            offset += Byte.BYTES;
        }

        assert offset == size;
    }

    /**
     * Faster memcpy routine (for small ranges) which JIT can optimize specifically for the range size.
     *
     * @param srcAddr the source address
     * @param dstAddr the destination address
     */
    protected static void memcpy(long srcAddr, long dstAddr, long size) {
        long offset = 0L;

        while (size - offset >= Long.BYTES) { //copy as many longs as possible
            PlatformDependent.putLong(dstAddr + offset, PlatformDependent.getLong(srcAddr + offset));
            offset += Long.BYTES;
        }

        while (size - offset >= Integer.BYTES) { //pad with ints
            PlatformDependent.putInt(dstAddr + offset, PlatformDependent.getInt(srcAddr + offset));
            offset += Integer.BYTES;
        }

        while (size - offset >= Byte.BYTES) { //pad with bytes
            PlatformDependent.putByte(dstAddr + offset, PlatformDependent.getByte(srcAddr + offset));
            offset += Byte.BYTES;
        }

        assert offset == size;
    }

    protected static long hashPosition(int x, int y, int z) {
        return x * 1403638657883916319L //some random prime numbers
            + y * 4408464607732138253L
            + z * 2587306874955016303L;
    }

    protected static int positionIndex(int x, int y, int z) {
        return ((x & BUCKET_AXIS_MASK) << (BUCKET_AXIS_BITS * 2)) | ((y & BUCKET_AXIS_MASK) << BUCKET_AXIS_BITS) | (z & BUCKET_AXIS_MASK);
    }

    protected static long positionFlag(int x, int y, int z) {
        return 1L << positionIndex(x, y, z);
    }

    protected static long allocateTable(long tableSize) {
        long size = tableSize * BUCKET_BYTES;
        long addr = PlatformDependent.allocateMemory(size); //allocate
        PlatformDependent.setMemory(addr, size, (byte) 0); //clear
        return addr;
    }

    /**
     * Inserts an entry into this map at the given position with the given value.
     * <p>
     * If an entry with the given position is already present in this map, it will be replaced.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     * @param value the value to insert. Must be an unsigned {@code byte}
     *
     * @return the previous entry's value, or {@link #DEFAULT_RETURN_VALUE} if no such entry was present
     *
     * @see java.util.Map#put(Object, Object)
     */
    public int put(int x, int y, int z, int value) {
        assert (value & 0xFF) == value : "value not in range [0,255]: " + value;

        int index = positionIndex(x, y, z);
        long flag = positionFlag(x, y, z);
        long bucket = this.findBucket(x >> BUCKET_AXIS_BITS, y >> BUCKET_AXIS_BITS, z >> BUCKET_AXIS_BITS, true);

        int oldValue;
        long flags = PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET);
        if ((flags & flag) == 0L) { //flag wasn't previously set
            PlatformDependent.putLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET, flags | flag);
            this.size++; //the position was newly added, so we need to increment the total size
            oldValue = DEFAULT_RETURN_VALUE;
        } else { //the flag was already set
            oldValue = PlatformDependent.getByte(bucket + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + index * Byte.BYTES) & 0xFF;
        }

        //store value into bucket
        PlatformDependent.putByte(bucket + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + index * Byte.BYTES, (byte) value);
        return oldValue;
    }

    /**
     * Inserts an entry into this map at the given position with the given value.
     * <p>
     * If an entry with the given position is already present in this map, the map will not be modified.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     * @param value the value to insert. Must be an unsigned {@code byte}
     *
     * @return the previous entry's value, or {@link #DEFAULT_RETURN_VALUE} if no such entry was present and the entry was inserted
     *
     * @see java.util.Map#putIfAbsent(Object, Object)
     */
    public int putIfAbsent(int x, int y, int z, int value) {
        assert (value & 0xFF) == value : "value not in range [0,255]: " + value;

        int index = positionIndex(x, y, z);
        long flag = positionFlag(x, y, z);
        long bucket = this.findBucket(x >> BUCKET_AXIS_BITS, y >> BUCKET_AXIS_BITS, z >> BUCKET_AXIS_BITS, true);

        long flags = PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET);
        if ((flags & flag) == 0L) { //flag wasn't previously set
            PlatformDependent.putLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET, flags | flag);
            this.size++; //the position was newly added, so we need to increment the total size
            PlatformDependent.putByte(bucket + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + index * Byte.BYTES, (byte) value);
            return DEFAULT_RETURN_VALUE;
        } else { //the flag was already set
            return PlatformDependent.getByte(bucket + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + index * Byte.BYTES) & 0xFF;
        }
    }

    /**
     * Checks whether or not an entry at the given position is present in this map.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     *
     * @return whether or not the position is present
     *
     * @see java.util.Map#containsKey(Object)
     */
    public boolean containsKey(int x, int y, int z) {
        long flag = positionFlag(x, y, z);
        long bucket = this.findBucket(x >> BUCKET_AXIS_BITS, y >> BUCKET_AXIS_BITS, z >> BUCKET_AXIS_BITS, false);

        return bucket != 0L //bucket exists
            && (PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) & flag) != 0L; //flag is set
    }

    /**
     * Gets the value of the entry associated with the given position.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     *
     * @return the entry's value, or {@link #DEFAULT_RETURN_VALUE} if no such entry was present
     *
     * @see java.util.Map#get(Object)
     */
    public int get(int x, int y, int z) {
        int index = positionIndex(x, y, z);
        long flag = positionFlag(x, y, z);
        long bucket = this.findBucket(x >> BUCKET_AXIS_BITS, y >> BUCKET_AXIS_BITS, z >> BUCKET_AXIS_BITS, false);

        if (bucket != 0L //bucket exists
            && (PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) & flag) != 0L) { //flag is set
            return PlatformDependent.getByte(bucket + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + index * Byte.BYTES) & 0xFF;
        } else { //bucket doesn't exist or doesn't contain the position
            return DEFAULT_RETURN_VALUE;
        }
    }

    protected long findBucket(int x, int y, int z, boolean createIfAbsent) {
        long tableSize = this.tableSize;
        long tableAddr = this.tableAddr;
        if (tableAddr == 0L) {
            if (createIfAbsent) { //the table hasn't been allocated yet - let's make a new one!
                this.tableAddr = tableAddr = allocateTable(tableSize);
            } else { //the table isn't even allocated yet, so the bucket clearly isn't present
                return 0L;
            }
        }

        long mask = tableSize - 1L; //tableSize is always a power of two, so we can safely create a bitmask like this
        long hash = hashPosition(x, y, z);

        for (long i = 0L; ; i++) {
            long bucketAddr = tableAddr + ((hash + i) & mask) * BUCKET_BYTES;

            if (PlatformDependent.getLong(bucketAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) == 0L) { //if the value's flags are 0, it means the bucket hasn't been assigned yet
                if (createIfAbsent) {
                    if (this.usedBuckets < this.resizeThreshold) { //let's assign the bucket to our current position
                        this.usedBuckets++;
                        PlatformDependent.putInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET, x);
                        PlatformDependent.putInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET, y);
                        PlatformDependent.putInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET, z);

                        //add bucket to linked list
                        long prevBucket = 0L;
                        long nextBucket = 0L;
                        if (this.firstBucket == 0L) { //no other buckets exist
                            this.firstBucket = bucketAddr;
                        } else { //there are other buckets, let's insert this bucket at the back of the list
                            putAddress(this.lastBucket + BUCKET_NEXT_OFFSET, bucketAddr);
                            prevBucket = this.lastBucket;
                        }
                        putAddress(bucketAddr + BUCKET_PREV_OFFSET, prevBucket);
                        putAddress(bucketAddr + BUCKET_NEXT_OFFSET, nextBucket);
                        this.lastBucket = bucketAddr;

                        return bucketAddr;
                    } else {
                        //we've established that there's no matching bucket, but the table is full. let's resize it before allocating a bucket
                        // to avoid overfilling the table
                        this.resize();
                        return this.findBucket(x, y, z, createIfAbsent); //tail recursion will probably be optimized away
                    }
                } else { //empty bucket, abort search - there won't be anything else later on
                    return 0L;
                }
            }

            //the bucket is set. check coordinates to see if it matches the one we're searching for
            if (PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET) == x
                && PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET) == y
                && PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET) == z) { //we found the matching bucket!
                return bucketAddr;
            }

            //continue search...
        }
    }

    protected void resize() {
        long oldTableSize = this.tableSize;
        long oldTableAddr = this.tableAddr;

        //allocate new table
        long newTableSize = oldTableSize << 1L;
        this.setTableSize(newTableSize);
        long newTableAddr = this.tableAddr = allocateTable(newTableSize);
        long newMask = newTableSize - 1L;

        //iterate through every bucket in the old table and copy it to the new one
        for (long i = 0; i < oldTableSize; i++) {
            long oldBucketAddr = oldTableAddr + i * BUCKET_BYTES;

            //read the key into registers
            int x = PlatformDependent.getInt(oldBucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET);
            int y = PlatformDependent.getInt(oldBucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET);
            int z = PlatformDependent.getInt(oldBucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET);
            if (PlatformDependent.getLong(oldBucketAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) == 0L) { //the bucket is unset, so there's no reason to copy it
                continue;
            }

            for (long hash = hashPosition(x, y, z), j = 0L; ; j++) {
                long newBucketAddr = newTableAddr + ((hash + j) & newMask) * BUCKET_BYTES;

                if (PlatformDependent.getLong(newBucketAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) == 0L) { //if the bucket value is 0, it means the bucket hasn't been assigned yet
                    //write bucket into new table
                    PlatformDependent.putInt(newBucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET, x);
                    PlatformDependent.putInt(newBucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET, y);
                    PlatformDependent.putInt(newBucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET, z);
                    memcpy(oldBucketAddr + BUCKET_VALUE_OFFSET, newBucketAddr + BUCKET_VALUE_OFFSET, VALUE_BYTES);
                    break; //advance to next bucket in old table
                }

                //continue search...
            }
        }

        //delete old table
        PlatformDependent.freeMemory(oldTableAddr);

        //iterate through every bucket in the new table and append non-empty buckets to the new linked list
        long prevBucket = 0L;
        for (long i = 0; i < newTableSize; i++) {
            long bucketAddr = newTableAddr + i * BUCKET_BYTES;

            if (PlatformDependent.getLong(bucketAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) == 0L) { //the bucket is unset, so there's no reason to add it to the list
                continue;
            }

            if (prevBucket == 0L) { //this is first bucket we've encountered in the list so far
                prevBucket = bucketAddr;
                this.firstBucket = bucketAddr;
            } else { //append current bucket to list
                putAddress(prevBucket + BUCKET_NEXT_OFFSET, bucketAddr);
                putAddress(bucketAddr + BUCKET_PREV_OFFSET, prevBucket);
                prevBucket = bucketAddr;
            }
        }
        this.lastBucket = prevBucket;
    }

    /**
     * Runs the given function on every entry in this map.
     *
     * @param action the function to run
     *
     * @see java.util.Map#forEach(java.util.function.BiConsumer)
     */
    public void forEach(EntryConsumer action) {
        long tableAddr = this.tableAddr;
        if (tableAddr == 0L) { //the table isn't even allocated yet, there's nothing to iterate through...
            return;
        }

        //haha yes, c-style iterators
        for (long bucket = tableAddr, end = tableAddr + this.tableSize * BUCKET_BYTES; bucket != end; bucket += BUCKET_BYTES) {
            //read the bucket's key and flags into registers
            int bucketX = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_X_OFFSET);
            int bucketY = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_Y_OFFSET);
            int bucketZ = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_Z_OFFSET);
            long flags = PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET);

            while (flags != 0L) {
                //this is intrinsic and compiles into TZCNT, which has a latency of 3 cycles - much faster than iterating through all 64 bits
                //  and checking each one individually!
                int index = Long.numberOfTrailingZeros(flags);

                //clear the bit in question so that it won't be returned next time around
                flags &= ~(1L << index);

                int dx = index >> (BUCKET_AXIS_BITS * 2);
                int dy = (index >> BUCKET_AXIS_BITS) & BUCKET_AXIS_MASK;
                int dz = index & BUCKET_AXIS_MASK;
                int val = PlatformDependent.getByte(bucket + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + index * Byte.BYTES) & 0xFF;
                action.accept((bucketX << BUCKET_AXIS_BITS) + dx, (bucketY << BUCKET_AXIS_BITS) + dy, (bucketZ << BUCKET_AXIS_BITS) + dz, val);
            }
        }
    }

    /**
     * Removes the entry at the given position from this map.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     *
     * @return the old value at the given position, or {@link #DEFAULT_RETURN_VALUE} if the position wasn't present
     *
     * @see java.util.Map#remove(Object)
     */
    public int remove(int x, int y, int z) {
        long tableAddr = this.tableAddr;
        if (tableAddr == 0L) { //the table isn't even allocated yet, there's nothing to remove...
            return DEFAULT_RETURN_VALUE;
        }

        long mask = this.tableSize - 1L; //tableSize is always a power of two, so we can safely create a bitmask like this

        long flag = positionFlag(x, y, z);
        int searchBucketX = x >> BUCKET_AXIS_BITS;
        int searchBucketY = y >> BUCKET_AXIS_BITS;
        int searchBucketZ = z >> BUCKET_AXIS_BITS;
        long hash = hashPosition(searchBucketX, searchBucketY, searchBucketZ);

        for (long i = 0L; ; i++) {
            long bucketAddr = tableAddr + ((hash + i) & mask) * BUCKET_BYTES;

            //read the bucket's key and flags into registers
            int bucketX = PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET);
            int bucketY = PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET);
            int bucketZ = PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET);
            long flags = PlatformDependent.getLong(bucketAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET);
            if (flags == 0L) { //the bucket is unset. we've reached the end of the bucket chain for this hash, which means it doesn't exist
                return DEFAULT_RETURN_VALUE;
            } else if (bucketX != searchBucketX || bucketY != searchBucketY || bucketZ != searchBucketZ) { //the bucket doesn't match, so the search must go on
                continue;
            } else if ((flags & flag) == 0L) { //we've found a matching bucket, but the position's flag is unset. there's nothing for us to do...
                return DEFAULT_RETURN_VALUE;
            }

            //load the old value in order to return it later (there's no reason to zero it out, since the flag bit will be cleared anyway)
            int oldVal = PlatformDependent.getByte(bucketAddr + BUCKET_VALUE_OFFSET + VALUE_VALS_OFFSET + positionIndex(x, y, z) * Byte.BYTES) & 0xFF;

            //the bucket that we found contains the position, so now we remove it from the set
            this.size--;

            //update bucket flags
            flags &= ~flag;
            PlatformDependent.putLong(bucketAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET, flags);

            if (flags == 0L) { //this position was the only position in the bucket, so we need to delete the bucket
                this.usedBuckets--;

                //remove the bucket from the linked list
                long prevBucket = getAddress(bucketAddr + BUCKET_PREV_OFFSET);
                long nextBucket = getAddress(bucketAddr + BUCKET_NEXT_OFFSET);

                if (prevBucket == 0L) { //previous bucket is nullptr, meaning the current bucket used to be at the front
                    this.firstBucket = nextBucket;
                } else {
                    putAddress(prevBucket + BUCKET_NEXT_OFFSET, nextBucket);
                }
                if (nextBucket == 0L) { //next bucket is nullptr, meaning the current bucket used to be at the back
                    this.lastBucket = prevBucket;
                } else {
                    putAddress(nextBucket + BUCKET_PREV_OFFSET, prevBucket);
                }

                //shifting the buckets IS expensive, yes, but it'll only happen when the entire bucket is deleted, which won't happen on every removal
                this.shiftBuckets(tableAddr, (hash + i) & mask, mask);
            }

            return oldVal;
        }
    }

    //adapted from it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap#shiftKeys(int)
    protected void shiftBuckets(long tableAddr, long pos, long mask) {
        long last;
        long slot;

        for (; ; ) {
            pos = ((last = pos) + 1L) & mask;

            for (; ; pos = (pos + 1L) & mask) {
                long currAddr = tableAddr + pos * BUCKET_BYTES;
                if (PlatformDependent.getLong(currAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET) == 0L) { //curr points to an unset bucket
                    PlatformDependent.putLong(tableAddr + last * BUCKET_BYTES, 0L); //delete last bucket
                    return;
                }

                slot = hashPosition(
                    PlatformDependent.getInt(currAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET),
                    PlatformDependent.getInt(currAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET),
                    PlatformDependent.getInt(currAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET)) & mask;

                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) { //move the bucket
                    long newAddr = tableAddr + last * BUCKET_BYTES;

                    //copy bucket to new address
                    memcpy(currAddr, newAddr, BUCKET_BYTES);

                    //clear flags in bucket's old position to mark it as empty
                    PlatformDependent.putLong(currAddr + BUCKET_VALUE_OFFSET + VALUE_FLAGS_OFFSET, 0L);

                    //update pointer to self in linked list neighbors
                    long prevBucket = getAddress(currAddr + BUCKET_PREV_OFFSET);
                    long nextBucket = getAddress(currAddr + BUCKET_NEXT_OFFSET);
                    if (prevBucket == 0L) { //previous bucket is nullptr, meaning the current bucket used to be at the front
                        this.firstBucket = newAddr;
                    } else {
                        putAddress(prevBucket + BUCKET_NEXT_OFFSET, newAddr);
                    }
                    if (nextBucket == 0L) { //next bucket is nullptr, meaning the current bucket used to be at the back
                        this.lastBucket = newAddr;
                    } else {
                        putAddress(nextBucket + BUCKET_PREV_OFFSET, newAddr);
                    }

                    break;
                }
            }
        }
    }

    /**
     * Removes every entry from this set.
     *
     * @see java.util.Map#clear()
     */
    public void clear() {
        if (this.isEmpty()) { //if the set is empty, there's nothing to clear
            return;
        }

        //fill the entire table with zeroes
        // (since the table isn't empty, we can be sure that the table has been allocated so there's no reason to check for it)
        PlatformDependent.setMemory(this.tableAddr, this.tableSize * BUCKET_BYTES, (byte) 0);

        //reset all size counters
        this.usedBuckets = 0L;
        this.size = 0L;
        this.firstBucket = 0L;
    }

    protected void setTableSize(long tableSize) {
        this.tableSize = tableSize;
        this.resizeThreshold = (tableSize >> 1L) + (tableSize >> 2L); //count * 0.75
    }

    /**
     * @return the number of entries stored in this map
     */
    public long size() {
        return this.size;
    }

    /**
     * @return whether or not this map is empty (contains no entries)
     */
    public boolean isEmpty() {
        return this.size == 0L;
    }

    /**
     * Irrevocably releases the resources claimed by this instance.
     * <p>
     * Once this method has been called, all methods in this class will produce undefined behavior.
     */
    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;

        //actually release memory
        if (this.tableAddr != 0L) {
            PlatformDependent.freeMemory(this.tableAddr);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() {
        //using a finalizer is bad, i know. however, there's no other reasonable way for me to clean up the memory without pulling in PorkLib:unsafe or
        // using sun.misc.Cleaner directly...
        this.close();
    }

    /**
     * A function which accepts a map entry (consisting of 3 {@code int}s for the key and 1 {@code int} for the value) as a parameter.
     */
    @FunctionalInterface
    public interface EntryConsumer {
        void accept(int x, int y, int z, int value);
    }
}
