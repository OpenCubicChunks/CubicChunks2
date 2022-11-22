package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FieldID;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;

/**
 * An interpreter which infers transforms that should be applied to values
 */
public class TransformTrackingInterpreter extends Interpreter<TransformTrackingValue> {
    private final Config config;
    private final Map<Integer, TransformType> parameterOverrides = new HashMap<>();
    private final Set<TransformTrackingValue> returnValues = new HashSet<>();

    private Map<MethodID, AnalysisResults> resultLookup = new HashMap<>();
    private Map<MethodID, List<FutureMethodBinding>> futureMethodBindings;
    private ClassNode currentClass;
    private AncestorHashMap<FieldID, TransformTrackingValue> fieldBindings;

    /**
     * Constructs a new {@link Interpreter}.
     *
     * @param api the ASM API version supported by this interpreter. Must be one of {@link Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or {@link Opcodes#ASM7}.
     */
    public TransformTrackingInterpreter(int api, Config config) {
        super(api);
        this.config = config;
        this.fieldBindings = new AncestorHashMap<>(config.getTypeInfo());
    }

    public void reset() {
        parameterOverrides.clear();
    }

    @Override
    public @Nullable TransformTrackingValue newValue(@Nullable Type subType) {
        if (subType == null) {
            return new TransformTrackingValue(null, fieldBindings, config);
        }
        if (subType.getSort() == Type.VOID) return null;
        if (subType.getSort() == Type.METHOD) throw new RuntimeException("Method subType not supported");
        return new TransformTrackingValue(subType, fieldBindings, config);
    }

    @Override
    public @Nullable TransformTrackingValue newParameterValue(boolean isInstanceMethod, int local, Type subType) {
        //Use parameter overrides to try to get the types
        if (subType == Type.VOID_TYPE) return null;
        TransformTrackingValue value = new TransformTrackingValue(subType, fieldBindings, config);
        if (parameterOverrides.containsKey(local)) {
            value.setTransformType(parameterOverrides.get(local));
        }
        return value;
    }

    @Override
    public TransformTrackingValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
        return switch (insn.getOpcode()) {
            case Opcodes.ACONST_NULL -> new TransformTrackingValue(BasicInterpreter.NULL_TYPE, fieldBindings, config);
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                Opcodes.ICONST_4, Opcodes.ICONST_5 -> new TransformTrackingValue(Type.INT_TYPE, fieldBindings, config);
            case Opcodes.LCONST_0, Opcodes.LCONST_1 -> new TransformTrackingValue(Type.LONG_TYPE, fieldBindings, config);
            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> new TransformTrackingValue(Type.FLOAT_TYPE, fieldBindings, config);
            case Opcodes.DCONST_0, Opcodes.DCONST_1 -> new TransformTrackingValue(Type.DOUBLE_TYPE, fieldBindings, config);
            case Opcodes.BIPUSH -> new TransformTrackingValue(Type.BYTE_TYPE, fieldBindings, config);
            case Opcodes.SIPUSH -> new TransformTrackingValue(Type.SHORT_TYPE, fieldBindings, config);
            case Opcodes.LDC -> {
                Object value = ((LdcInsnNode) insn).cst;
                if (value instanceof Integer) {
                    yield new TransformTrackingValue(Type.INT_TYPE, fieldBindings, config);
                } else if (value instanceof Float) {
                    yield new TransformTrackingValue(Type.FLOAT_TYPE, fieldBindings, config);
                } else if (value instanceof Long) {
                    yield new TransformTrackingValue(Type.LONG_TYPE, fieldBindings, config);
                } else if (value instanceof Double) {
                    yield new TransformTrackingValue(Type.DOUBLE_TYPE, fieldBindings, config);
                } else if (value instanceof String) {
                    yield new TransformTrackingValue(Type.getObjectType("java/lang/String"), fieldBindings, config);
                } else if (value instanceof Type) {
                    int sort = ((Type) value).getSort();
                    if (sort == Type.OBJECT || sort == Type.ARRAY) {
                        yield new TransformTrackingValue(Type.getObjectType("java/lang/Class"), fieldBindings, config);
                    } else if (sort == Type.METHOD) {
                        yield new TransformTrackingValue(Type.getObjectType("java/lang/invoke/MethodType"), fieldBindings, config);
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                }
                throw new IllegalStateException("This shouldn't happen");
            }
            case Opcodes.JSR -> new TransformTrackingValue(Type.VOID_TYPE, fieldBindings, config);
            case Opcodes.GETSTATIC -> new TransformTrackingValue(Type.getType(((FieldInsnNode) insn).desc), fieldBindings, config);
            case Opcodes.NEW -> new TransformTrackingValue(Type.getObjectType(((TypeInsnNode) insn).desc), fieldBindings, config);
            default -> throw new IllegalStateException("Unexpected value: " + insn.getType());
        };
    }

