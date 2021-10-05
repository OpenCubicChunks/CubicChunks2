package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.bytecodegen;

import org.objectweb.asm.tree.AbstractInsnNode;

public interface InstructionFactory {
    AbstractInsnNode create();
}
