package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

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

public class TransformSubtype {
    //All types that are known to be regular transform subtypes
    private static final Set<Type> REGULAR_TYPES = new HashSet<>();
    //All types that are known to be consumer transform types
    private static final Set<Type> CONSUMER_TYPES = new HashSet<>();
    //All types that are known to be predicate transform types
    private static final Set<Type> PREDICATE_TYPES = new HashSet<>();

    //A reference to a TransformType. THis means when the transform type gets changed all referenced ones can get notified
    private final TransformTypePtr transformType;
    //Array dimensionality of type. So an array of longs (with type long -> (int, int, int)) would have dimensionality 1
    private int arrayDimensionality;
    //The subtype. Either NONE, CONSUMER or PREDICATE
    private SubType subtype;

    public TransformSubtype(TransformTypePtr transformType, int arrayDimensionality, SubType subtype) {
        this.transformType = transformType;
        this.arrayDimensionality = arrayDimensionality;
        this.subtype = subtype;
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
    TransformTypePtr getTransformTypePtr() {
        return transformType;
    }

    public int getArrayDimensionality() {
        return arrayDimensionality;
    }

    public SubType getSubtype() {
        return subtype;
    }

    public void setArrayDimensionality(int arrayDimensionality) {
        this.arrayDimensionality = arrayDimensionality;
    }

    public void setSubType(SubType transformSubType) {
        this.subtype = transformSubType;
    }

    /**
     * Initializes {@code REGULAR_TYPES}, {@code CONSUMER_TYPES} and {@code PREDICATE_TYPES} with
     * the transform types listed in the config
     * @param config The config to use
     */
    public static void init(Config config) {
        for (var entry : config.getTypes().entrySet()) {
            var subType = entry.getValue();
            REGULAR_TYPES.add(subType.getFrom());
            CONSUMER_TYPES.add(subType.getOriginalConsumerType());
            PREDICATE_TYPES.add(subType.getOriginalPredicateType());
        }
    }

    /**
     * @return A transform subtype for which nothing is known yet. The transform type is null, array dimensionality is 0 and
     * the subtype is NONE
     */
    public static TransformSubtype createDefault() {
        return new TransformSubtype(new TransformTypePtr(null), 0, SubType.NONE);
    }

    /**
     * Create a transform subtype from a string.
     * <br>Example: "blockpos consumer[][]" would create a transform subtype of a 2D array of blockpos consumers
     * @param s The string to load it from
     * @param transformLookup The transform types
     * @return The created transform subtype
     */
    public static TransformSubtype fromString(String s, Map<String, TransformType> transformLookup) {
        int arrIndex = s.indexOf('[');
        int arrDimensionality = 0;
        if (arrIndex != -1) {
            arrDimensionality = (s.length() - arrIndex) / 2;
            s = s.substring(0, arrIndex);
        }

        String[] parts = s.split(" ");
        SubType subType;
        TransformType transformType = transformLookup.get(parts[0]);
        if (parts.length == 1) {
            subType = SubType.NONE;
        } else {
            subType = SubType.fromString(parts[1]);
        }
        return new TransformSubtype(new TransformTypePtr(transformType), arrDimensionality, subType);
    }

    /**
     * Creates a TransformSubtype
     * @param subType The transform type of the subtype
     * @return A transform subtype with the given transform type and no array dimensionality
     */
    public static TransformSubtype of(@Nullable TransformType subType) {
        return new TransformSubtype(new TransformTypePtr(subType), 0, SubType.NONE);
    }

    /**
     * Creates a TransformSubtype
     * @param transformType The transform type of the subtype
     * @param subType The subtype as a string
     * @return A transform subtype with no array dimensionality
     */
    public static TransformSubtype of(TransformType transformType, String subType) {
        return new TransformSubtype(new TransformTypePtr(transformType), 0, SubType.fromString(subType));
    }

    /**
     * @param transform A transform type
     * @return The original type of the transform type using the subtype of this object
     */
    public Type getRawType(TransformType transform) {
        return switch (this.subtype) {
            case NONE -> transform.getFrom();
            case PREDICATE -> transform.getOriginalPredicateType();
            case CONSUMER -> transform.getOriginalConsumerType();
        };
    }

    /**
     * @param type A type
     * @return The potential subtype of the type. If unknown, returns NONE
     */
    public static SubType getSubType(Type type) {
        while (true) {
            if (REGULAR_TYPES.contains(type)) {
                return SubType.NONE;
            } else if (CONSUMER_TYPES.contains(type)) {
                return SubType.CONSUMER;
            } else if (PREDICATE_TYPES.contains(type)) {
                return SubType.PREDICATE;
            }

            if (type.getSort() != Type.ARRAY) {
                break;
            } else {
                type = type.getElementType();
            }
        }


        return SubType.NONE;
    }

    /**
     * @return The single transformed type of this transform subtype.
     * @throws IllegalStateException If this subtype is not NONE or there is not a single transformed type. To get all the types
     * use {@link #transformedTypes()}
     */
    public Type getSingleType() {
        if (subtype == SubType.NONE && transformType.getValue().getTo().length != 1) {
            throw new IllegalStateException("Cannot get single subType for " + this);
        }

        Type baseType;
        if (subtype == SubType.NONE) {
            baseType = transformType.getValue().getTo()[0];
        } else if (subtype == SubType.CONSUMER) {
            baseType = transformType.getValue().getTransformedConsumerType();
        } else {
            baseType = transformType.getValue().getTransformedPredicateType();
        }

        if (arrayDimensionality == 0) {
            return baseType;
        } else {
            return Type.getType("[".repeat(arrayDimensionality) + baseType.getDescriptor());
        }
    }

    //Does not work with array dimensionality
    /**
     * @return The list of transformed types that should replace a value with this transform subtype.
     * If the transform subtype is not known this will return an empty list.
     */
    private List<Type> transformedTypes() {
        List<Type> types = new ArrayList<>();
        if (subtype == SubType.NONE) {
            types.addAll(Arrays.asList(transformType.getValue().getTo()));
        } else if (subtype == SubType.CONSUMER) {
            types.add(transformType.getValue().getTransformedConsumerType());
        } else {
            types.add(transformType.getValue().getTransformedPredicateType());
        }

        return types;
    }

    /**
     * Similar to {@link #transformedTypes()} but returns a list of a single element.
     * @param subType The type to default to
     * @return The list of transformed types that should replace a value that has this transform subtype.
     */
    public List<Type> transformedTypes(Type subType) {
        if (transformType.getValue() == null) {
            return List.of(subType);
        }
        return transformedTypes();
    }

    /**
     * Gets the transform size (in local var slots) of a transform value with this transform subtype.
     * @return The size
     */
    public int getTransformedSize() {
        if (subtype == SubType.NONE) {
            return transformType.getValue().getTransformedSize();
        } else {
            return 1;
        }
    }

    public enum SubType {
        NONE,
        PREDICATE,
        CONSUMER;

        public static SubType fromString(String part) {
            return switch (part.toLowerCase(Locale.ROOT)) {
                case "predicate" -> PREDICATE;
                case "consumer" -> CONSUMER;
                default -> {
                    System.err.println("Unknown subtype: " + part);
                    yield NONE;
                }
            };
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformSubtype that = (TransformSubtype) o;
        return arrayDimensionality == that.arrayDimensionality && transformType.getValue() == that.transformType.getValue() && subtype == that.subtype;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformType, arrayDimensionality, subtype);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (transformType.getValue() == null) {
            if (subtype == SubType.NONE) {
                return "No transform";
            } else {
                sb.append(subtype.name().toLowerCase(Locale.ROOT));
                sb.append(" candidate");
                return sb.toString();
            }
        }

        sb.append(transformType.getValue());

        if (subtype != SubType.NONE) {
            sb.append(" ");
            sb.append(subtype.name().toLowerCase(Locale.ROOT));
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

        if (subtype == SubType.NONE) {
            return transformType.getValue().convertToTransformed(originalSupplier);
        } else if (subtype == SubType.CONSUMER || subtype == SubType.PREDICATE) {
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

            if (subtype == SubType.CONSUMER) {
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

        throw new IllegalArgumentException("Unsupported subtype: " + subtype);
    }
}
