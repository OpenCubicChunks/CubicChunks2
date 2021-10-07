package io.github.opencubicchunks.cubicchunks.utils;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LinkedInt3HashSetTest {
    @Test
    public void test1(){
        LinkedInt3HashSet set = new LinkedInt3HashSet();

        set.add(5, 8, -4);
        set.add(9, 8, 7);
        set.add(0, 10, 11);

        assertEquals(set.getFirstX(), 5);
        assertEquals(set.getFirstY(), 8);
        assertEquals(set.getFirstZ(), -4);

        assertEquals(set.size(), 3);

        assertEquals(BlockPos.asLong(5, 8, -4), set.removeFirstLong());
        assertEquals(set.size(), 2);

        assertEquals(BlockPos.asLong(9, 8, 7), set.removeFirstLong());
        assertEquals(set.size(), 1);

        assertEquals(BlockPos.asLong(0, 10, 11), set.removeFirstLong());
        assertEquals(set.size(), 0);

        set.add(0, 0, 0);
        set.add(0, 0, 1);
        set.add(0, 2, 3);
        set.add(3, 1, 0);

        assertArrayEquals(new LinkedInt3HashSet.XYZTriple[]{
            new LinkedInt3HashSet.XYZTriple(0, 0, 0),
            new LinkedInt3HashSet.XYZTriple(0, 0, 1),
            new LinkedInt3HashSet.XYZTriple(0, 2, 3),
            new LinkedInt3HashSet.XYZTriple(3, 1, 0)
        }, set.toArray());

        set.remove(0, 2, 3);

        assertEquals(BlockPos.asLong(0, 0, 0), set.removeFirstLong());
        assertEquals(BlockPos.asLong(0, 0, 1), set.removeFirstLong());
        assertEquals(BlockPos.asLong(3, 1, 0), set.removeFirstLong());
    }
}
