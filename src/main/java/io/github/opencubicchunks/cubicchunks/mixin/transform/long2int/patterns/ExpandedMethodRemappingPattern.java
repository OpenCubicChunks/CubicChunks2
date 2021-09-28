package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LongPosTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

public class ExpandedMethodRemappingPattern extends BytecodePackedUsePattern {
    protected ExpandedMethodRemappingPattern(Map<String, LongPosTransformer.MethodRemappingInfo> transformedMethods) {
        super(transformedMethods);
    }

    @Override
    public boolean matches(InsnList instructions, LocalVariableMapper mapper, int index) {
        if(instructions.get(index).getOpcode() == Opcodes.LLOAD){
            return mapper.isARemappedTransformedLong(((VarInsnNode) instructions.get(index)).var);
        }
        return false;
    }

    @Override
    public int patternLength(InsnList instructions, LocalVariableMapper mapper, int index) {
        return 1;
    }

    @Override
    public InsnList forX(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList xCode = new InsnList();
        xCode.add(new VarInsnNode(Opcodes.ILOAD, getLongIndex(instructions, index)));

        return xCode;
    }

    @Override
    public InsnList forY(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList yCode = new InsnList();
        yCode.add(new VarInsnNode(Opcodes.ILOAD, getLongIndex(instructions, index) + 1));

        return yCode;
    }

    @Override
    public InsnList forZ(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList zCode = new InsnList();
        zCode.add(new VarInsnNode(Opcodes.ILOAD, getLongIndex(instructions, index) + 2));

        return zCode;
    }

    private int getLongIndex(InsnList instructions, int index){
        return ((VarInsnNode) instructions.get(index)).var;
    }
}
