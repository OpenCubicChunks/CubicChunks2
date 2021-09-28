package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class BlockPosUnpackingPattern implements BytecodePattern {
    private static final String OFFSET_0 = "net/minecraft/core/BlockPos#getX";
    private static final String OFFSET_1 = "net/minecraft/core/BlockPos#getY";
    private static final String OFFSET_2 = "net/minecraft/core/BlockPos#getZ";

    @Override
    public boolean apply(InsnList instructions, LocalVariableMapper variableMapper, int index) {
        if(index + 1 >= instructions.size()) return false;

        AbstractInsnNode first = instructions.get(index);
        AbstractInsnNode second = instructions.get(index + 1);

        if(first.getOpcode() != Opcodes.LLOAD) return false;
        VarInsnNode loadInstruction = (VarInsnNode) first;

        int j = 1;
        while(second instanceof LabelNode || second instanceof LineNumberNode){
            j++;
            if(index + j >= instructions.size()) return false;
            second = instructions.get(index + j);
        }

        if(second.getOpcode() != Opcodes.INVOKESTATIC) return false;
        MethodInsnNode methodCall = (MethodInsnNode) second;

        if(!variableMapper.isARemappedTransformedLong(loadInstruction.var)) return false;

        String methodName = methodCall.owner + "#" + methodCall.name;

        if(methodName.equals(OFFSET_0)){
            loadInstruction.setOpcode(Opcodes.ILOAD);
            instructions.remove(second);
        }else if(methodName.equals(OFFSET_1)){
            loadInstruction.setOpcode(Opcodes.ILOAD);
            loadInstruction.var++;
            instructions.remove(second);
        }else if(methodName.equals(OFFSET_2)){
            loadInstruction.setOpcode(Opcodes.ILOAD);
            loadInstruction.var += 2;
            instructions.remove(second);
        }

        return false;
    }
}
