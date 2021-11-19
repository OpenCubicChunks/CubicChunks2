package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.Interpreter;

public class LightEngineInterpreter extends Interpreter<LightEngineValue> {
    private final Map<String, MethodInfo> methodInfoMap;
    private List<Integer> localVarOverrides = new ArrayList<>();

    protected LightEngineInterpreter(Map<String, MethodInfo> methodInfo) {
        super(ASM9);
        this.methodInfoMap = methodInfo;
    }

    @Override
    public LightEngineValue newValue(Type type) {
        if(type == null){
            return new LightEngineValue(null);
        }
        if(type.getSort() == Type.VOID) return null;
        if(type.getSort() == Type.METHOD) throw new AssertionError();
        return new LightEngineValue(type);
    }

    @Override
    public LightEngineValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
        if(type == Type.VOID_TYPE) return null;
        LightEngineValue value = new LightEngineValue(type, local);
        if(localVarOverrides.contains(local)){
            value.setPackedLong();
        }
        return value;
    }

    @Override
    public LightEngineValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
        return switch (insn.getOpcode()){
            case Opcodes.ACONST_NULL -> new LightEngineValue(BasicInterpreter.NULL_TYPE, insn);
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                    Opcodes.ICONST_4, Opcodes.ICONST_5 -> new LightEngineValue(Type.INT_TYPE, insn);
            case Opcodes.LCONST_0, Opcodes.LCONST_1 -> new LightEngineValue(Type.LONG_TYPE, insn);
            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> new LightEngineValue(Type.FLOAT_TYPE, insn);
            case Opcodes.DCONST_0, Opcodes.DCONST_1 -> new LightEngineValue(Type.DOUBLE_TYPE, insn);
            case Opcodes.BIPUSH -> new LightEngineValue(Type.BYTE_TYPE, insn);
            case Opcodes.SIPUSH -> new LightEngineValue(Type.SHORT_TYPE, insn);
            case Opcodes.LDC -> {
                Object value = ((LdcInsnNode) insn).cst;
                if (value instanceof Integer) {
                    yield  new LightEngineValue(Type.INT_TYPE, insn);
                } else if (value instanceof Float) {
                    yield  new LightEngineValue(Type.FLOAT_TYPE, insn);
                } else if (value instanceof Long) {
                    yield  new LightEngineValue(Type.LONG_TYPE, insn);
                } else if (value instanceof Double) {
                    yield  new LightEngineValue(Type.DOUBLE_TYPE, insn);
                } else if (value instanceof String) {
                    yield  new LightEngineValue(Type.getObjectType("java/lang/String"), insn);
                } else if (value instanceof Type) {
                    int sort = ((Type) value).getSort();
                    if (sort == Type.OBJECT || sort == Type.ARRAY) {
                        yield new LightEngineValue(Type.getObjectType("java/lang/Class"), insn);
                    } else if (sort == Type.METHOD) {
                        yield new LightEngineValue(Type.getObjectType("java/lang/invoke/MethodType"), insn);
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                }
                throw new IllegalStateException("This shouldn't happen");
            }
            case Opcodes.JSR -> new LightEngineValue(Type.VOID_TYPE, insn);
            case Opcodes.GETSTATIC -> new LightEngineValue(Type.getType(((FieldInsnNode) insn).desc), insn);
            case Opcodes.NEW -> new LightEngineValue(Type.getObjectType(((TypeInsnNode) insn).desc), insn);
            default -> throw new IllegalStateException("Unexpected value: " + insn.getType());
        };
    }

    @Override
    public LightEngineValue copyOperation(AbstractInsnNode insn, LightEngineValue value) throws AnalyzerException {
        if(insn instanceof VarInsnNode varInsn){
            return new LightEngineValue(value.getType(), insn, varInsn.var, value.getPackedLongRef());
        }
        return value;
    }

    @Override
    public LightEngineValue unaryOperation(AbstractInsnNode insn, LightEngineValue value) throws AnalyzerException {
        consumeBy(value, insn);

        switch (insn.getOpcode()) {
            case INEG:
            case IINC:
            case L2I:
            case F2I:
            case D2I:
            case I2B:
            case I2C:
            case I2S:
                return new LightEngineValue(Type.INT_TYPE, insn);
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                return new LightEngineValue(Type.FLOAT_TYPE, insn);
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                return new LightEngineValue(Type.LONG_TYPE, insn);
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return new LightEngineValue(Type.DOUBLE_TYPE, insn);
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case PUTSTATIC:
                return null;
            case GETFIELD:
                return new LightEngineValue(Type.getType(((FieldInsnNode) insn).desc), insn);
            case NEWARRAY:
                switch (((IntInsnNode) insn).operand) {
                    case T_BOOLEAN:
                        return new LightEngineValue(Type.getType("[Z"), insn);
                    case T_CHAR:
                        return new LightEngineValue(Type.getType("[C"), insn);
                    case T_BYTE:
                        return new LightEngineValue(Type.getType("[B"), insn);
                    case T_SHORT:
                        return new LightEngineValue(Type.getType("[S"), insn);
                    case T_INT:
                        return new LightEngineValue(Type.getType("[I"), insn);
                    case T_FLOAT:
                        return new LightEngineValue(Type.getType("[F"), insn);
                    case T_DOUBLE:
                        return new LightEngineValue(Type.getType("[D"), insn);
                    case T_LONG:
                        return new LightEngineValue(Type.getType("[J"), insn);
                    default:
                        break;
                }
                throw new AnalyzerException(insn, "Invalid array type");
            case ANEWARRAY:
                return new LightEngineValue(Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc)), insn);
            case ARRAYLENGTH:
                return new LightEngineValue(Type.INT_TYPE, insn);
            case ATHROW:
                return null;
            case CHECKCAST:
                return new LightEngineValue(Type.getObjectType(((TypeInsnNode) insn).desc), insn);
            case INSTANCEOF:
                return new LightEngineValue(Type.INT_TYPE, insn);
            case MONITORENTER:
            case MONITOREXIT:
            case IFNULL:
            case IFNONNULL:
                return null;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public LightEngineValue binaryOperation(AbstractInsnNode insn, LightEngineValue value1, LightEngineValue value2) throws AnalyzerException {
        consumeBy(value1, insn);
        consumeBy(value2, insn);
        switch (insn.getOpcode()) {
            case IALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR:
                return new LightEngineValue(Type.INT_TYPE, insn);
            case FALOAD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return new LightEngineValue(Type.FLOAT_TYPE, insn);
            case LALOAD:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
                return new LightEngineValue(Type.LONG_TYPE, insn);
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                return new LightEngineValue(Type.DOUBLE_TYPE, insn);
            case AALOAD:
                return new LightEngineValue(Type.getObjectType("java/lang/Object"), insn);
            case LCMP:
                if(isMaxLong(value1)){
                    value1.setPackedLong();
                }

                if(isMaxLong(value2)){
                    value2.setPackedLong();
                }

                if(value1.isAPackedLong()){
                    value2.setPackedLong();
                }else if(value2.isAPackedLong()){
                    value1.setPackedLong();
                }
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                return new LightEngineValue(Type.INT_TYPE, insn);
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case PUTFIELD:
                return null;
            default:
                throw new AssertionError();
        }
    }

    private boolean isMaxLong(LightEngineValue value){
        AbstractInsnNode source = value.getSource().iterator().next();

        if(source instanceof LdcInsnNode constant){
            if(constant.cst instanceof Long l){
                return l == Long.MAX_VALUE;
            }
        }

        return false;
    }

    @Override
    public LightEngineValue ternaryOperation(AbstractInsnNode insn, LightEngineValue value1, LightEngineValue value2, LightEngineValue value3) throws AnalyzerException {
        consumeBy(value1, insn);
        consumeBy(value2, insn);
        consumeBy(value3, insn);
        return null;
    }

    @Override
    public LightEngineValue naryOperation(AbstractInsnNode insn, List<? extends LightEngineValue> values) throws AnalyzerException {
        for(LightEngineValue value : values){
            consumeBy(value, insn);
        }

        int opcode = insn.getOpcode();
        if (opcode == MULTIANEWARRAY) {
            return new LightEngineValue(Type.getType(((MultiANewArrayInsnNode) insn).desc), insn);
        } else if (opcode == INVOKEDYNAMIC) {
            Type type = Type.getReturnType(((InvokeDynamicInsnNode) insn).desc);
            if(type.getSort() == Type.VOID) return null;
            return new LightEngineValue(type, insn);
        } else {
            MethodInsnNode methodCall = (MethodInsnNode) insn;
            Type type = Type.getReturnType(methodCall.desc);
            String methodID = methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
            MethodInfo methodInfo = methodInfoMap.get(methodID);
            if(methodInfo != null){
                for(int index: methodInfo.getExpandedIndices()){
                    values.get(index).setPackedLong();
                }

                if(methodInfo.returnsPackedBlockPos()){
                    LightEngineValue value = new LightEngineValue(type, insn);
                    value.setPackedLong();
                    return value;
                }
            }

            if(type.getSort() == Type.VOID) return null;

            return new LightEngineValue(type, insn);
        }
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, LightEngineValue value, LightEngineValue expected) throws AnalyzerException {
        consumeBy(value, insn);
    }

    @Override
    public LightEngineValue merge(LightEngineValue value1, LightEngineValue value2) {
        return value1.merge(value2);
    }

    private void consumeBy(LightEngineValue value, AbstractInsnNode consumer){
        assert value != null;
        value.consumeBy(consumer);
    }

    public void setLocalVarOverrides(List<Integer> overrides) {
        this.localVarOverrides.clear();
        this.localVarOverrides.addAll(overrides);
    }
}
