package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import org.objectweb.asm.tree.InsnList;

public interface BytecodeFactory {
    InsnList generate();
}
