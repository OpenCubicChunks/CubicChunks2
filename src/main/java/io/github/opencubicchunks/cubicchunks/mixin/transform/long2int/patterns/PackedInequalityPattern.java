package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class PackedInequalityPattern implements BytecodePattern {
    @Override
    public boolean apply(InsnList instructions, LocalVariableMapper variableMapper, int index) {
        if(index + 3 >= instructions.size()) return false;

        AbstractInsnNode first = instructions.get(index);
        AbstractInsnNode second = instructions.get(index + 1);
        AbstractInsnNode third = instructions.get(index + 2);
        AbstractInsnNode fourth = instructions.get(index + 3);

        if(!(first.getOpcode() == Opcodes.LLOAD && second.getOpcode() == Opcodes.LLOAD)) return false;

        if(!(third.getOpcode() == Opcodes.LCMP)) return false;

        if(!(fourth.getOpcode() == Opcodes.IFNE || fourth.getOpcode() == Opcodes.IFEQ)) return false;

        VarInsnNode varInstructionOne = (VarInsnNode) first;
        VarInsnNode varInstructionTwo = (VarInsnNode) second;

        JumpInsnNode jumpNode = (JumpInsnNode) fourth;

        if(!(variableMapper.isARemappedTransformedLong(varInstructionOne.var) && variableMapper.isARemappedTransformedLong(varInstructionTwo.var))) return false;

        int varOne = varInstructionOne.var;
        int varTwo = varInstructionTwo.var;
        InsnList newCode = new InsnList();

        int opcode = fourth.getOpcode() == Opcodes.IFNE ? Opcodes.IF_ICMPNE : Opcodes.IF_ICMPEQ;

        newCode.add(new VarInsnNode(Opcodes.ILOAD, varOne));
        newCode.add(new VarInsnNode(Opcodes.ILOAD, varTwo));
        newCode.add(new JumpInsnNode(opcode, jumpNode.label));

        newCode.add(new VarInsnNode(Opcodes.ILOAD, varOne + 1));
        newCode.add(new VarInsnNode(Opcodes.ILOAD, varTwo + 1));
        newCode.add(new JumpInsnNode(opcode, jumpNode.label));

        newCode.add(new VarInsnNode(Opcodes.ILOAD, varOne + 2));
        newCode.add(new VarInsnNode(Opcodes.ILOAD, varTwo + 2));
        newCode.add(new JumpInsnNode(opcode, jumpNode.label));

        instructions.insertBefore(first, newCode);

        instructions.remove(first);
        instructions.remove(second);
        instructions.remove(third);
        instructions.remove(fourth);

        return false;
    }
}
