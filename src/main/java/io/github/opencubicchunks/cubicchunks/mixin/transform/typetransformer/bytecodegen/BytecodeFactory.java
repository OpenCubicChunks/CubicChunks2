package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;

/**
 * An interface which generates a bytecode instruction list.
 */
public interface BytecodeFactory {
    /**
     * Generates a bytecode instruction list.
     * @param variableAllocator A function which, when given a type, returns an appropriate variable slot for that type.
     * @return A bytecode instruction list.
     */
    InsnList generate(Function<Type, Integer> variableAllocator);
}
