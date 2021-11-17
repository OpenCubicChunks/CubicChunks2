package io.github.opencubicchunks.cubicchunks.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.Vec3i;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Int3ListTest {
    @Test
    public void randomTest(){
        Random random = new Random();

        long seed = random.nextLong();
        System.out.println("Seed: " + seed);
        random.setSeed(seed);

        try(Int3List list = new Int3List()) {
            List<Vec3i> tester = new ArrayList<>();

            for(int i = 0; i < 100000; i++){
                int x, y, z, index;
                switch (random.nextInt(4)){
                    case 0:
                        x = random.nextInt(50);
                        y = random.nextInt(50);
                        z = random.nextInt(50);
                        list.add(x, y, z);
                        tester.add(new Vec3i(x, y, z));
                        break;
                    case 1:
                        if(list.size() == 0) break;
                        index = random.nextInt(list.size());
                        list.remove(index);
                        tester.remove(index);
                        break;
                    case 2:
                        if(list.size() == 0) break;
                        index = random.nextInt(list.size());
                        x = random.nextInt(50);
                        y = random.nextInt(50);
                        z = random.nextInt(50);
                        list.set(index, x, y, z);
                        tester.set(index, new Vec3i(x, y, z));
                        break;
                    case 3:
                        index = random.nextInt(list.size() + 1);
                        x = random.nextInt(50);
                        y = random.nextInt(50);
                        z = random.nextInt(50);
                        list.insert(index, x, y, z);
                        tester.add(index, new Vec3i(x, y, z));
                }

                if(random.nextInt(10000) == 0){
                    list.clear();
                    tester.clear();
                }

                assertEqualList(list, tester);
            }
        }
    }

    private void assertEqualList(Int3List list, List<Vec3i> tester) {
        assertEquals("Different Sizes", list.size(), tester.size());

        for(int i = 0; i < list.size(); i++){
            Vec3i vec = tester.get(i);

            assertEquals(vec.getX(), list.getX(i));
            assertEquals(vec.getY(), list.getY(i));
            assertEquals(vec.getZ(), list.getZ(i));
        }
    }
}
