package io.github.opencubicchunks.cubicchunks.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import io.netty.util.internal.PlatformDependent;
import net.minecraft.core.BlockPos;
import org.apache.commons.lang3.mutable.MutableInt;

/**
 * Modification of DaPorkchop_'s original {@code Int3HashSet} which keeps track of a linked list which
 * allows for easy lookup of elements. This makes it a bit slower and uses up more memory
 * <br><br>
 * Original Description (By DaPorkchop_):
 * <br>
 * A fast hash-set implementation for 3-dimensional vectors with {@code int} components.
 * <p>
 *   Optimized for the case where queries will be close to each other.
 * <p>
 *   Not thread-safe. Attempting to use this concurrently from multiple threads will likely have catastrophic results (read: JVM crashes).
 *
 * @author DaPorkchop_ & Salamander
 */
public class LinkedInt3HashSet implements AutoCloseable{
    protected static final long KEY_X_OFFSET = 0L;
    protected static final long KEY_Y_OFFSET = KEY_X_OFFSET + Integer.BYTES;
    protected static final long KEY_Z_OFFSET = KEY_Y_OFFSET + Integer.BYTES;
    protected static final long KEY_BYTES = KEY_Z_OFFSET + Integer.BYTES;

    protected static final long VALUE_BYTES = Long.BYTES;
    protected static final long NEXT_VALUE_BYTES = Long.BYTES;
    protected static final long PREV_VALUE_BYTES = Long.BYTES;
    protected static final long NEXT_VALUE_OFFSET = KEY_BYTES + VALUE_BYTES;
    protected static final long PREV_VALUE_OFFSET = NEXT_VALUE_OFFSET + NEXT_VALUE_BYTES;

    protected static final long BUCKET_KEY_OFFSET = 0L;
    protected static final long BUCKET_VALUE_OFFSET = BUCKET_KEY_OFFSET + KEY_BYTES;
    protected static final long BUCKET_BYTES = BUCKET_VALUE_OFFSET + VALUE_BYTES + NEXT_VALUE_BYTES + PREV_VALUE_BYTES;

    protected static final long DEFAULT_TABLE_SIZE = 16L;

    protected static final int BUCKET_AXIS_BITS = 2; //the number of bits per axis which are used inside of the bucket rather than identifying the bucket
    protected static final int BUCKET_AXIS_MASK = (1 << BUCKET_AXIS_BITS) - 1;
    protected static final int BUCKET_SIZE = (BUCKET_AXIS_MASK << (BUCKET_AXIS_BITS * 2)) | (BUCKET_AXIS_MASK << BUCKET_AXIS_BITS) | BUCKET_AXIS_MASK;

    protected static long hashPosition(int x, int y, int z) {
        return x * 1403638657883916319L //some random prime numbers
            + y * 4408464607732138253L
            + z * 2587306874955016303L;
    }

    protected static long positionFlag(int x, int y, int z) {
        return 1L << (((x & BUCKET_AXIS_MASK) << (BUCKET_AXIS_BITS * 2)) | ((y & BUCKET_AXIS_MASK) << BUCKET_AXIS_BITS) | (z & BUCKET_AXIS_MASK));
    }

    protected static long allocateTable(long tableSize) {
        long size = tableSize * BUCKET_BYTES;
        long addr = PlatformDependent.allocateMemory(size); //allocate
        PlatformDependent.setMemory(addr, size, (byte) 0); //clear
        return addr;
    }

    protected long tableAddr = 0L; //the address of the table in memory
    protected long tableSize = 0L; //the physical size of the table (in buckets). always a non-zero power of two
    protected long resizeThreshold = 0L;
    protected long usedBuckets = 0L;

    protected long size = 0L; //the number of values stored in the set

    protected boolean closed = false;

    protected long first = 0;
    protected long last = 0;

    public LinkedInt3HashSet() {
        this.setTableSize(DEFAULT_TABLE_SIZE);
    }

