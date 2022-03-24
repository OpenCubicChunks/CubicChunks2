package io.github.opencubicchunks.cubicchunks.testutils;

import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeNode.WIDTH_BLOCKS;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.BiConsumer;

public class Utils {
    public static void forEachBlockColumn(BiConsumer<Integer, Integer> xzConsumer) {
        for (int x = 0; x < WIDTH_BLOCKS; x++) {
            for (int z = 0; z < WIDTH_BLOCKS; z++) {
                xzConsumer.accept(x, z);
            }
        }
    }

    public static void shouldFail(Runnable r, String onFailureMessage) {
        boolean didFail = false;
        try {
            r.run();
            didFail = true;
        } catch (Throwable t) {
            //success
        }
        if (didFail) {
            fail(onFailureMessage);
        }
    }
    public static void shouldSucceed(Runnable r, String onFailureMessage) {
        try {
            r.run();
            //success
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            fail(onFailureMessage);
        }
    }
}
