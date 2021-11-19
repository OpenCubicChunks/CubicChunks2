package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

public class OpcodeUtil {
    public static boolean isLocalVarLoad(int opcode){
        return opcode == Opcodes.ILOAD ||
                opcode == Opcodes.LLOAD ||
                opcode == Opcodes.FLOAD ||
                opcode == Opcodes.DLOAD ||
                opcode == Opcodes.ALOAD;
    }

    public static boolean isLocalVarStore(int opcode){
        return opcode == Opcodes.ISTORE ||
                opcode == Opcodes.LSTORE ||
                opcode == Opcodes.FSTORE ||
                opcode == Opcodes.DSTORE ||
                opcode == Opcodes.ASTORE;
    }

    //Note that for the return and throw instruction it technically clears the stack
    //DUP2 and POP2 is not implemented because it's a pain in the ass. Any code with dup2* or pop2 of any kind will not work
    /**
     *
     * @param instruction
     * @return how much the height of the stack gets modified by this isntruction;
     */
    public static int getStackChange(AbstractInsnNode instruction){
        if(instruction instanceof MethodInsnNode methodCall){
            var callInfo = ParameterInfo.parseDescriptor(methodCall.desc);
            int numParams = callInfo.getFirst().size();
            if(instruction.getOpcode() != Opcodes.INVOKESTATIC){
                numParams++;
            }

            int numReturn = callInfo.getSecond() == ParameterInfo.VOID ? 0 : 1;

            return numReturn - numParams;
        }

        //I have no idea how this one works so this code is a bit of a guess
        if(instruction instanceof InvokeDynamicInsnNode dynamicMethodCall){
            var callInfo = ParameterInfo.parseDescriptor(dynamicMethodCall.desc);
            int numParams = callInfo.getFirst().size();
            int numReturn = callInfo.getSecond() == ParameterInfo.VOID ? 0 : 1;
            return numReturn - numParams;
        }

        if(instruction.getOpcode() == Opcodes.MULTIANEWARRAY){
            MultiANewArrayInsnNode newArray = (MultiANewArrayInsnNode) instruction;
            return 1 - newArray.dims;
        }

        return switch (instruction.getOpcode()){
            case Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.DASTORE, Opcodes.FASTORE, Opcodes.IASTORE,
                    Opcodes.LASTORE, Opcodes.SASTORE-> -3;
            case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
                    Opcodes.IF_ICMPLE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPNE, Opcodes.PUTFIELD -> -2;
            case Opcodes.AALOAD, Opcodes.ASTORE, Opcodes.CALOAD, Opcodes.BALOAD, Opcodes.DADD, Opcodes.DALOAD, Opcodes.DCMPG,
                    Opcodes.DCMPL, Opcodes.DDIV, Opcodes.DMUL, Opcodes.DREM, Opcodes.DSTORE, Opcodes.DSUB, Opcodes.FADD,
                    Opcodes.FALOAD, Opcodes.FCMPG, Opcodes.FCMPL, Opcodes.FDIV, Opcodes.FMUL, Opcodes.FREM, Opcodes.FSTORE,
                    Opcodes.FSUB, Opcodes.IADD, Opcodes.IALOAD, Opcodes.IAND, Opcodes.IDIV, Opcodes.IFEQ, Opcodes.IFNE,
                    Opcodes.IFLE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFNONNULL, Opcodes.IFNULL, Opcodes.IMUL,
                    Opcodes.IOR, Opcodes.IREM, Opcodes.ISHL, Opcodes.ISHR, Opcodes.ISTORE, Opcodes.ISUB, Opcodes.IUSHR, Opcodes.IXOR,
                    Opcodes.LADD, Opcodes.LALOAD, Opcodes.LAND, Opcodes.LCMP, Opcodes.LDIV, Opcodes.LMUL, Opcodes.LOOKUPSWITCH,
                    Opcodes.LOR, Opcodes.LREM, Opcodes.LSHL, Opcodes.LSHR, Opcodes.LSTORE, Opcodes.LSUB, Opcodes.LUSHR, Opcodes.LXOR,
                    Opcodes.MONITORENTER, Opcodes.MONITOREXIT, Opcodes.POP, Opcodes.PUTSTATIC, Opcodes.SALOAD, Opcodes.TABLESWITCH-> -1;
            case Opcodes.ANEWARRAY, Opcodes.ARETURN, Opcodes.ARRAYLENGTH, Opcodes.ATHROW, Opcodes.CHECKCAST, Opcodes.D2F,
                    Opcodes.D2I, Opcodes.D2L, Opcodes.DNEG, Opcodes.DRETURN, Opcodes.F2D, Opcodes.F2I, Opcodes.F2L,
                    Opcodes.FNEG, Opcodes.FRETURN, Opcodes.GETFIELD, Opcodes.GOTO, Opcodes.I2B, Opcodes.I2C,
                    Opcodes.I2D, Opcodes.I2F, Opcodes.I2L, Opcodes.I2S, Opcodes.IINC, Opcodes.INEG, Opcodes.INSTANCEOF,
                    Opcodes.IRETURN, Opcodes.L2D, Opcodes.L2F, Opcodes.L2I, Opcodes.LNEG, Opcodes.LRETURN, Opcodes.NEWARRAY,
                    Opcodes.NOP, Opcodes.RET, Opcodes.RETURN, Opcodes.SWAP-> 0;
            case Opcodes.ACONST_NULL, Opcodes.ALOAD, Opcodes.BIPUSH, Opcodes.DCONST_0, Opcodes.DCONST_1, Opcodes.DLOAD,
                    Opcodes.DUP, Opcodes.DUP_X1, Opcodes.DUP_X2, Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2,
                    Opcodes.FLOAD, Opcodes.GETSTATIC, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                    Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.ICONST_M1, Opcodes.ILOAD, Opcodes.JSR, Opcodes.LCONST_0,
                    Opcodes.LCONST_1, Opcodes.LDC, Opcodes.LLOAD, Opcodes.NEW, Opcodes.SIPUSH -> 1;
            default -> {
                System.out.println("Don't know stack change for " + instruction.getOpcode() + ". Returning 0");
                yield 0;
            }
        };
    }

