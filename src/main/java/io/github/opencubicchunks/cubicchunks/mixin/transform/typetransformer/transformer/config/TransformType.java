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
                MethodParameterInfo info = new MethodParameterInfo(methodID, TransformSubtype.of(null), new TransformSubtype[] { TransformSubtype.of(this) }, null, methodReplacement);
                parameterInfo.computeIfAbsent(methodID, k -> new ArrayList<>()).add(info);
                i++;
            }
        }

        BytecodeFactory[] expansions = new BytecodeFactory[to.length];
        for (int i = 0; i < to.length; i++) {
            expansions[i] = (Function<Type, Integer> variableAllocator) -> new InsnList();
        }

        if (toOriginal != null) {
            TransformSubtype[] to = new TransformSubtype[this.to.length];
            for (int i = 0; i < to.length; i++) {
                to[i] = TransformSubtype.of(null);
            }

            List<Integer>[][] indices = new List[to.length][to.length];
            for (int i = 0; i < to.length; i++) {
                for (int j = 0; j < to.length; j++) {
                    if (i == j) {
                        indices[i][j] = Collections.singletonList(0);
                    } else {
                        indices[i][j] = Collections.emptyList();
                    }
                }
            }

            MethodParameterInfo info = new MethodParameterInfo(toOriginal, TransformSubtype.of(this), to, null, new MethodReplacement(expansions, indices));
            parameterInfo.computeIfAbsent(toOriginal, k -> new ArrayList<>()).add(info);
        }

        if (originalPredicateType != null) {
            MethodID predicateID = new MethodID(originalPredicateType, "test", Type.getMethodType(Type.BOOLEAN_TYPE, from), MethodID.CallType.INTERFACE);

            TransformSubtype[] argTypes = new TransformSubtype[] { TransformSubtype.of(this, "predicate"), TransformSubtype.of(this) };

            MethodReplacement methodReplacement = new MethodReplacement(
                (Function<Type, Integer> variableAllocator) -> {
                    InsnList list = new InsnList();
                    list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, transformedPredicateType.getInternalName(), "test", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, to)));
                    return list;
                }
            );

            MethodTransformChecker.Minimum[] minimums = new MethodTransformChecker.Minimum[] {
                new MethodTransformChecker.Minimum(TransformSubtype.of(null), TransformSubtype.of(this, "predicate"), TransformSubtype.of(null)),
                new MethodTransformChecker.Minimum(TransformSubtype.of(null), TransformSubtype.of(null), TransformSubtype.of(this))
            };

            MethodParameterInfo info = new MethodParameterInfo(predicateID, TransformSubtype.of(null), argTypes, minimums, methodReplacement);
            parameterInfo.computeIfAbsent(predicateID, k -> new ArrayList<>()).add(info);
        }

        if (originalConsumerType != null) {
            MethodID consumerID = new MethodID(originalConsumerType, "accept", Type.getMethodType(Type.VOID_TYPE, from), MethodID.CallType.INTERFACE);

            TransformSubtype[] argTypes = new TransformSubtype[] { TransformSubtype.of(this, "consumer"), TransformSubtype.of(this) };

            MethodReplacement methodReplacement = new MethodReplacement(
                (Function<Type, Integer> variableAllocator) -> {
                    InsnList list = new InsnList();
                    list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, transformedConsumerType.getInternalName(), "accept", Type.getMethodDescriptor(Type.VOID_TYPE, to)));
                    return list;
                }
            );

            MethodTransformChecker.Minimum[] minimums = new MethodTransformChecker.Minimum[] {
                new MethodTransformChecker.Minimum(TransformSubtype.of(null), TransformSubtype.of(this, "consumer"), TransformSubtype.of(null)),
                new MethodTransformChecker.Minimum(TransformSubtype.of(null), TransformSubtype.of(null), TransformSubtype.of(this))
            };

            MethodParameterInfo info = new MethodParameterInfo(consumerID, TransformSubtype.of(null), argTypes, minimums, methodReplacement);
            parameterInfo.computeIfAbsent(consumerID, k -> new ArrayList<>()).add(info);
        }
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
