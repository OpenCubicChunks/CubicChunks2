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
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.HierarchyTree;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FieldID;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
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

public class TransformTrackingInterpreter extends Interpreter<TransformTrackingValue> {
    private final Config config;
    private final Map<Integer, TransformType> parameterOverrides = new HashMap<>();
    private final Set<TransformTrackingValue> returnValues = new HashSet<>();

    private Map<MethodID, AnalysisResults> resultLookup = new HashMap<>();
    private Map<MethodID, List<FutureMethodBinding>> futureMethodBindings;
    private ClassNode currentClass;
    private AncestorHashMap<FieldID, TransformTrackingValue> fieldBindings = new AncestorHashMap<>(new HierarchyTree());

    /**
     * Constructs a new {@link Interpreter}.
     *
     * @param api the ASM API version supported by this interpreter. Must be one of {@link
     *            Opcodes#ASM4}, {@link Opcodes#ASM5}, {@link
     *            Opcodes#ASM6} or {@link Opcodes#ASM7}.
     */
    public TransformTrackingInterpreter(int api, Config config) {
        super(api);
        this.config = config;
    }

    public void reset(){
        parameterOverrides.clear();
    }

    @Override
    public TransformTrackingValue newValue(Type subType) {
        if(subType == null){
            return new TransformTrackingValue(null, fieldBindings);
        }
        if(subType.getSort() == Type.VOID) return null;
        if(subType.getSort() == Type.METHOD) throw new RuntimeException("Method subType not supported");
        return new TransformTrackingValue(subType, fieldBindings);
    }

    @Override
    public TransformTrackingValue newParameterValue(boolean isInstanceMethod, int local, Type subType) {
        if(subType == Type.VOID_TYPE) return null;
        TransformTrackingValue value = new TransformTrackingValue(subType, local, fieldBindings);
        if(parameterOverrides.containsKey(local)){
            value.setTransformType(parameterOverrides.get(local));
        }
        return value;
    }

    @Override
    public TransformTrackingValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
        return switch (insn.getOpcode()){
            case Opcodes.ACONST_NULL -> new TransformTrackingValue(BasicInterpreter.NULL_TYPE, insn, fieldBindings);
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                    Opcodes.ICONST_4, Opcodes.ICONST_5 -> new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
            case Opcodes.LCONST_0, Opcodes.LCONST_1 -> new TransformTrackingValue(Type.LONG_TYPE, insn, fieldBindings);
            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> new TransformTrackingValue(Type.FLOAT_TYPE, insn, fieldBindings);
            case Opcodes.DCONST_0, Opcodes.DCONST_1 -> new TransformTrackingValue(Type.DOUBLE_TYPE, insn, fieldBindings);
            case Opcodes.BIPUSH -> new TransformTrackingValue(Type.BYTE_TYPE, insn, fieldBindings);
            case Opcodes.SIPUSH -> new TransformTrackingValue(Type.SHORT_TYPE, insn, fieldBindings);
            case Opcodes.LDC -> {
                Object value = ((LdcInsnNode) insn).cst;
                if (value instanceof Integer) {
                    yield  new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
                } else if (value instanceof Float) {
                    yield  new TransformTrackingValue(Type.FLOAT_TYPE, insn, fieldBindings);
                } else if (value instanceof Long) {
                    yield  new TransformTrackingValue(Type.LONG_TYPE, insn, fieldBindings);
                } else if (value instanceof Double) {
                    yield  new TransformTrackingValue(Type.DOUBLE_TYPE, insn, fieldBindings);
                } else if (value instanceof String) {
                    yield  new TransformTrackingValue(Type.getObjectType("java/lang/String"), insn, fieldBindings);
                } else if (value instanceof Type) {
                    int sort = ((Type) value).getSort();
                    if (sort == Type.OBJECT || sort == Type.ARRAY) {
                        yield new TransformTrackingValue(Type.getObjectType("java/lang/Class"), insn, fieldBindings);
                    } else if (sort == Type.METHOD) {
                        yield new TransformTrackingValue(Type.getObjectType("java/lang/invoke/MethodType"), insn, fieldBindings);
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                }
                throw new IllegalStateException("This shouldn't happen");
            }
            case Opcodes.JSR -> new TransformTrackingValue(Type.VOID_TYPE, insn, fieldBindings);
            case Opcodes.GETSTATIC -> new TransformTrackingValue(Type.getType(((FieldInsnNode) insn).desc), insn, fieldBindings);
            case Opcodes.NEW -> new TransformTrackingValue(Type.getObjectType(((TypeInsnNode) insn).desc), insn, fieldBindings);
            default -> throw new IllegalStateException("Unexpected value: " + insn.getType());
        };
    }

