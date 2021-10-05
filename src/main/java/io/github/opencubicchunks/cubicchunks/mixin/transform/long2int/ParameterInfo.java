package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.datafixers.util.Pair;

public record ParameterInfo(int numSlots, boolean isPrimitive, char primitiveType, String objectType) {
    public static ParameterInfo BOOLEAN = new ParameterInfo(1, true, 'B', null);
    public static ParameterInfo CHAR = new ParameterInfo(1, true, 'C', null);
    public static ParameterInfo DOUBLE = new ParameterInfo(2, true, 'D', null);
    public static ParameterInfo FLOAT = new ParameterInfo(1, true, 'F', null);
    public static ParameterInfo INT = new ParameterInfo(1, true, 'I', null);
    public static ParameterInfo LONG = new ParameterInfo(2, true, 'J', null);
    public static ParameterInfo BYTE = new ParameterInfo(1, true, 'Z', null);
    public static ParameterInfo VOID = new ParameterInfo(0, true, 'V', null);
    public static ParameterInfo SHORT = new ParameterInfo(1, true, 'S', null);

    public String toDescriptorType(){
        if(isPrimitive){
            return String.valueOf(primitiveType);
        }else{
            return objectType + ";";
        }
    }

    @Override
    public String toString() {
        if(isPrimitive){
            return switch (primitiveType) {
                case 'B' -> "boolean";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'V' -> "void";
                case 'Z' -> "byte";
                default -> "[ERROR TYPE]";
            };
        }else{
            return objectType;
        }
    }

    /**
     * Parses a method descriptor into {@code ParameterInfo}
     * @param methodDescriptor The descriptor of the method
     * @return A pair. The first element is a list of parameters and the second is the return value
     */
    public static Pair<List<ParameterInfo>, ParameterInfo> parseDescriptor(String methodDescriptor){
        List<ParameterInfo> parameters = new ArrayList<>();
        ParameterInfo returnType = null;

        int startIndex = 1;
        try {
            while (methodDescriptor.charAt(startIndex) != ')'){
                var result = parseParameterAtIndex(methodDescriptor, startIndex);
                startIndex = result.getSecond();
                parameters.add(result.getFirst());
            }

            returnType = parseParameterAtIndex(methodDescriptor, startIndex + 1).getFirst();
        }catch (IndexOutOfBoundsException e){
            throw new IllegalStateException("Invalid descriptor: '" + methodDescriptor + "'");
        }

        return new Pair<>(parameters, returnType);
    }

    private static Pair<ParameterInfo, Integer> parseParameterAtIndex(String methodDescriptor, int index){
        ParameterInfo parameter = switch (methodDescriptor.charAt(index)){
            case 'B' -> BOOLEAN;
            case 'C' -> CHAR;
            case 'D' -> DOUBLE;
            case 'F' -> FLOAT;
            case 'I' -> INT;
            case 'J' -> LONG;
            case 'S' -> SHORT;
            case 'Z' -> BYTE;
            case 'V' -> VOID;
            default -> {
                int currentIndex = index;
                while(methodDescriptor.charAt(currentIndex) != ';'){
                    currentIndex++;
                }
                int tempIndex = index;
                index = currentIndex;
                yield new ParameterInfo(1, false, 'L', methodDescriptor.substring(tempIndex, currentIndex)); }
        };

        return new Pair<>(parameter, index + 1);
    }

    public static String writeDescriptor(Collection<ParameterInfo> parameters, ParameterInfo returnType){
        StringBuilder descriptor = new StringBuilder("(");
        for(ParameterInfo parameter : parameters){
            descriptor.append(parameter.toDescriptorType());
        }
        descriptor.append(")");
        descriptor.append(returnType.toDescriptorType());

        return descriptor.toString();
    }
}