    //Screw DUP and SWAPS
    public static int getConsumedOperands(AbstractInsnNode instruction){
        if(instruction instanceof MethodInsnNode methodCall){
            var callInfo = ParameterInfo.parseDescriptor(methodCall.desc);
            int numParams = callInfo.getFirst().size();
            if(instruction.getOpcode() != Opcodes.INVOKESTATIC) {
                numParams++;
            }

            return numParams;
        }

        //I have no idea how this one works so this code is a bit of a guess
        if(instruction instanceof InvokeDynamicInsnNode dynamicMethodCall){
            var callInfo = ParameterInfo.parseDescriptor(dynamicMethodCall.desc);
            return callInfo.getFirst().size();
        }

        if(instruction.getOpcode() == Opcodes.MULTIANEWARRAY){
            MultiANewArrayInsnNode newArray = (MultiANewArrayInsnNode) instruction;
            return newArray.dims;
        }

        return switch (instruction.getOpcode()){
            case Opcodes.ACONST_NULL, Opcodes.ALOAD, Opcodes.ATHROW, Opcodes.BIPUSH, Opcodes.CHECKCAST, Opcodes.D2F,
                    Opcodes.D2I, Opcodes.D2L, Opcodes.DCONST_0, Opcodes.DCONST_1, Opcodes.DLOAD, Opcodes.FCONST_0,
                    Opcodes.FCONST_1, Opcodes.FCONST_2, Opcodes.FLOAD, Opcodes.GETSTATIC, Opcodes.GOTO, Opcodes.ICONST_0,
                    Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5, Opcodes.ICONST_M1,
                    Opcodes.IINC, Opcodes.ILOAD, Opcodes.JSR, Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.LDC, Opcodes.LLOAD,
                    Opcodes.NEW, Opcodes.NOP, Opcodes.POP, Opcodes.RET, Opcodes.RETURN, Opcodes.SIPUSH -> 0;
            case Opcodes.ANEWARRAY, Opcodes.ARETURN, Opcodes.ARRAYLENGTH, Opcodes.ASTORE, Opcodes.DNEG, Opcodes.DRETURN, Opcodes.DSTORE,
                    Opcodes.F2D, Opcodes.F2I, Opcodes.F2L, Opcodes.FNEG, Opcodes.FRETURN, Opcodes.FSTORE, Opcodes.GETFIELD, Opcodes.I2B,
                    Opcodes.I2C, Opcodes.I2D, Opcodes.I2F, Opcodes.I2L, Opcodes.I2S, Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLE, Opcodes.IFLT,
                    Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFNONNULL, Opcodes.IFNULL, Opcodes.INEG, Opcodes.INSTANCEOF, Opcodes.IRETURN, Opcodes.ISTORE,
                    Opcodes.L2D, Opcodes.L2F, Opcodes.L2I, Opcodes.LNEG, Opcodes.LOOKUPSWITCH, Opcodes.LRETURN, Opcodes.LSTORE, Opcodes.MONITORENTER,
                    Opcodes.MONITOREXIT, Opcodes.NEWARRAY, Opcodes.PUTSTATIC, Opcodes.TABLESWITCH -> 1;
            case Opcodes.AALOAD, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.DADD, Opcodes.DALOAD, Opcodes.DCMPG, Opcodes.DCMPL,
                    Opcodes.DDIV, Opcodes.DMUL, Opcodes.DREM, Opcodes.DSUB, Opcodes.FADD, Opcodes.FALOAD, Opcodes.FCMPG, Opcodes.FCMPL,
                    Opcodes.FDIV, Opcodes.FMUL, Opcodes.FREM, Opcodes.FSUB, Opcodes.IADD, Opcodes.IALOAD, Opcodes.IAND, Opcodes.IDIV,
                    Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE,
                    Opcodes.IF_ICMPLT, Opcodes.IF_ICMPNE, Opcodes.IMUL, Opcodes.IOR, Opcodes.IREM, Opcodes.ISHL, Opcodes.ISHR, Opcodes.ISUB,
                    Opcodes.IUSHR, Opcodes.IXOR, Opcodes.LADD, Opcodes.LALOAD, Opcodes.LAND, Opcodes.LCMP, Opcodes.LDIV, Opcodes.LMUL, Opcodes.LOR,
                    Opcodes.LREM, Opcodes.LSHL, Opcodes.LSHR, Opcodes.LSUB, Opcodes.LUSHR, Opcodes.LXOR, Opcodes.PUTFIELD, Opcodes.SALOAD-> 2;
            case Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.DASTORE, Opcodes.FASTORE, Opcodes.IASTORE, Opcodes.LASTORE,
                    Opcodes.SASTORE-> 3;
            default -> {
                System.out.println("Don't know stack consumption for " + instruction.getOpcode() + ". Returning 0");
                yield 0;
            }
        };
    }

    public static boolean isArithmeticOperation(int opcode){
        //Currently, only for int because there is no reason to have anything else
        return switch (opcode){
            case Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM, Opcodes.ISHL, Opcodes.ISHR, Opcodes.IUSHR, Opcodes.IAND, Opcodes.IOR, Opcodes.IXOR -> true;
            default -> false;
        };
    }
}
