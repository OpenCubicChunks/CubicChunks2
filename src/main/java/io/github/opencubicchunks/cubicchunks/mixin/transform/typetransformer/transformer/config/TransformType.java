package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;

public class TransformType {
    private final String id;
    private final Type from;
    private final Type[] to;
    private final MethodID[] fromOriginal;
    private final MethodID toOriginal;

    private final Type originalPredicateType;
    private final Type transformedPredicateType;

    private final Type originalConsumerType;
    private final Type transformedConsumerType;

    private final String[] postfix;

    private final Map<Object, BytecodeFactory[]> constantReplacements;

    private final int transformedSize;

    public TransformType(String id, Type from, Type[] to, MethodID[] fromOriginal, MethodID toOriginal, Type originalPredicateType, Type transformedPredicateType, Type originalConsumerType,
                         Type transformedConsumerType, String[] postfix, Map<Object, BytecodeFactory[]> constantReplacements) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.fromOriginal = fromOriginal;
        this.toOriginal = toOriginal;
        this.originalPredicateType = originalPredicateType;
        this.transformedPredicateType = transformedPredicateType;
        this.originalConsumerType = originalConsumerType;
        this.transformedConsumerType = transformedConsumerType;
        this.constantReplacements = constantReplacements;

        int size = 0;
        for (Type t : to) {
            size += t.getSize();
        }
        this.transformedSize = size;
        this.postfix = postfix;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("Transform Type " + id + "[" + ASMUtil.onlyClassName(from.getClassName()) + " -> (");

        for (int i = 0; i < to.length; i++) {
            str.append(ASMUtil.onlyClassName(to[i].getClassName()));
            if (i < to.length - 1) {
                str.append(", ");
            }
        }

        str.append(")]");

        return str.toString();
    }

    public void addParameterInfoTo(Map<MethodID, List<MethodParameterInfo>> parameterInfo) {
        if (fromOriginal != null) {
            addFromOriginalInfo(parameterInfo);
        }

        if (toOriginal != null) {
            addToOriginalInfo(parameterInfo);
        }

        if (originalPredicateType != null) {
            addSpecialInfo(parameterInfo, originalPredicateType, "test", Type.BOOLEAN_TYPE, TransformSubtype.SubType.PREDICATE, transformedPredicateType);
        }

        if (originalConsumerType != null) {
            addSpecialInfo(parameterInfo, originalConsumerType, "accept", Type.VOID_TYPE, TransformSubtype.SubType.CONSUMER, transformedConsumerType);
        }
    }

    private void addFromOriginalInfo(Map<MethodID, List<MethodParameterInfo>> parameterInfo) {
        int i = 0;
        for (MethodID methodID : fromOriginal) {
            MethodReplacement methodReplacement = new MethodReplacement(
                new BytecodeFactory[] {
                    (Function<Type, Integer> variableAllocator) -> new InsnList()
                },
                new List[][] {
                    new List[] {
                        Collections.singletonList(i)
                    }
                }
            );
            MethodParameterInfo info = new MethodParameterInfo(
                methodID,
                TransformSubtype.createDefault(methodID.getDescriptor().getReturnType()),
                new TransformSubtype[] { TransformSubtype.of(this) },
                null,
                methodReplacement
            );
            parameterInfo.computeIfAbsent(methodID, k -> new ArrayList<>()).add(info);
            i++;
        }
    }

    private void addToOriginalInfo(Map<MethodID, List<MethodParameterInfo>> parameterInfo) {
        BytecodeFactory[] expansions = new BytecodeFactory[to.length];
        for (int i = 0; i < to.length; i++) {
            expansions[i] = (Function<Type, Integer> variableAllocator) -> new InsnList();
        }

        TransformSubtype[] parameterTypes = new TransformSubtype[this.to.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = TransformSubtype.createDefault(this.to[i]);
        }

        List<Integer>[][] indices = new List[parameterTypes.length][parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            for (int j = 0; j < parameterTypes.length; j++) {
                if (i == j) {
                    indices[i][j] = Collections.singletonList(0);
                } else {
                    indices[i][j] = Collections.emptyList();
                }
            }
        }

        MethodParameterInfo info = new MethodParameterInfo(toOriginal, TransformSubtype.of(this), parameterTypes, null, new MethodReplacement(expansions, indices));
        parameterInfo.computeIfAbsent(toOriginal, k -> new ArrayList<>()).add(info);
    }

    private void addSpecialInfo(Map<MethodID, List<MethodParameterInfo>> parameterInfo, Type type, String methodName, Type returnType, TransformSubtype.SubType subType,
                                Type transformedType) {
        MethodID consumerID = new MethodID(type, methodName, Type.getMethodType(returnType, from), MethodID.CallType.INTERFACE);

        TransformSubtype[] argTypes = new TransformSubtype[] { TransformSubtype.of(this, subType), TransformSubtype.of(this) };

        MethodReplacement methodReplacement = new MethodReplacement(
            (Function<Type, Integer> variableAllocator) -> {
                InsnList list = new InsnList();
                list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, transformedType.getInternalName(), methodName, Type.getMethodDescriptor(returnType, to)));
                return list;
            },

            argTypes
        );

        MethodTransformChecker.MinimumConditions[] minimumConditions = new MethodTransformChecker.MinimumConditions[] {
            new MethodTransformChecker.MinimumConditions(
                TransformSubtype.createDefault(returnType),
                TransformSubtype.of(this, subType),
                TransformSubtype.createDefault(this.from)
            ),
            new MethodTransformChecker.MinimumConditions(
                TransformSubtype.createDefault(returnType),
                TransformSubtype.createDefault(type),
                TransformSubtype.of(this)
            )
        };

        MethodParameterInfo info = new MethodParameterInfo(
            consumerID,
            TransformSubtype.createDefault(consumerID.getDescriptor().getReturnType()),
            argTypes,
            minimumConditions,
            methodReplacement
        );
        parameterInfo.computeIfAbsent(consumerID, k -> new ArrayList<>()).add(info);
    }

    public String getName() {
        return id;
    }

    public Type getFrom() {
        return from;
    }

    public Type[] getTo() {
        return to;
    }

    public MethodID[] getFromOriginal() {
        return fromOriginal;
    }

    public MethodID getToOriginal() {
        return toOriginal;
    }

    public Type getOriginalPredicateType() {
        return originalPredicateType;
    }

    public Type getTransformedPredicateType() {
        return transformedPredicateType;
    }

    public Type getOriginalConsumerType() {
        return originalConsumerType;
    }

    public Type getTransformedConsumerType() {
        return transformedConsumerType;
    }

    public int getTransformedSize() {
        return transformedSize;
    }

    public String[] getPostfix() {
        return postfix;
    }

    public Map<Object, BytecodeFactory[]> getConstantReplacements() {
        return constantReplacements;
    }

    public InsnList convertToTransformed(Supplier<InsnList> originalSupplier) {
        InsnList list = new InsnList();

        //Use the methods provided in the config
        for (MethodID methodID : fromOriginal) {
            list.add(originalSupplier.get());
            list.add(methodID.callNode());
        }

        return list;
    }
}
