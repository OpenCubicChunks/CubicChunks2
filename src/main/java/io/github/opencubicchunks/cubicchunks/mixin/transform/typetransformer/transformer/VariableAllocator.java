package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.objectweb.asm.Type;

/**
 * This class allows for the creation of new local variables for a method. This class is by no means made to be efficient but it works
 */
public class VariableAllocator {
    private final int baseline; //The maxLocals of the original method. No variable will be allocated in this range
    private final int maxLength; //The length of the instructions
    private final List<boolean[]> variables = new ArrayList<>(); //Stores which slots are used for each frame

    /**
     * Creates a new VariableManager with the given maxLocals and instruction length
     *
     * @param maxLocals The maxLocals of the method
     * @param maxLength The length of the instructions
     */
    public VariableAllocator(int maxLocals, int maxLength) {
        this.baseline = maxLocals;
        this.maxLength = maxLength;
    }

    /**
     * Allocates n consecutive slots
     *
     * @param from The index of the first place this variable will be used
     * @param to The index of the last place this variable will be used
     * @param n The number of consecutive slots to allocate
     */
    public int allocate(int from, int to, int n) {
        int level = 0;
        while (true) {
            while (level + n - 1 >= variables.size()) {
                variables.add(new boolean[maxLength]);
            }

            boolean[][] vars = new boolean[n][];
            for (int i = 0; i < n; i++) {
                vars[i] = variables.get(level + i);
            }

            //Check that all of it is free
            boolean free = true;
            out:
            for (int i = from; i < to; i++) {
                for (boolean[] var : vars) {
                    if (var[i]) {
                        free = false;
                        break out;
                    }
                }
            }

            if (free) {
                //Mark it as used
                for (int i = from; i < to; i++) {
                    for (boolean[] var : vars) {
                        var[i] = true;
                    }
                }

                return level + baseline;
            }

            level++;
        }
    }

    /**
     * Allocates a variable
     *
     * @param minIndex The minimum index of the variable
     * @param maxIndex The maximum index of the variable
     * @param type The type of the variable
     *
     * @return The index of the variable
     */
    public int allocate(int minIndex, int maxIndex, Type type) {
        return this.allocate(minIndex, maxIndex, type.getSize());
    }

    /**
     * Allocates a variable which takes up a single slot
     *
     * @param from The index of the first place this variable will be used
     * @param to The index of the last place this variable will be used
     *
     * @return The index of the variable
     */
    public int allocateSingle(int from, int to) {
        return this.allocate(from, to, 1);
    }

    public static Function<Type, Integer> makeBasicAllocator(int baseline) {
        return new Function<>() {
            int curr = baseline;

            @Override
            public Integer apply(Type type) {
                int index = curr;
                curr += type.getSize();
                return index;
            }
        };
    }
}
