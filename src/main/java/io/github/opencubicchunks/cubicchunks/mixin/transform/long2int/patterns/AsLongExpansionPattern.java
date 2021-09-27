package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class AsLongExpansionPattern extends BytecodePackedUsePattern{
    protected AsLongExpansionPattern(Map<String, String> transformedMethods) {
        super(transformedMethods);
    }

    @Override
    protected boolean matches(InsnList instructions, LocalVariableMapper mapper, int index) {
        if(index + 1 >= instructions.size()) return false;

        AbstractInsnNode first = instructions.get(index);
        AbstractInsnNode second = instructions.get(index + 1);

        if(first.getOpcode() != Opcodes.ALOAD) return false;
        if(second.getOpcode() != Opcodes.INVOKEVIRTUAL) return false;

        MethodInsnNode methodCall = (MethodInsnNode) second;
        return methodCall.owner.equals("net/minecraft/util/math/BlockPos") && methodCall.name.equals("asLong");
    }

    @Override
    protected int patternLength(InsnList instructions, LocalVariableMapper mapper, int index) {
        return 2;
    }

    @Override
    protected InsnList forX(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) instructions.get(index)).var));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/util/math/BlockPos", "getX", "()I"));

        return code;
    }

    @Override
    protected InsnList forY(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) instructions.get(index)).var));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/util/math/BlockPos", "getY", "()I"));

        return code;
    }

    @Override
    protected InsnList forZ(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) instructions.get(index)).var));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/util/math/BlockPos", "getZ", "()I"));

        return code;
    }
}
