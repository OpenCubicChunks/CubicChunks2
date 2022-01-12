package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;

public interface BytecodeFactory {
    InsnList generate(Function<Type, Integer> variableAllocator);
}
