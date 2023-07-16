package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class DerivedTransformType {
    //A reference to a TransformType. This means when the transform type gets changed all referenced ones can get notified
    private final TransformTypeRef transformType;
    //Array dimensionality of type. So an array of longs (with type long -> (int, int, int)) would have dimensionality 1
    private int arrayDimensionality;
    //The kind. Either NONE, CONSUMER or PREDICATE
    private Kind kind;

    private final Type originalType;

    public DerivedTransformType(TransformTypeRef transformType, int arrayDimensionality, Kind kind, Type originalType) {
        this.transformType = transformType;
        this.arrayDimensionality = arrayDimensionality;
        this.kind = kind;
        this.originalType = originalType;
    }

    /**
     * Get the transform type of this object.
     * @return The transform type of this object. This may be null if the object doesn't have a transform type or
     * it has not been inferred yet.
     */
    public @Nullable TransformType getTransformType() {
        return transformType.getValue();
    }

    /**
     * For internal use only
     * @return The reference to the transform type
     */
    TransformTypeRef getTransformTypePtr() {
        return transformType;
    }

    public int getArrayDimensionality() {
        return arrayDimensionality;
    }

    public Kind getKind() {
        return kind;
    }

    public void setArrayDimensionality(int arrayDimensionality) {
        this.arrayDimensionality = arrayDimensionality;
    }

    public void setKind(Kind transformKind) {
        this.kind = transformKind;
    }

    /**
     * @return A derived transform type for which nothing is known yet. The transform type is null, array dimensionality is 0 and
     * the kind is NONE
     */
    public static DerivedTransformType createDefault(Type type) {
        return new DerivedTransformType(new TransformTypeRef(null), 0, Kind.NONE, type);
    }

    /**
     * Create a derived transform type from a string.
     * <br>Example: "blockpos consumer[][]" would create a derived type of a 2D array of blockpos consumers
     * @param s The string to load it from
     * @param transformLookup The transform types
     * @return The parsed derived type
     */
    public static DerivedTransformType fromString(String s, Map<String, TransformType> transformLookup) {
        int arrIndex = s.indexOf('[');
        int arrDimensionality = 0;
        if (arrIndex != -1) {
            arrDimensionality = (s.length() - arrIndex) / 2;
            s = s.substring(0, arrIndex);
        }

        String[] parts = s.split(" ");
        Kind kind;
        TransformType transformType = transformLookup.get(parts[0]);
        if (parts.length == 1) {
            kind = Kind.NONE;
        } else {
            kind = Kind.fromString(parts[1]);
        }

        return new DerivedTransformType(new TransformTypeRef(transformType), arrDimensionality, kind, getRawType(transformType, kind));
    }

    /**
     * Creates a DerivedTransformType
     * @param transformType The transform type of the derived type
     * @return A derived type with the given transform type and no array dimensionality
     */
    public static DerivedTransformType of(@Nullable TransformType transformType) {
        return new DerivedTransformType(new TransformTypeRef(transformType), 0, Kind.NONE, getRawType(transformType, Kind.NONE));
    }

    /**
     * Creates a DerivedTransformType
     * @return A derived type with no array dimensionality
     */
    public static DerivedTransformType of(TransformType transformType, Kind kind) {
        return new DerivedTransformType(new TransformTypeRef(transformType), 0, kind, getRawType(transformType, kind));
    }

    /**
     * @param transform A transform type
     * @return The original type of a value with the given transform type and the kind of this derived type
     */
    public Type getRawType(TransformType transform) {
        return getRawType(transform, this.kind);
    }

    private static Type getRawType(TransformType transform, Kind kind) {
        return switch (kind) {
            case NONE -> transform.getFrom();
            case PREDICATE -> transform.getOriginalPredicateType();
            case CONSUMER -> transform.getOriginalConsumerType();
        };
    }

    /**
     * @param type A type
     * @return The potential kind of the type. If unknown, returns NONE
     */
    public static Kind getKindFor(@Nullable Type type, Config config) {
        while (type != null) {
            if (type.getSort() == Type.OBJECT) {
                for (var t: config.getTypeInfo().ancestry(type)) {
                    if (config.getRegularTypes().contains(t)) {
                        return Kind.NONE;
                    } else if (config.getConsumerTypes().contains(t)) {
                        return Kind.CONSUMER;
                    } else if (config.getPredicateTypes().contains(t)) {
                        return Kind.PREDICATE;
                    }
                }
            } else {
                if (config.getRegularTypes().contains(type)) {
                    return Kind.NONE;
                } else if (config.getConsumerTypes().contains(type)) {
                    return Kind.CONSUMER;
                } else if (config.getPredicateTypes().contains(type)) {
                    return Kind.PREDICATE;
                }
            }

            if (type.getSort() != Type.ARRAY) {
                break;
            } else {
                type = type.getElementType();
            }
        }


        return Kind.NONE;
    }

    /**
     * @return Same as {@link #resultingTypes} but only returns the first element.
     * @throws IllegalStateException If {@link #resultingTypes()} returns more than one element
     * use {@link #resultingTypes()}
     */
    public Type getSingleType() {
        List<Type> allTypes = this.resultingTypes();

        if (allTypes.size() != 1) {
            throw new IllegalStateException("Cannot get single type of a transform type with multiple types");
        }

        return allTypes.get(0);
    }

    /**
     * @return The list of types that should replace a value with this derived type.
     * If this represents a value that does not need to be transformed, it returns a singleton list with the original type.
     */
    public List<Type> resultingTypes() {
        if (transformType.getValue() == null) {
            if (this.originalType != null) {
                return List.of(this.originalType);
            } else {
                return List.of(Type.VOID_TYPE);
            }
        }

        List<Type> types = new ArrayList<>();
        if (kind == Kind.NONE) {
            types.addAll(Arrays.asList(transformType.getValue().getTo()));
        } else if (kind == Kind.CONSUMER) {
            types.add(transformType.getValue().getTransformedConsumerType());
        } else {
            types.add(transformType.getValue().getTransformedPredicateType());
        }

        if (arrayDimensionality != 0) {
            types = types.stream().map(t -> Type.getType("[".repeat(arrayDimensionality) + t.getDescriptor())).collect(Collectors.toList());
        }

        return types;
    }

    public int[] getIndices() {
        List<Type> types = resultingTypes();
        int[] indices = new int[types.size()];

        for (int i = 1; i < indices.length; i++) {
            indices[i] = indices[i - 1] + types.get(i - 1).getSize();
        }

        return indices;
    }

    /**
     * Gets the transform size (in local var slots) of a transform value with this derived type.
     * @return The size
     */
    public int getTransformedSize() {
        if (transformType.getValue() == null) {
            return Objects.requireNonNull(this.originalType).getSize();
        }

        if (kind == Kind.NONE && this.arrayDimensionality == 0) {
            return transformType.getValue().getTransformedSize();
        } else if (this.arrayDimensionality != 0) {
            return this.resultingTypes().size();
        } else {
            return 1;
        }
    }

    public enum Kind {
        NONE,
        PREDICATE,
        CONSUMER;

        public static Kind fromString(String part) {
            return switch (part.toLowerCase(Locale.ROOT)) {
                case "predicate" -> PREDICATE;
                case "consumer" -> CONSUMER;
                default -> {
                    System.err.println("Unknown kind: " + part);
                    yield NONE;
                }
            };
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DerivedTransformType that = (DerivedTransformType) o;
        return arrayDimensionality == that.arrayDimensionality && transformType.getValue() == that.transformType.getValue() && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformType, arrayDimensionality, kind);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (transformType.getValue() == null) {
            if (kind == Kind.NONE) {
                return "No transform";
            } else {
                sb.append(kind.name().toLowerCase(Locale.ROOT));
                sb.append(" candidate");
                return sb.toString();
            }
        }

        sb.append(transformType.getValue());

        if (kind != Kind.NONE) {
            sb.append(" ");
            sb.append(kind.name().toLowerCase(Locale.ROOT));
        }

        if (arrayDimensionality > 0) {
            for (int i = 0; i < arrayDimensionality; i++) {
                sb.append("[]");
            }
        }

        return sb.toString();
    }

    /**
     * Converts a value into the transformed value
     *
     * @param originalSupplier The supplier of the original value
     * @param transformers A set. This should be unique per-class.
     * @param className The name of the class being transformed.
     *
     * @return The transformed value
     */
    public InsnList convertToTransformed(Supplier<InsnList> originalSupplier, Set<MethodNode> transformers, String className) {
        if (transformType.getValue() == null) {
            //No transform needed
            return originalSupplier.get();
        }

        if (arrayDimensionality != 0) {
            throw new IllegalStateException("Not supported yet");
        }

        if (kind == Kind.NONE) {
            return transformType.getValue().convertToTransformed(originalSupplier);
        } else if (kind == Kind.CONSUMER || kind == Kind.PREDICATE) {
            /*
             * Example:
             *   LongConsumer c = ...;
             * Becomes:
             *   Int3Consumer c1 = (x, y, z) -> c.accept(BlockPos.asLong(x, y, z));
             * We need to create a new lambda which turns the takes transformed values, turns them into the original values, and then calls the original lambda.
             */

            InsnList list = new InsnList();
            list.add(originalSupplier.get());

            Type returnType;
            String transformerType;
            String methodName;

            Type originalLambdaType;
            Type transformedLambdaType;

            if (kind == Kind.CONSUMER) {
                returnType = Type.VOID_TYPE;
                transformerType = "consumer";
                methodName = "accept";
                originalLambdaType = transformType.getValue().getOriginalConsumerType();
                transformedLambdaType = transformType.getValue().getTransformedConsumerType();
            } else {
                returnType = Type.BOOLEAN_TYPE;
                transformerType = "predicate";
                methodName = "test";
                originalLambdaType = transformType.getValue().getOriginalPredicateType();
                transformedLambdaType = transformType.getValue().getTransformedPredicateType();
            }

            String returnDescriptor = returnType.getDescriptor();

            //Name that is unique per-type for easy lookup
            String transformerName = "lambdaTransformer_" + transformerType + "_" + transformType.getValue().getName();

            //Find if the transformer has already been created
            MethodNode transformer = null;
            for (MethodNode mn : transformers) {
                if (mn.name.equals(transformerName)) {
                    transformer = mn;
                }
            }

            if (transformer == null) {
                /*
                 * This is the lambda method that the call will get passed to. For the above example this would be:
                 * private static void lambdaTransformer_consumer_blockpos(LongConsumer c, int x, int y, int z){
                 *     c.accept(BlockPos.asLong(x, y, z));
                 * }
                 */
                int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC; //Remove synthetic flag to make it easier to debug

                //Create the new descriptor for the lambda
                StringBuilder descBuilder = new StringBuilder();
                descBuilder.append("(");
                descBuilder.append(originalLambdaType.getDescriptor());
                for (Type t : transformType.getValue().getTo()) {
                    descBuilder.append(t.getDescriptor());
                }
                descBuilder.append(")");
                descBuilder.append(returnDescriptor);

                transformer = new MethodNode(Opcodes.ASM9, access, transformerName, descBuilder.toString(), null, null);

                InsnList l = new InsnList();
                //Load original consumer
                l.add(new VarInsnNode(Opcodes.ALOAD, 0));

                //Load transformed values
                int index = 1;
                for (Type t : transformType.getValue().getTo()) {
                    l.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), index));
                    index += t.getSize();
                }

                //Transform the transformed values back into the original values
                l.add(transformType.getValue().getToOriginal().callNode());

                //Call original
                String newDesc = Type.getMethodDescriptor(returnType, transformType.getValue().getFrom());
                l.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, originalLambdaType.getInternalName(), methodName, newDesc, true));
                l.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));

                //Actually insert the code
                transformer.instructions.add(l);

                //Add the lambda to the transformers
                transformers.add(transformer);
            }

            //Create the actual MethodHandle
            Handle transformerHandle = new Handle(Opcodes.H_INVOKESTATIC, className, transformer.name, transformer.desc, false);

            list.add(new InvokeDynamicInsnNode(
                methodName,
                "(" + originalLambdaType.getDescriptor() + ")" + transformedLambdaType.getDescriptor(),
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    "java/lang/invoke/LambdaMetafactory",
                    "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                        + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                    false
                ),
                Type.getMethodType(returnType, transformType.getValue().getTo()),
                transformerHandle,
                Type.getMethodType(returnType, transformType.getValue().getTo())
            ));

            return list;
        }

        throw new IllegalArgumentException("Unsupported kind: " + kind);
    }
}