    @Override
    //Because of the custom Frame (defined in Config$DuplicatorFrame) this method may be called multiple times for the same instruction-value pair
    public TransformTrackingValue copyOperation(AbstractInsnNode insn, TransformTrackingValue value) {
        if (insn instanceof VarInsnNode varInsn) {
            return new TransformTrackingValue(value.getType(), value.getTransform(), fieldBindings, config);
        } else {
            return new TransformTrackingValue(value.getType(), value.getTransform(), fieldBindings, config);
        }
    }

    @Override
    public @Nullable TransformTrackingValue unaryOperation(AbstractInsnNode insn, TransformTrackingValue value) throws AnalyzerException {

        FieldInsnNode fieldInsnNode;
        switch (insn.getOpcode()) {
            case INEG:
            case IINC:
            case L2I:
            case F2I:
            case D2I:
            case I2B:
            case I2C:
            case I2S:
            case INSTANCEOF:
            case ARRAYLENGTH:
                return new TransformTrackingValue(Type.INT_TYPE, fieldBindings, config);
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                return new TransformTrackingValue(Type.FLOAT_TYPE, fieldBindings, config);
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                return new TransformTrackingValue(Type.LONG_TYPE, fieldBindings, config);
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return new TransformTrackingValue(Type.DOUBLE_TYPE, fieldBindings, config);
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
                return null;
            case PUTSTATIC:
                fieldInsnNode = (FieldInsnNode) insn;

                if (fieldInsnNode.owner.equals(currentClass.name)) {
                    FieldID fieldAndDesc = new FieldID(Type.getObjectType(fieldInsnNode.owner), fieldInsnNode.name, Type.getType(fieldInsnNode.desc));
                    TransformTrackingValue field = fieldBindings.get(fieldAndDesc);

                    if (field != null) {
                        TransformTrackingValue.setSameType(field, value);
                    }
                }
                return null;
            case GETFIELD: {
                //Add field source and set the value to have the same transform as the field
                fieldInsnNode = (FieldInsnNode) insn;
                TransformTrackingValue fieldValue = new TransformTrackingValue(Type.getType(((FieldInsnNode) insn).desc), fieldBindings, config);

                if (fieldInsnNode.owner.equals(currentClass.name)) {
                    FieldID fieldAndDesc = new FieldID(Type.getObjectType(fieldInsnNode.owner), fieldInsnNode.name, Type.getType(fieldInsnNode.desc));
                    TransformTrackingValue fieldBinding = fieldBindings.get(fieldAndDesc);
                    if (fieldBinding != null) {
                        TransformTrackingValue.setSameType(fieldValue, fieldBinding);
                    }
                }

                return fieldValue;
            }
            case NEWARRAY:
                switch (((IntInsnNode) insn).operand) {
                    case T_BOOLEAN:
                        return new TransformTrackingValue(Type.getType("[Z"), fieldBindings, config);
                    case T_CHAR:
                        return new TransformTrackingValue(Type.getType("[C"), fieldBindings, config);
                    case T_BYTE:
                        return new TransformTrackingValue(Type.getType("[B"), fieldBindings, config);
                    case T_SHORT:
                        return new TransformTrackingValue(Type.getType("[S"), fieldBindings, config);
                    case T_INT:
                        return new TransformTrackingValue(Type.getType("[I"), fieldBindings, config);
                    case T_FLOAT:
                        return new TransformTrackingValue(Type.getType("[F"), fieldBindings, config);
                    case T_DOUBLE:
                        return new TransformTrackingValue(Type.getType("[D"), fieldBindings, config);
                    case T_LONG:
                        return new TransformTrackingValue(Type.getType("[J"), fieldBindings, config);
                    default:
                        break;
                }
                throw new AnalyzerException(insn, "Invalid array subType");
            case ANEWARRAY:
                return new TransformTrackingValue(Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc)), fieldBindings, config);
            case ATHROW:
                return null;
            case CHECKCAST:
                return new TransformTrackingValue(Type.getObjectType(((TypeInsnNode) insn).desc), fieldBindings, config);
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
    public @Nullable TransformTrackingValue binaryOperation(AbstractInsnNode insn, TransformTrackingValue value1, TransformTrackingValue value2) throws AnalyzerException {
        TransformTrackingValue value;

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
                value = new TransformTrackingValue(Type.INT_TYPE,fieldBindings, config);
                if (insn.getOpcode() == IALOAD || insn.getOpcode() == BALOAD || insn.getOpcode() == CALOAD || insn.getOpcode() == SALOAD) {
                    TransformTrackingValue.setSameType(value1, value);
                }
                return value;
            case FALOAD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                value = new TransformTrackingValue(Type.FLOAT_TYPE,fieldBindings, config);
                if (insn.getOpcode() == FALOAD) {
                    TransformTrackingValue.setSameType(value1, value);
                }
                return value;
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
                value = new TransformTrackingValue(Type.LONG_TYPE,fieldBindings, config);
                if (insn.getOpcode() == LALOAD) {
                    TransformTrackingValue.setSameType(value1, value);
                }
                return value;
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                value = new TransformTrackingValue(Type.DOUBLE_TYPE,fieldBindings, config);
                if (insn.getOpcode() == DALOAD) {
                    TransformTrackingValue.setSameType(value1, value);
                }
                return value;
            case AALOAD:
                value = new TransformTrackingValue(ASMUtil.getArrayElement(value1.getType()),fieldBindings, config);
                TransformTrackingValue.setSameType(value1, value);
                return value;
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                TransformTrackingValue.setSameType(value1, value2);
                return new TransformTrackingValue(Type.INT_TYPE,fieldBindings, config);
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
                return null;
            case PUTFIELD:
                FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;

                if (fieldInsnNode.owner.equals(currentClass.name)) {
                    FieldID fieldAndDesc = new FieldID(Type.getObjectType(fieldInsnNode.owner), fieldInsnNode.name, Type.getType(fieldInsnNode.desc));
                    TransformTrackingValue field = fieldBindings.get(fieldAndDesc);

                    if (field != null) {
                        TransformTrackingValue.setSameType(field, value2);
                    }
                }
                return null;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public @Nullable TransformTrackingValue ternaryOperation(AbstractInsnNode insn, TransformTrackingValue value1, TransformTrackingValue value2, TransformTrackingValue value3) {
        return null;
    }

    @Override
    public @Nullable TransformTrackingValue naryOperation(AbstractInsnNode insn, List<? extends TransformTrackingValue> values) throws AnalyzerException {
        int opcode = insn.getOpcode();
        if (opcode == MULTIANEWARRAY) {
            return new TransformTrackingValue(Type.getType(((MultiANewArrayInsnNode) insn).desc), fieldBindings, config);
        } else if (opcode == INVOKEDYNAMIC) {
            return invokeDynamicOperation(insn, values);
        } else {
            return methodCallOperation(insn, values, opcode);
        }
    }

    @Nullable private TransformTrackingValue methodCallOperation(AbstractInsnNode insn, List<? extends TransformTrackingValue> values, int opcode) {
        //Create bindings to the method parameters
        MethodInsnNode methodCall = (MethodInsnNode) insn;
        Type subType = Type.getReturnType(methodCall.desc);

        MethodID methodID = new MethodID(methodCall.owner, methodCall.name, methodCall.desc, MethodID.CallType.fromOpcode(opcode));

        bindValues(methodID, 0, values.toArray(new TransformTrackingValue[0]));

        List<MethodParameterInfo> possibilities = config.getMethodParameterInfo().get(methodID);

        if (possibilities != null) {
            TransformTrackingValue returnValue = null;

            if (subType != null) {
                returnValue = new TransformTrackingValue(subType, fieldBindings, config);
            }

            for (MethodParameterInfo info : possibilities) {
                TransformTrackingValue[] parameterValues = new TransformTrackingValue[info.getParameterTypes().length];
                for (int i = 0; i < values.size(); i++) {
                    parameterValues[i] = values.get(i);
                }

                UnresolvedMethodTransform unresolvedTransform = new UnresolvedMethodTransform(info, returnValue, parameterValues);

                int checkResult = unresolvedTransform.check();
                if (checkResult == 0) {
                    if (returnValue != null) {
                        returnValue.possibleTransformChecks.add(unresolvedTransform);
                    }

                    for (TransformTrackingValue parameterValue : parameterValues) {
                        parameterValue.possibleTransformChecks.add(unresolvedTransform);
                    }
                } else if (checkResult == 1) {
                    unresolvedTransform.accept();
                    break;
                }
            }

            return returnValue;
        }

        if (methodCall.owner.equals("java/util/Arrays") && methodCall.name.equals("fill")) {
            TransformTrackingValue array = values.get(0);
            TransformTrackingValue value = values.get(1);

            TransformTrackingValue.setSameType(array, value);
        }

        if (subType.getSort() == Type.VOID) return null;

        return new TransformTrackingValue(subType, fieldBindings, config);
    }

    @Nullable private TransformTrackingValue invokeDynamicOperation(AbstractInsnNode insn, List<? extends TransformTrackingValue> values) {
        //Bind the lambda captured parameters and lambda types
        InvokeDynamicInsnNode node = (InvokeDynamicInsnNode) insn;
        Type subType = Type.getReturnType(node.desc);

        TransformTrackingValue ret = new TransformTrackingValue(subType, fieldBindings, config);

        //Make sure this is LambdaMetafactory.metafactory
        if (node.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && node.bsm.getName().equals("metafactory")) {
            //Bind values
            Handle referenceMethod = (Handle) node.bsmArgs[1];
            MethodID.CallType callType = getDynamicCallType(referenceMethod);
            MethodID methodID = new MethodID(referenceMethod.getOwner(), referenceMethod.getName(), referenceMethod.getDesc(), callType);

            bindValues(methodID, 0, values.toArray(new TransformTrackingValue[0]));

            boolean isTransformPredicate = ret.getTransform().getSubtype() == TransformSubtype.SubType.PREDICATE;
            boolean isTransformConsumer = ret.getTransform().getSubtype() == TransformSubtype.SubType.CONSUMER;

            if (isTransformConsumer && isTransformPredicate) {
                throw new RuntimeException("A subType cannot be both a predicate and a consumer. This is a bug in the configuration ('subType-transform.json').");
            }

            if (isTransformConsumer || isTransformPredicate) {
                int offset = values.size();
                offset += callType.getOffset();

                bindValues(methodID, offset, ret);
            }
        }

        if (subType.getSort() == Type.VOID) return null;

        return ret;
    }

    //Sets the values to have the same types as the method parameters. If the method hasn't
    //been analyzed, it will bind them later.
    private void bindValues(MethodID methodID, int offset, TransformTrackingValue... values) {
        if (resultLookup.containsKey(methodID)) {
            bindValuesToMethod(resultLookup.get(methodID), offset, values);
        } else {
            futureMethodBindings.computeIfAbsent(methodID, k -> new ArrayList<>()).add(
                new FutureMethodBinding(offset, values)
            );
        }
    }

    @NotNull private MethodID.CallType getDynamicCallType(Handle referenceMethod) {
        return switch (referenceMethod.getTag()) {
            case H_INVOKESTATIC -> MethodID.CallType.STATIC;
            case H_INVOKEVIRTUAL -> MethodID.CallType.VIRTUAL;
            case H_INVOKESPECIAL, H_NEWINVOKESPECIAL -> MethodID.CallType.SPECIAL;
            case H_INVOKEINTERFACE -> MethodID.CallType.INTERFACE;
            default -> throw new AssertionError();
        };
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, TransformTrackingValue value, TransformTrackingValue expected) throws AnalyzerException {
        if (value.getTransformType() != null) {
            if (expected.transformedTypes().size() == 1) {
                returnValues.add(value);
            } else {
                //A method cannot return multiple values.
                throw new AnalyzerException(insn, "Return subType is not single");
            }
        }
    }

    @Override
    public TransformTrackingValue merge(TransformTrackingValue value1, TransformTrackingValue value2) {
        if (!Objects.equals(value1.getType(), value2.getType())) {
            //System.err.println("WARNING: Merge types are not equal");
            return value1;
        }

        return value1.merge(value2);
    }

    public void setLocalVarOverrides(Map<Integer, TransformType> localVarOverrides) {
        this.parameterOverrides.clear();
        this.parameterOverrides.putAll(localVarOverrides);
    }

    public static void bindValuesToMethod(AnalysisResults methodResults, int parameterOffset, TransformTrackingValue... parameters) {
        Frame<TransformTrackingValue> firstFrame = methodResults.frames()[0];

        Type[] argumentTypes = Type.getArgumentTypes(methodResults.methodNode().desc);
        Type[] allTypes;
        if (!ASMUtil.isStatic(methodResults.methodNode())) {
            //Remove the first element because it represents 'this'
            allTypes = new Type[argumentTypes.length + 1];
            allTypes[0] = Type.getObjectType("java/lang/Object"); //The actual subType doesn't matter
            System.arraycopy(argumentTypes, 0, allTypes, 1, argumentTypes.length);
        } else {
            allTypes = argumentTypes;
        }

        int paramIndex = 0;
        int varIndex = 0;

        for (int i = 0; i < parameterOffset; i++) {
            varIndex += allTypes[i].getSize();
        }

        for (Type parameterType : allTypes) {
            if (paramIndex >= parameters.length) { //This can happen for invokedynamic
                break;
            }
            TransformTrackingValue.setSameType(firstFrame.getLocal(varIndex), parameters[paramIndex]);
            varIndex += parameterType.getSize();
            paramIndex++;
        }
    }

    public void setResultLookup(Map<MethodID, AnalysisResults> analysisResults) {
        this.resultLookup = analysisResults;
    }

    public void setFutureBindings(Map<MethodID, List<FutureMethodBinding>> bindings) {
        this.futureMethodBindings = bindings;
    }

    public void setCurrentClass(ClassNode currentClass) {
        this.currentClass = currentClass;
    }

    public void setFieldBindings(AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues) {
        this.fieldBindings = fieldPseudoValues;
    }
}
