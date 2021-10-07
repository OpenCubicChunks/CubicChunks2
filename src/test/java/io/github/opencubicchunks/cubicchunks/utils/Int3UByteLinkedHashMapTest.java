package io.github.opencubicchunks.cubicchunks.utils;

import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Vec3i;
import org.junit.Test;

public class Int3UByteLinkedHashMapTest {
    @Test
    public void test1000BigCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.test(1000, ThreadLocalRandom::nextInt));
    }

    @Test
    public void test1000000BigCoordinates() {
        this.test(1000000, ThreadLocalRandom::nextInt);
    }

    @Test
    public void test1000SmallCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.test(1000, r -> r.nextInt() & 1023));
    }

    @Test
    public void test1000000SmallCoordinates() {
        this.test(1000000, r -> r.nextInt() & 1023);
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

        class Tester implements BiConsumer<Vec3i, Integer>, Int3UByteLinkedHashMap.EntryConsumer, Runnable {
            int countReference;
            int countTest;

            @Override public void accept(Vec3i k, Integer value) {
                this.countReference++;

                checkState(test.containsKey(k.getX(), k.getY(), k.getZ()));
                checkState(test.get(k.getX(), k.getY(), k.getZ()) == value);
            }

            @Override public void accept(int x, int y, int z, int value) {
                this.countTest++;

                checkState(reference.containsKey(new Vec3i(x, y, z)));
                checkState(reference.getInt(new Vec3i(x, y, z)) == value);
            }

            @Override
            public void run() {
                reference.forEach(this);
                test.forEach(this);

                checkState(this.countReference == this.countTest);
            }
        }

        new Tester().run();
    }

    @Test
    public void testDuplicateInsertionBigCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.testDuplicateInsertion(ThreadLocalRandom::nextInt));
    }

    @Test
    public void testDuplicateInsertionSmallCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.testDuplicateInsertion(r -> r.nextInt() & 1023));
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
        IntStream.range(0, 1024).parallel().forEach(i -> this.testDuplicateRemoval(ThreadLocalRandom::nextInt));
    }

    @Test
    public void testDuplicateRemovalSmallCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.testDuplicateRemoval(r -> r.nextInt() & 1023));
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

    @Test
    public void testPollBigCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.testPoll(ThreadLocalRandom::nextInt));
    }

    @Test
    public void testPollSmallCoordinates() {
        IntStream.range(0, 1024).parallel().forEach(i -> this.testPoll(r -> r.nextInt() & 1023));
    }

    protected void testPoll(ToIntFunction<ThreadLocalRandom> rng) {
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

            {
                Int3UByteLinkedHashMap.EntryConsumer callback = (x, y, z, value) -> {
                    checkState(!test.containsKey(x, y, z));
                    checkState(reference.containsKey(new Vec3i(x, y, z)));
                    checkState(reference.getInt(new Vec3i(x, y, z)) == value);

                    checkState(reference.removeInt(new Vec3i(x, y, z)) == value);

                    if (r.nextBoolean()) { //low chance of inserting a new entry
                        int nx = rng.applyAsInt(r);
                        int ny = rng.applyAsInt(r);
                        int nz = rng.applyAsInt(r);
                        int nvalue = r.nextInt() & 0xFF;

                        int v0 = reference.put(new Vec3i(nx, ny, nz), nvalue);
                        int v1 = test.put(nx, ny, nz, nvalue);
                        checkState(v0 == v1);
                    }
                };

                while (test.poll(callback)) {
                }
            }

            this.ensureEqual(Object2IntMaps.emptyMap(), test);
        }
    }
}
