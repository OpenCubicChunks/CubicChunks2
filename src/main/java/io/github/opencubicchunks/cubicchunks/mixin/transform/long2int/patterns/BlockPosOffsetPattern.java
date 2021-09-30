package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns;

import java.util.Map;

import com.google.gson.JsonObject;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LocalVariableMapper;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.LongPosTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.MethodInfo;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class BlockPosOffsetPattern extends BytecodePackedUsePattern {
    public static String DIRECTION_CLASS_NAME, BLOCK_POS_CLASS_NAME;
    public static String GET_X_NAME, GET_Y_NAME, GET_Z_NAME;
    public static String TARGET_METHOD, TARGET_DESCRIPTOR;
    public static String BLOCK_POS_INTERMEDIARY;

    @Override
    public boolean matches(InsnList instructions, LocalVariableMapper mapper, int index) {
        if(instructions.size() <= index + 2) return false;

        if(instructions.get(index).getOpcode() == Opcodes.LLOAD){
            if(!mapper.isARemappedTransformedLong(((VarInsnNode) instructions.get(index)).var)){
                return false;
            }
        }else{
            return false;
        }

        if(!(instructions.get(index + 1).getOpcode() == Opcodes.ALOAD)){
            return false;
        }

        if(instructions.get(index + 2) instanceof MethodInsnNode methodCall){
            return methodCall.owner.equals(BLOCK_POS_CLASS_NAME) && methodCall.name.equals(TARGET_METHOD) && methodCall.desc.equals(TARGET_DESCRIPTOR);
        }

        return false;
    }

    @Override
    public int patternLength(InsnList instructions, LocalVariableMapper mapper, int index) {
        return 3;
    }

    @Override
    public InsnList forX(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList xCode = new InsnList();
        xCode.add(new VarInsnNode(Opcodes.ILOAD, getLongIndex(instructions, index)));
        xCode.add(new VarInsnNode(Opcodes.ALOAD, getDirectionIndex(instructions, index)));
        xCode.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DIRECTION_CLASS_NAME, GET_X_NAME, "()I"));
        xCode.add(new InsnNode(Opcodes.IADD));

        return xCode;
    }

    @Override
    public InsnList forY(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList yCode = new InsnList();
        yCode.add(new VarInsnNode(Opcodes.ILOAD, getLongIndex(instructions, index) + 1));
        yCode.add(new VarInsnNode(Opcodes.ALOAD, getDirectionIndex(instructions, index)));
        yCode.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DIRECTION_CLASS_NAME, GET_Y_NAME, "()I"));
        yCode.add(new InsnNode(Opcodes.IADD));

        return yCode;
    }

    @Override
    public InsnList forZ(InsnList instructions, LocalVariableMapper mapper, int index) {
        InsnList zCode = new InsnList();
        zCode.add(new VarInsnNode(Opcodes.ILOAD, getLongIndex(instructions, index) + 2));
        zCode.add(new VarInsnNode(Opcodes.ALOAD, getDirectionIndex(instructions, index)));
        zCode.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DIRECTION_CLASS_NAME, GET_Z_NAME, "()I"));
        zCode.add(new InsnNode(Opcodes.IADD));

        return zCode;
    }

    private int getLongIndex(InsnList instructions, int index){
        return ((VarInsnNode) instructions.get(index)).var;
    }

    private int getDirectionIndex(InsnList instruction, int index){
        return ((VarInsnNode) instruction.get(index + 1)).var;
    }

    public static void readConfig(JsonObject config, MappingResolver map){
        String directionIntermediary = config.get("direction").getAsString().replace('/', '.');
        BLOCK_POS_INTERMEDIARY = config.get("block_pos").getAsString().replace('/', '.');

        BLOCK_POS_CLASS_NAME = map.mapClassName("intermediary", BLOCK_POS_INTERMEDIARY).replace('.', '/');
        DIRECTION_CLASS_NAME = map.mapClassName("intermediary", directionIntermediary).replace('.', '/');

        String methodDescIntermediary = config.get("method_desc").getAsString();
        TARGET_METHOD = map.mapMethodName("intermediary", BLOCK_POS_INTERMEDIARY, config.get("method_name").getAsString(), methodDescIntermediary);
        TARGET_DESCRIPTOR = MethodInfo.mapDescriptor(methodDescIntermediary, map);

        GET_X_NAME = map.mapMethodName("intermediary", directionIntermediary, config.get("step_x").getAsString(), "()I");
        GET_Y_NAME = map.mapMethodName("intermediary", directionIntermediary, config.get("step_y").getAsString(), "()I");
        GET_Z_NAME = map.mapMethodName("intermediary", directionIntermediary, config.get("step_z").getAsString(), "()I");
    }
}
