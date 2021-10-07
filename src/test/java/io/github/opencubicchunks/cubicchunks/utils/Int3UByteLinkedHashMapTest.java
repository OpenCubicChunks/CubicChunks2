package io.github.opencubicchunks.cubicchunks.utils;

import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Vec3i;
import org.junit.Test;

public class Int3UByteLinkedHashMapTest {
    @Test
    public void test1000BigCoordinates() {
        this.test(1000, ThreadLocalRandom::nextInt);
    }

    @Test
    public void test1000000BigCoordinates() {
        this.test(1000000, ThreadLocalRandom::nextInt);
    }

    @Test
    public void test1000SmallCoordinates() {
        this.test(1000, r -> r.nextInt(1000));
    }

    @Test
    public void test1000000SmallCoordinates() {
        this.test(1000000, r -> r.nextInt(1000));
    }

    protected void test(int nPoints, ToIntFunction<ThreadLocalRandom> rng) {
        Object2IntMap<Vec3i> reference = new Object2IntOpenHashMap<>(nPoints);
        reference.defaultReturnValue(-1);

        ThreadLocalRandom r = ThreadLocalRandom.current();

        try (Int3UByteLinkedHashMap test = new Int3UByteLinkedHashMap()) {
            for (int i = 0; i < nPoints; i++) { //insert some random values
                int x = rng.applyAsInt(r);
                int y = rng.applyAsInt(r);
                int z = rng.applyAsInt(r);
                int value = r.nextInt() & 0xFF;

                int v0 = reference.put(new Vec3i(x, y, z), value);
                int v1 = test.put(x, y, z, value);
                checkState(v0 == v1);
            }

            this.ensureEqual(reference, test);

            for (Iterator<Object2IntMap.Entry<Vec3i>> itr = reference.object2IntEntrySet().iterator(); itr.hasNext(); ) { //remove some positions at random
                Object2IntMap.Entry<Vec3i> entry = itr.next();
                Vec3i pos = entry.getKey();
                int value = entry.getIntValue();

                if ((r.nextInt() & 3) == 0) {
                    itr.remove();

                    int removed = test.remove(pos.getX(), pos.getY(), pos.getZ());
                    checkState(value == removed);
                }
            }

            this.ensureEqual(reference, test);
        }
    }

    protected void ensureEqual(Object2IntMap<Vec3i> reference, Int3UByteLinkedHashMap test) {
        checkState(reference.size() == test.size());

        reference.forEach((k, v) -> {
            checkState(test.containsKey(k.getX(), k.getY(), k.getZ()));
            checkState(test.get(k.getX(), k.getY(), k.getZ()) == v);
        });
        test.forEach((x, y, z, v) -> {
            checkState(reference.containsKey(new Vec3i(x, y, z)));
            checkState(reference.getInt(new Vec3i(x, y, z)) == v);
        });
    }

    @Test
    public void testDuplicateInsertionBigCoordinates() {
        this.testDuplicateInsertion(ThreadLocalRandom::nextInt);
    }

    @Test
    public void testDuplicateInsertionSmallCoordinates() {
        this.testDuplicateInsertion(r -> r.nextInt(1000));
    }

    protected void testDuplicateInsertion(ToIntFunction<ThreadLocalRandom> rng) {
        Object2IntMap<Vec3i> reference = new Object2IntOpenHashMap<>();
        reference.defaultReturnValue(-1);

        ThreadLocalRandom r = ThreadLocalRandom.current();

        try (Int3UByteLinkedHashMap test = new Int3UByteLinkedHashMap()) {
            this.ensureEqual(reference, test);

            for (int i = 0; i < 10000; i++) {
                int x = rng.applyAsInt(r);
                int y = rng.applyAsInt(r);
                int z = rng.applyAsInt(r);
                int value = r.nextInt() & 0xFF;

                if (reference.putIfAbsent(new Vec3i(x, y, z), value) >= 0) {
                    i--;
                    continue;
                }

                int v0 = test.put(x, y, z, value);
                int v1 = test.putIfAbsent(x, y, z, (value + 1) & 0xFF);
                int v2 = test.put(x, y, z, value);
                checkState(v0 == Int3UByteLinkedHashMap.DEFAULT_RETURN_VALUE && v1 == value && v2 == value);
            }

            this.ensureEqual(reference, test);
        }
    }

    @Test
    public void testDuplicateRemovalBigCoordinates() {
        this.testDuplicateRemoval(ThreadLocalRandom::nextInt);
    }

    @Test
    public void testDuplicateRemovalSmallCoordinates() {
        this.testDuplicateRemoval(r -> r.nextInt(1000));
    }

    protected void testDuplicateRemoval(ToIntFunction<ThreadLocalRandom> rng) {
        Object2IntMap<Vec3i> reference = new Object2IntOpenHashMap<>();
        reference.defaultReturnValue(-1);

        ThreadLocalRandom r = ThreadLocalRandom.current();

        try (Int3UByteLinkedHashMap test = new Int3UByteLinkedHashMap()) {
            this.ensureEqual(reference, test);

            for (int i = 0; i < 10000; i++) {
                int x = rng.applyAsInt(r);
                int y = rng.applyAsInt(r);
                int z = rng.applyAsInt(r);
                int value = r.nextInt() & 0xFF;

                int v0 = reference.put(new Vec3i(x, y, z), value);
                int v1 = test.put(x, y, z, value);
                checkState(v0 == v1);
            }

            this.ensureEqual(reference, test);

            reference.forEach((k, v) -> {
                int v0 = test.remove(k.getX(), k.getY(), k.getZ());
                int v1 = test.remove(k.getX(), k.getY(), k.getZ());
                checkState(v0 == v && v1 == Int3UByteLinkedHashMap.DEFAULT_RETURN_VALUE);
            });

            this.ensureEqual(Object2IntMaps.emptyMap(), test);
        }
    }
}