    @Override
    //Because of the custom Frame (defined in Config$DuplicatorFrame) this method may be called multiple times for the same instruction-value pair
    public TransformTrackingValue copyOperation(AbstractInsnNode insn, TransformTrackingValue value){
        if(insn instanceof VarInsnNode varInsn){
            return new TransformTrackingValue(value.getType(), insn, varInsn.var, value.getTransform(), fieldBindings);
        }else {
            consumeBy(value, insn);
            return new TransformTrackingValue(value.getType(), Set.of(insn), value.getLocalVars(), value.getTransform(), fieldBindings);
        }
    }

    @Override
    public TransformTrackingValue unaryOperation(AbstractInsnNode insn, TransformTrackingValue value) throws AnalyzerException {
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
                return new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                return new TransformTrackingValue(Type.FLOAT_TYPE, insn, fieldBindings);
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                return new TransformTrackingValue(Type.LONG_TYPE, insn, fieldBindings);
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return new TransformTrackingValue(Type.DOUBLE_TYPE, insn, fieldBindings);
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
            case GETFIELD:{
                FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                TransformTrackingValue fieldValue = new TransformTrackingValue(Type.getType(((FieldInsnNode) insn).desc), insn, fieldBindings);
                FieldSource fieldSource = new FieldSource(fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc, 0);
                fieldValue.addFieldSource(fieldSource);

                if(fieldInsnNode.owner.equals(currentClass.name)){
                    FieldID fieldAndDesc = new FieldID(Type.getObjectType(fieldInsnNode.owner), fieldInsnNode.name, Type.getType(fieldInsnNode.desc));
                    TransformTrackingValue fieldBinding = fieldBindings.get(fieldAndDesc);
                    if(fieldBinding != null) {
                        TransformTrackingValue.setSameType(fieldValue, fieldBinding);
                    }
                }

                return fieldValue;
            }
            case NEWARRAY:
                switch (((IntInsnNode) insn).operand) {
                    case T_BOOLEAN:
                        return new TransformTrackingValue(Type.getType("[Z"), insn, fieldBindings);
                    case T_CHAR:
                        return new TransformTrackingValue(Type.getType("[C"), insn, fieldBindings);
                    case T_BYTE:
                        return new TransformTrackingValue(Type.getType("[B"), insn, fieldBindings);
                    case T_SHORT:
                        return new TransformTrackingValue(Type.getType("[S"), insn, fieldBindings);
                    case T_INT:
                        return new TransformTrackingValue(Type.getType("[I"), insn, fieldBindings);
                    case T_FLOAT:
                        return new TransformTrackingValue(Type.getType("[F"), insn, fieldBindings);
                    case T_DOUBLE:
                        return new TransformTrackingValue(Type.getType("[D"), insn, fieldBindings);
                    case T_LONG:
                        return new TransformTrackingValue(Type.getType("[J"), insn, fieldBindings);
                    default:
                        break;
                }
                throw new AnalyzerException(insn, "Invalid array subType");
            case ANEWARRAY:
                return new TransformTrackingValue(Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc)), insn, fieldBindings);
            case ARRAYLENGTH:
                return new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
            case ATHROW:
                return null;
            case CHECKCAST:
                return new TransformTrackingValue(Type.getObjectType(((TypeInsnNode) insn).desc), insn, fieldBindings);
            case INSTANCEOF:
                return new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
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
    public TransformTrackingValue binaryOperation(AbstractInsnNode insn, TransformTrackingValue value1, TransformTrackingValue value2) throws AnalyzerException {
        consumeBy(value1, insn);
        consumeBy(value2, insn);

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
                value = new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
                if(insn.getOpcode() == IALOAD || insn.getOpcode() == BALOAD || insn.getOpcode() == CALOAD || insn.getOpcode() == SALOAD) {
                    deepenFieldSource(value1, value);
                }
                return value;
            case FALOAD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                value = new TransformTrackingValue(Type.FLOAT_TYPE, insn, fieldBindings);
                if(insn.getOpcode() == FALOAD) {
                    deepenFieldSource(value1, value);
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
                value = new TransformTrackingValue(Type.LONG_TYPE, insn, fieldBindings);
                if(insn.getOpcode() == LALOAD) {
                    deepenFieldSource(value1, value);
                }
                return value;
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                value = new TransformTrackingValue(Type.DOUBLE_TYPE, insn, fieldBindings);
                if(insn.getOpcode() == DALOAD) {
                    deepenFieldSource(value1, value);
                }
                return value;
            case AALOAD:
                value = new TransformTrackingValue(value1.getType().getElementType(), insn, fieldBindings);
                deepenFieldSource(value1, value);
                return value;
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                TransformTrackingValue.setSameType(value1, value2);
                return new TransformTrackingValue(Type.INT_TYPE, insn, fieldBindings);
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

    @Override
    public TransformTrackingValue ternaryOperation(AbstractInsnNode insn, TransformTrackingValue value1, TransformTrackingValue value2, TransformTrackingValue value3) throws AnalyzerException {
        consumeBy(value1, insn);
        consumeBy(value2, insn);
        consumeBy(value3, insn);
        return null;
    }

    @Override
    public TransformTrackingValue naryOperation(AbstractInsnNode insn, List<? extends TransformTrackingValue> values) throws AnalyzerException {
        for(TransformTrackingValue value : values){
            consumeBy(value, insn);
        }

        int opcode = insn.getOpcode();
        if (opcode == MULTIANEWARRAY) {
            return new TransformTrackingValue(Type.getType(((MultiANewArrayInsnNode) insn).desc), insn, fieldBindings);
        } else if (opcode == INVOKEDYNAMIC) {
            //TODO: Handle invokedynamic and subType inference for call sites
            InvokeDynamicInsnNode node = (InvokeDynamicInsnNode) insn;
            Type subType = Type.getReturnType(node.desc);

            TransformTrackingValue ret = new TransformTrackingValue(subType, insn, fieldBindings);

            //Make sure this is LambdaMetafactory.metafactory
            if(node.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && node.bsm.getName().equals("metafactory")){
                //Bind values
                Handle referenceMethod = (Handle) node.bsmArgs[1];
                MethodID.CallType callType = switch (referenceMethod.getTag()) {
                    case H_INVOKESTATIC -> MethodID.CallType.STATIC;
                    case H_INVOKEVIRTUAL -> MethodID.CallType.VIRTUAL;
                    case H_INVOKESPECIAL, H_NEWINVOKESPECIAL -> MethodID.CallType.SPECIAL;
                    case H_INVOKEINTERFACE -> MethodID.CallType.INTERFACE;
                    default -> throw new AssertionError();
                };
                MethodID methodID = new MethodID(referenceMethod.getOwner(), referenceMethod.getName(), referenceMethod.getDesc(), callType);

                if(resultLookup.containsKey(methodID)){
                    bindValuesToMethod(resultLookup.get(methodID), 0, values.toArray(new TransformTrackingValue[0]));
                }else{
                    futureMethodBindings.computeIfAbsent(methodID, k -> new ArrayList<>()).add(
                            new FutureMethodBinding(0, values.toArray(new TransformTrackingValue[0]))
                    );
                }

                boolean isTransformPredicate = ret.getTransform().getSubtype() == TransformSubtype.SubType.PREDICATE;
                boolean isTransformConsumer = ret.getTransform().getSubtype() == TransformSubtype.SubType.CONSUMER;

                if(isTransformConsumer && isTransformPredicate){
                    throw new RuntimeException("A subType cannot be both a predicate and a consumer. This is a bug in the configuration ('subType-transform.json').");
                }

                if(isTransformConsumer || isTransformPredicate) {
                    int offset = values.size();
                    offset += callType.getOffset();

                    if(resultLookup.containsKey(methodID)){
                        bindValuesToMethod(resultLookup.get(methodID), offset, ret);
                    }else{
                        futureMethodBindings.computeIfAbsent(methodID, k -> new ArrayList<>()).add(
                                new FutureMethodBinding(offset, ret)
                        );
                    }
                }
            }

            if(subType.getSort() == Type.VOID) return null;

            return ret;
        } else {
            MethodInsnNode methodCall = (MethodInsnNode) insn;
            Type subType = Type.getReturnType(methodCall.desc);

            MethodID methodID = new MethodID(methodCall.owner, methodCall.name, methodCall.desc, MethodID.CallType.fromOpcode(opcode));

            if(resultLookup.containsKey(methodID)) {
                bindValuesToMethod(resultLookup.get(methodID), 0, values.toArray(new TransformTrackingValue[0]));
            }else if(methodCall.owner.equals(currentClass.name)){
                futureMethodBindings.computeIfAbsent(methodID, k -> new ArrayList<>()).add(
                        new FutureMethodBinding(0, values.toArray(new TransformTrackingValue[0]))
                );
            }

            List<MethodParameterInfo> possibilities = config.getMethodParameterInfo().get(methodID);

            if(possibilities != null){
                TransformTrackingValue returnValue = null;

                if(subType != null){
                    returnValue = new TransformTrackingValue(subType, insn, fieldBindings);
                }

                for(MethodParameterInfo info : possibilities) {
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

            if(subType.getSort() == Type.VOID) return null;

            return new TransformTrackingValue(subType, insn, fieldBindings);
        }
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, TransformTrackingValue value, TransformTrackingValue expected) throws AnalyzerException {
        if(value.getTransformType() != null){
            if(expected.transformedTypes().size() == 1){
                returnValues.add(value);
            }else{
                throw new AnalyzerException(insn, "Return subType is not single");
            }
        }
        consumeBy(value, insn);
    }

    @Override
    public TransformTrackingValue merge(TransformTrackingValue value1, TransformTrackingValue value2) {
        if(!Objects.equals(value1.getType(), value2.getType())){
            //System.err.println("WARNING: Merge types are not equal");
            return value1;
        }

        return value1.merge(value2);
    }

    private void consumeBy(TransformTrackingValue value, AbstractInsnNode consumer){
        assert value != null;
        value.consumeBy(consumer);
    }

    public void setLocalVarOverrides(Map<Integer, TransformType> localVarOverrides) {
        this.parameterOverrides.clear();
        this.parameterOverrides.putAll(localVarOverrides);
    }

    private static void deepenFieldSource(TransformTrackingValue fieldValue, TransformTrackingValue newValue){
        for(FieldSource source : fieldValue.getFieldSources()){
            newValue.addFieldSource(source.deeper());
        }

        TransformTrackingValue.setSameType(fieldValue, newValue);
    }

    public static void bindValuesToMethod(AnalysisResults methodResults, int parameterOffset, TransformTrackingValue... parameters){
        Frame<TransformTrackingValue> firstFrame = methodResults.frames()[0];

        Type[] argumentTypes = Type.getArgumentTypes(methodResults.methodNode().desc);
        Type[] allTypes;
        if(!ASMUtil.isStatic(methodResults.methodNode())){
            allTypes = new Type[argumentTypes.length + 1];
            allTypes[0] = Type.getObjectType("java/lang/Object"); //The actual subType doesn't matter
            System.arraycopy(argumentTypes, 0, allTypes, 1, argumentTypes.length);
        }else{
            allTypes = argumentTypes;
        }

        int paramIndex = 0;
        int varIndex = 0;

        for (int i = 0; i < parameterOffset; i++) {
            varIndex += allTypes[i].getSize();
        }

        for(Type parameterType : allTypes){
            if(paramIndex >= parameters.length){ //This can happen for invokedynamic
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

    public void setFutureBindings(Map<MethodID, List<FutureMethodBinding>> futureMethodBindings) {
        this.futureMethodBindings = futureMethodBindings;
    }

    public void setCurrentClass(ClassNode currentClass) {
        this.currentClass = currentClass;
    }

    public void setFieldBindings(AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues) {
        this.fieldBindings = fieldPseudoValues;
    }
}
