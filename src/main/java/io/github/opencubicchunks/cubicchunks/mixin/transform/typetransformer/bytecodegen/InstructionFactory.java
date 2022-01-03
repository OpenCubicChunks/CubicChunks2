package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public interface InstructionFactory extends BytecodeFactory{
    AbstractInsnNode create();
    default InsnList generate() {
        InsnList list = new InsnList();
        list.add(create());
        return list;
    }
}
