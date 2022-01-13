package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.List;
import java.util.Stack;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class MethodParameterInfo{
    private final MethodID method;
    private final TransformSubtype returnType;
    private final TransformSubtype[] parameterTypes;
    private final MethodTransformChecker transformCondition;
    private final MethodReplacement replacement;

    public MethodParameterInfo(MethodID method, @NotNull TransformSubtype returnType, @NotNull TransformSubtype[] parameterTypes, MethodTransformChecker.Minimum[] minimums,
                               MethodReplacement replacement) {
        this.method = method;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
        this.transformCondition = new MethodTransformChecker(this, minimums);
        this.replacement = replacement;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if(returnType.getTransformType() == null){
            sb.append(getOnlyName(method.getDescriptor().getReturnType()));
        }else{
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
        for(int i = 0; i < parameterTypes.length; i++){
            TransformType type = parameterTypes[i].getTransformType();
            if(type != null){
                sb.append('[');
                sb.append(type.getName());
                sb.append(']');
            }else{
                sb.append(getOnlyName(types[i]));
            }
            if(i != parameterTypes.length - 1){
                sb.append(", ");
            }
        }

        sb.append(")");
        return sb.toString();
    }

    private static String getOnlyName(Type type){
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public MethodID getMethod() {
        return method;
    }

    public TransformSubtype getReturnType() {
        return returnType;
    }

    public TransformSubtype[] getParameterTypes() {
        return parameterTypes;
    }

    public MethodTransformChecker getTransformCondition() {
        return transformCondition;
    }

    public MethodReplacement getReplacement() {
        return replacement;
    }

    public static String getNewDesc(TransformSubtype returnType, TransformSubtype[] parameterTypes, String originalDesc){
        Type[] types = Type.getArgumentTypes(originalDesc);
        StringBuilder sb = new StringBuilder("(");
        for(int i = 0; i < parameterTypes.length; i++){
            if(parameterTypes[i] != null && parameterTypes[i].getTransformType() != null){
                for(Type type : parameterTypes[i].transformedTypes(Type.VOID_TYPE /*This doesn't matter because we know it won't be used because getTransformType() != null*/)){
                    sb.append(type.getDescriptor());
                }
            }else{
                sb.append(types[i].getDescriptor());
            }
        }
        sb.append(")");
        if(returnType != null && returnType.getTransformType() != null){
            if(returnType.transformedTypes(Type.VOID_TYPE).size() != 1){
                throw new IllegalArgumentException("Return type must have exactly one transform type");
            }
            sb.append(returnType.transformedTypes(Type.VOID_TYPE).get(0).getDescriptor());
        }else{
            sb.append(Type.getReturnType(originalDesc).getDescriptor());
        }
        return sb.toString();
    }

    public static String getNewDesc(TransformTrackingValue returnValue, TransformTrackingValue[] parameters, String originalDesc){
        TransformSubtype returnType = returnValue.getTransform();
        TransformSubtype[] parameterTypes = new TransformSubtype[parameters.length];
        for(int i = 0; i < parameters.length; i++){
            parameterTypes[i] = parameters[i].getTransform();
        }

        return getNewDesc(returnType, parameterTypes, originalDesc);
    }
}
