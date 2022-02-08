package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

/**
 * A factory that creates a single instruction. Can be used as a {@link BytecodeFactory}.
 */
public interface InstructionFactory extends BytecodeFactory {
    AbstractInsnNode create();

    default InsnList generate(Function<Type, Integer> variableAllocator) {
        InsnList list = new InsnList();
        list.add(create());
        return list;
    }
}
