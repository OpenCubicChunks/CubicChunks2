package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class CheckInvalidPosPattern implements BytecodePattern {
    @Override
    public boolean apply(InsnList instructions, LocalVariableMapper variableMapper, int index) {
        if(index + 3 >= instructions.size()) return false;

        AbstractInsnNode first = instructions.get(index);
        AbstractInsnNode second = instructions.get(index + 1);
        AbstractInsnNode third = instructions.get(index + 2);

        if(first.getOpcode() != Opcodes.LLOAD){
            AbstractInsnNode temp = first;
            first = second;
            second = temp;
        }

        if(first.getOpcode() != Opcodes.LLOAD) return false;

        int var = ((VarInsnNode) first).var;
        if(!variableMapper.isARemappedTransformedLong(var));

        if(!(second instanceof LdcInsnNode)) return false;

        Object checkObject = ((LdcInsnNode) second).cst;
        if(!(checkObject instanceof Long)) return false;
        if(((Long) checkObject) != Long.MAX_VALUE) return false;

        if(third.getOpcode() != Opcodes.LCMP) return false;

        AbstractInsnNode fourth = instructions.get(index + 3);
        if(!(fourth.getOpcode() == Opcodes.IFNE || fourth.getOpcode() == Opcodes.IFEQ)) return false;
        JumpInsnNode jump = (JumpInsnNode) fourth;
        boolean isEq = fourth.getOpcode() == Opcodes.IFEQ;
        int opcode = isEq ? Opcodes.IF_ICMPEQ : Opcodes.IF_ICMPNE;

        InsnList newInstructions = new InsnList();
        newInstructions.add(new LdcInsnNode(Integer.MAX_VALUE));
        newInstructions.add(new VarInsnNode(Opcodes.ILOAD, var));
        newInstructions.add(new JumpInsnNode(opcode, jump.label));

        newInstructions.add(new LdcInsnNode(Integer.MAX_VALUE));
        newInstructions.add(new VarInsnNode(Opcodes.ILOAD, var + 1));
        newInstructions.add(new JumpInsnNode(opcode, jump.label));

        newInstructions.add(new LdcInsnNode(Integer.MAX_VALUE));
        newInstructions.add(new VarInsnNode(Opcodes.ILOAD, var + 2));
        newInstructions.add(new JumpInsnNode(opcode, jump.label));

        instructions.insertBefore(first, newInstructions);
        instructions.remove(first);
        instructions.remove(second);
        instructions.remove(third);
        instructions.remove(fourth);

        return false;
    }
}
