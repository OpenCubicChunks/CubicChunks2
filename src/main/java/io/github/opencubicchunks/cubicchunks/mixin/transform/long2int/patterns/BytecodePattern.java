package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import org.objectweb.asm.tree.InsnList;

public interface BytecodePattern {
    boolean apply(InsnList instructions, LocalVariableMapper variableMapper, int index);
}