    public LinkedInt3HashSet(int initialCapacity) {
        initialCapacity = (int) Math.ceil(initialCapacity * (1.0d / 0.75d)); //scale according to resize threshold
        initialCapacity = 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(initialCapacity - 1)); //round up to next power of two
        this.setTableSize(Math.max(initialCapacity, DEFAULT_TABLE_SIZE));
    }

    /**
     * Adds the given position to this set.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     *
     * @return whether or not the position was added (i.e. was previously absent)
     *
     * @see java.util.Set#add(Object)
     */
    public boolean add(int x, int y, int z) {
        long flag = positionFlag(x, y, z);
        long bucket = this.findBucket(x >> BUCKET_AXIS_BITS, y >> BUCKET_AXIS_BITS, z >> BUCKET_AXIS_BITS, true);

        long value = PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET);
        if ((value & flag) == 0L) { //flag wasn't previously set
            PlatformDependent.putLong(bucket + BUCKET_VALUE_OFFSET, value | flag);
            this.size++; //the position was newly added, so we need to increment the total size
            return true;
        } else { //flag was already set
            return false;
        }
    }

    /**
     * Checks whether or not the given position is present in this set.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     *
     * @return whether or not the position is present
     *
     * @see java.util.Set#contains(Object)
     */
    public boolean contains(int x, int y, int z) {
        long flag = positionFlag(x, y, z);
        long bucket = this.findBucket(x >> BUCKET_AXIS_BITS, y >> BUCKET_AXIS_BITS, z >> BUCKET_AXIS_BITS, false);

        return bucket != 0L //bucket exists
            && (PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET) & flag) != 0L; //flag is set
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

            if (PlatformDependent.getLong(bucketAddr + BUCKET_VALUE_OFFSET) == 0L) { //if the bucket value is 0, it means the bucket hasn't been assigned yet
                if (createIfAbsent) {
                    if (this.usedBuckets < this.resizeThreshold) { //let's assign the bucket to our current position
                        this.usedBuckets++;
                        PlatformDependent.putInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET, x);
                        PlatformDependent.putInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET, y);
                        PlatformDependent.putInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET, z);

                        if(first == 0){
                            first = bucketAddr;
                        }

                        //If last is set, set the the last value's pointer to point here and set this pointer to last
                        if(last != 0){
                            PlatformDependent.putLong(last + NEXT_VALUE_OFFSET, bucketAddr);
                            PlatformDependent.putLong(bucketAddr + PREV_VALUE_OFFSET, last);
                        }

                        last = bucketAddr;
                        
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
        System.out.println("Resizing!");
        this.cachedIndex = -1; //Invalidate cached index

        long oldTableSize = this.tableSize;
        long oldTableAddr = this.tableAddr;

        //allocate new table
        long newTableSize = oldTableSize << 1L;
        this.setTableSize(newTableSize);
        long newTableAddr = this.tableAddr = allocateTable(newTableSize);
        long newMask = newTableSize - 1L;

        //iterate through every bucket in the old table and copy it to the new one
        long prevBucket = 0;
        long bucket = first;

        while (bucket != 0){
            int x = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_X_OFFSET);
            int y = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_Y_OFFSET);
            int z = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_Z_OFFSET);
            long value = PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET);

            long newBucketAddr;
            for(long hash = hashPosition(x, y, z), j = 0L; ; j++){
                newBucketAddr = newTableAddr + ((hash + j) & newMask) * BUCKET_BYTES;

                if(PlatformDependent.getLong(newBucketAddr + BUCKET_VALUE_OFFSET) == 0L){ //if the bucket value is 0, it means the bucket hasn't been assigned yet
                    PlatformDependent.putInt(newBucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET, x);
                    PlatformDependent.putInt(newBucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET, y);
                    PlatformDependent.putInt(newBucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET, z);
                    PlatformDependent.putLong(newBucketAddr + BUCKET_VALUE_OFFSET, value);

                    PlatformDependent.putLong(newBucketAddr + PREV_VALUE_OFFSET, prevBucket);

                    if(prevBucket == 0){
                        this.first = newBucketAddr;
                    }else{
                        PlatformDependent.putLong(prevBucket + NEXT_VALUE_OFFSET, newBucketAddr);
                    }
                    this.last = newBucketAddr;

                    break;
                }
            }

            prevBucket = newBucketAddr;
            bucket = PlatformDependent.getLong(bucket + NEXT_VALUE_OFFSET);
        }

        //delete old table
        PlatformDependent.freeMemory(oldTableAddr);
    }

    /**
     * Runs the given function on every position in this set.
     *
     * @param action the function to run
     *
     * @see java.util.Set#forEach(java.util.function.Consumer)
     */
    public void forEach(Int3HashSet.XYZConsumer action) {
        long tableAddr = this.tableAddr;
        if (tableAddr == 0L) { //the table isn't even allocated yet, there's nothing to iterate through...
            return;
        }

        long bucket = first;

        while (bucket != 0){
            int bucketX = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_X_OFFSET);
            int bucketY = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_Y_OFFSET);
            int bucketZ = PlatformDependent.getInt(bucket + BUCKET_KEY_OFFSET + KEY_Z_OFFSET);
            long value = PlatformDependent.getLong(bucket + BUCKET_VALUE_OFFSET);

            for (int i = 0; i <= BUCKET_SIZE; i++) { //check each flag in the bucket value to see if it's set
                if ((value & (1L << i)) == 0L) { //the flag isn't set
                    continue;
                }

                int dx = i >> (BUCKET_AXIS_BITS * 2);
                int dy = (i >> BUCKET_AXIS_BITS) & BUCKET_AXIS_MASK;
                int dz = i & BUCKET_AXIS_MASK;
                action.accept((bucketX << BUCKET_AXIS_BITS) + dx, (bucketY << BUCKET_AXIS_BITS) + dy, (bucketZ << BUCKET_AXIS_BITS) + dz);
            }

            bucket = PlatformDependent.getLong(bucket + NEXT_VALUE_OFFSET);
        }
    }

    /**
     * Removes the given position from this set.
     *
     * @param x the position's X coordinate
     * @param y the position's Y coordinate
     * @param z the position's Z coordinate
     *
     * @return whether or not the position was removed (i.e. was previously present)
     *
     * @see java.util.Set#remove(Object)
     */
    public boolean remove(int x, int y, int z) {
        long tableAddr = this.tableAddr;
        if (tableAddr == 0L) { //the table isn't even allocated yet, there's nothing to remove...
            return false;
        }

        cachedIndex = -1;

        long mask = this.tableSize - 1L; //tableSize is always a power of two, so we can safely create a bitmask like this

        long flag = positionFlag(x, y, z);
        int searchBucketX = x >> BUCKET_AXIS_BITS;
        int searchBucketY = y >> BUCKET_AXIS_BITS;
        int searchBucketZ = z >> BUCKET_AXIS_BITS;
        long hash = hashPosition(searchBucketX, searchBucketY, searchBucketZ);

        for (long i = 0L; ; i++) {
            long bucketAddr = tableAddr + ((hash + i) & mask) * BUCKET_BYTES;

            //read the bucket into registers
            int bucketX = PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET);
            int bucketY = PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET);
            int bucketZ = PlatformDependent.getInt(bucketAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET);
            long value = PlatformDependent.getLong(bucketAddr + BUCKET_VALUE_OFFSET);
            if (value == 0L) { //the bucket is unset. we've reached the end of the bucket chain for this hash, which means
                return false;
            } else if (bucketX != searchBucketX || bucketY != searchBucketY || bucketZ != searchBucketZ) { //the bucket doesn't match, so the search must go on
                continue;
            } else if ((value & flag) == 0L) { //we've found a matching bucket, but the position's flag is unset. there's nothing for us to do...
                return false;
            }

            //the bucket that we found contains the position, so now we remove it from the set
            this.size--;

            if ((value & ~flag) == 0L) { //this position is the only position in the bucket, so we need to delete the bucket
                removeBucket(bucketAddr);

                //shifting the buckets IS expensive, yes, but it'll only happen when the entire bucket is deleted, which won't happen on every removal
                this.shiftBuckets(tableAddr, (hash + i) & mask, mask);
            } else { //update bucket value with this position removed
                PlatformDependent.putLong(bucketAddr + BUCKET_VALUE_OFFSET, value & ~flag);
            }

            return true;
        }
    }

    protected void removeBucket(long bucketAddr){
        this.usedBuckets--;

        patchRemoval(bucketAddr);
        if(bucketAddr == this.first){
            long newFirst = PlatformDependent.getLong(bucketAddr + NEXT_VALUE_OFFSET);
            if(newFirst != 0){
                PlatformDependent.putLong(newFirst + PREV_VALUE_OFFSET, 0);
            }
            this.first = newFirst;
        }

        if(bucketAddr == this.last){
            long newLast = PlatformDependent.getLong(bucketAddr + PREV_VALUE_OFFSET);
            if(newLast != 0)
                PlatformDependent.putLong(newLast + NEXT_VALUE_OFFSET, 0);
            this.last = newLast;
        }
    }

    //adapted from it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap#shiftKeys(int)
    protected void shiftBuckets(long tableAddr, long pos, long mask) {
        long last;
        long slot;

        int currX;
        int currY;
        int currZ;
        long currValue;
        long currPrev;
        long currNext;
        long currAddr;

        for (; ; ) {
            pos = ((last = pos) + 1L) & mask;
            for (; ; pos = (pos + 1L) & mask) {
                currAddr = tableAddr + pos * BUCKET_BYTES;
                if ((currValue = PlatformDependent.getLong(currAddr + BUCKET_VALUE_OFFSET)) == 0L) { //curr points to an unset bucket
                    long ptr = tableAddr + last * BUCKET_BYTES;
                    PlatformDependent.setMemory(ptr, BUCKET_BYTES, (byte) 0); //delete last bucket
                    return;
                }

                slot = hashPosition(
                    currX = PlatformDependent.getInt(currAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET),
                    currY = PlatformDependent.getInt(currAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET),
                    currZ = PlatformDependent.getInt(currAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET)) & mask;

                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) {
                    currPrev = PlatformDependent.getLong(currAddr + PREV_VALUE_OFFSET);
                    currNext = PlatformDependent.getLong(currAddr + NEXT_VALUE_OFFSET);

                    break;
                }
            }

            long lastAddr = tableAddr + last * BUCKET_BYTES;

            patchMove(currAddr, lastAddr);

            PlatformDependent.putInt(lastAddr + BUCKET_KEY_OFFSET + KEY_X_OFFSET, currX);
            PlatformDependent.putInt(lastAddr + BUCKET_KEY_OFFSET + KEY_Y_OFFSET, currY);
            PlatformDependent.putInt(lastAddr + BUCKET_KEY_OFFSET + KEY_Z_OFFSET, currZ);
            PlatformDependent.putLong(lastAddr + BUCKET_VALUE_OFFSET, currValue);
            PlatformDependent.putLong(lastAddr + NEXT_VALUE_OFFSET, currNext);
            PlatformDependent.putLong(lastAddr + PREV_VALUE_OFFSET, currPrev);
        }
    }

    private void patchMove(long currPtr, long newPtr) {
        long ptrPrev = PlatformDependent.getLong(currPtr + PREV_VALUE_OFFSET);
        long ptrNext = PlatformDependent.getLong(currPtr + NEXT_VALUE_OFFSET);

        if(ptrPrev != 0){
            PlatformDependent.putLong(ptrPrev + NEXT_VALUE_OFFSET, newPtr);
        }

        if(ptrNext != 0){
            PlatformDependent.putLong(ptrNext + PREV_VALUE_OFFSET, newPtr);
        }

        if(currPtr == this.first){
            first = newPtr;
        }

        if(currPtr == this.last){
            this.last = newPtr;
        }
    }

    public void patchRemoval(long ptr){
        long ptrPrev = PlatformDependent.getLong(ptr + PREV_VALUE_OFFSET);
        long ptrNext = PlatformDependent.getLong(ptr + NEXT_VALUE_OFFSET);

        if(ptrPrev != 0){
            PlatformDependent.putLong(ptrPrev + NEXT_VALUE_OFFSET, ptrNext);
        }

        if(ptrNext != 0){
            PlatformDependent.putLong(ptrNext + PREV_VALUE_OFFSET, ptrPrev);
        }
    }

    /**
     * Removes every position from this set.
     *
     * @see java.util.Set#clear()
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

        cachedIndex = -1;

        this.first = 0L;
        this.last = 0L;
    }

    //Cached index of value
    int cachedIndex = -1;

    public int getFirstX(){
        if(size == 0)
            throw new NoSuchElementException();

        if(cachedIndex == -1){
            getFirstSetBitInFirstBucket();
        }

        int x = PlatformDependent.getInt(first + KEY_X_OFFSET);

        int dx = cachedIndex >> (BUCKET_AXIS_BITS * 2);
        return (x << BUCKET_AXIS_BITS) + dx;
    }
    public int getFirstY(){
        if(size == 0)
            throw new NoSuchElementException();

        if(cachedIndex == -1){
            getFirstSetBitInFirstBucket();
        }

        int y = PlatformDependent.getInt(first + KEY_Y_OFFSET);

        int dy = (cachedIndex >> BUCKET_AXIS_BITS) & BUCKET_AXIS_MASK;
        return (y << BUCKET_AXIS_BITS) + dy;
    }
    public int getFirstZ(){
        if(size == 0)
            throw new NoSuchElementException();

        if(cachedIndex == -1){
            getFirstSetBitInFirstBucket();
        }

        int z = PlatformDependent.getInt(first + KEY_Z_OFFSET);

        int dz = cachedIndex & BUCKET_AXIS_MASK;
        return (z << BUCKET_AXIS_BITS) + dz;
    }

    public void removeFirstValue(){
        if(size == 0)
            throw new NoSuchElementException();

        if(cachedIndex == -1){
            getFirstSetBitInFirstBucket();
        }

        long value = PlatformDependent.getLong(first + BUCKET_VALUE_OFFSET);

        value ^= 1L << cachedIndex;

        this.size--;

        if(value == 0){
            long pos = (this.first - tableAddr) / BUCKET_BYTES;
            removeBucket(this.first);
            this.shiftBuckets(tableAddr, pos, tableSize - 1L);

            cachedIndex = -1;
        }else{
            PlatformDependent.putLong(first + BUCKET_VALUE_OFFSET, value);
            getFirstSetBitInFirstBucket(cachedIndex);
        }
    }

    protected void getFirstSetBitInFirstBucket(){
        getFirstSetBitInFirstBucket(0);
    }

    protected void getFirstSetBitInFirstBucket(int start) {
        long value = PlatformDependent.getLong(first + BUCKET_VALUE_OFFSET);

        cachedIndex = Long.numberOfTrailingZeros(value);
    }

    protected void setTableSize(long tableSize) {
        this.tableSize = tableSize;
        this.resizeThreshold = (tableSize >> 1L) + (tableSize >> 2L); //count * 0.75
    }

    /**
     * @return the number of values stored in this set
     */
    public long size() {
        return this.size;
    }

    /**
     * @return whether or not this set is empty (contains no values)
     */
    public boolean isEmpty() {
        return this.size == 0L;
    }

    /**
     * Irrevocably releases the resources claimed by this instance.
     * <p>
     * Once this method has been calls, all methods in this class will produce undefined behavior.
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
    protected void finalize() throws Throwable {
        //using a finalizer is bad, i know. however, there's no other reasonable way for me to clean up the memory without pulling in PorkLib:unsafe or
        // using sun.misc.Cleaner directly...
        this.close();
    }

    //These methods probably won't be used by any CC code but should help ensure some compatibility if other mods access the light engine

    public boolean add(long l){
        return add(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l));
    }

    public boolean contains(long l){
        return contains(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l));
    }

    public boolean remove(long l){
        return remove(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l));
    }

    public long removeFirstLong(){
        int x = getFirstX();
        int y = getFirstY();
        int z = getFirstZ();

        removeFirstValue();
        return BlockPos.asLong(x, y, z);
    }

    //Should only be used during tests

    public XYZTriple[] toArray(){
        XYZTriple[] arr = new XYZTriple[(int) size];

        MutableInt i = new MutableInt(0);
        forEach((x, y, z) -> {
            arr[i.getAndIncrement()] = new XYZTriple(x, y, z);
        });

        if(i.getValue() != size){
            throw new IllegalStateException("Size mismatch");
        }

        return arr;
    }
    public static record XYZTriple(int x, int y, int z){}
}
