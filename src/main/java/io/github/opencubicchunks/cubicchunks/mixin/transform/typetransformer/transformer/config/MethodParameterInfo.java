package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.DerivedTransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

public class MethodParameterInfo {
    private final MethodID method;
    private final DerivedTransformType returnType;
    private final DerivedTransformType[] parameterTypes;
    private final MethodTransformChecker transformCondition;
    private final @Nullable MethodReplacement replacement;

    public MethodParameterInfo(
        MethodID method, @NotNull DerivedTransformType returnType, @NotNull DerivedTransformType[] parameterTypes,
        MethodTransformChecker.MinimumConditions[] minimumConditions, @Nullable MethodReplacement replacement
    ) {
        this.method = method;
        this.returnType = returnType;
        this.transformCondition = new MethodTransformChecker(this, minimumConditions);
        this.replacement = replacement;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (returnType.getTransformType() == null) {
            sb.append(getOnlyName(method.getDescriptor().getReturnType()));
        } else {
            sb.append('[');
            sb.append(returnType.getTransformType().getName());
            sb.append(']');
        }

        Type[] types = method.getDescriptor().getArgumentTypes();

        sb.append(" ");
        sb.append(getOnlyName(method.getOwner()));
        sb.append("#");
        sb.append(method.getName());
        sb.append("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            TransformType type = parameterTypes[i].getTransformType();
            if (type != null) {
                sb.append('[');
                sb.append(type.getName());
                sb.append(']');
            } else {
                sb.append(getOnlyName(types[i]));
            }
            if (i != parameterTypes.length - 1) {
                sb.append(", ");
            }
        }

        sb.append(")");
        return sb.toString();
    }

    private static String getOnlyName(Type type) {
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public MethodID getMethod() {
        return method;
    }

    public @Nullable DerivedTransformType getReturnType() {
        return returnType;
    }

    public DerivedTransformType[] getParameterTypes() {
        return parameterTypes;
    }

    public MethodTransformChecker getTransformCondition() {
        return transformCondition;
    }

    public @Nullable MethodReplacement getReplacement() {
        return replacement;
    }

    public static String getNewDesc(DerivedTransformType returnType, DerivedTransformType[] parameterTypes, String originalDesc) {
        Type[] types = Type.getArgumentTypes(originalDesc);
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] != null && parameterTypes[i].getTransformType() != null) {
                for (Type type : parameterTypes[i].resultingTypes()) {
                    sb.append(type.getDescriptor());
                }
            } else {
                sb.append(types[i].getDescriptor());
            }
        }
        sb.append(")");
        if (returnType != null && returnType.getTransformType() != null) {
            if (returnType.resultingTypes().size() != 1) {
                throw new IllegalArgumentException("Return type must have exactly one transform type");
            }
            sb.append(returnType.resultingTypes().get(0).getDescriptor());
        } else {
            sb.append(Type.getReturnType(originalDesc).getDescriptor());
        }
        return sb.toString();
    }

    public static String getNewDesc(TransformTrackingValue returnValue, TransformTrackingValue[] parameters, String originalDesc) {
        DerivedTransformType returnType = returnValue.getTransform();
        DerivedTransformType[] parameterTypes = new DerivedTransformType[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            parameterTypes[i] = parameters[i].getTransform();
        }

        return getNewDesc(returnType, parameterTypes, originalDesc);
    }
}
