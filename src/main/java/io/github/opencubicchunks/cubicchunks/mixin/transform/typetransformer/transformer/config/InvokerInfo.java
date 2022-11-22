package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.ArrayList;
import java.util.List;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class InvokerInfo {
    private final Type mixinClass;
    private final Type targetClass;
    private final List<InvokerMethodInfo> methods;

    public InvokerInfo(Type mixinClass, Type targetClass, List<InvokerMethodInfo> methods) {
        this.mixinClass = mixinClass;
        this.targetClass = targetClass;
        this.methods = methods;
    }

    public void addReplacementTo(AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo) {
        for (InvokerMethodInfo method : methods) {
            method.addReplacementTo(parameterInfo, this);
        }
    }

    public List<InvokerMethodInfo> getMethods() {
        return methods;
    }

    public record InvokerMethodInfo(TransformSubtype[] argTypes, String mixinMethodName, String targetMethodName, String desc) {
        public void addReplacementTo(AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo, InvokerInfo invokerInfo) {
            List<Type> transformedTypes = new ArrayList<>();

            for (int i = 0; i < argTypes.length; i++) {
                transformedTypes.addAll(argTypes[i].resultingTypes());
            }

            StringBuilder newDescBuilder = new StringBuilder();
            newDescBuilder.append("(");
            for (Type type : transformedTypes) {
                newDescBuilder.append(type.getDescriptor());
            }
            newDescBuilder.append(")");
            newDescBuilder.append(Type.getReturnType(desc).getDescriptor());

            String newDesc = newDescBuilder.toString();

            BytecodeFactory replacement = generateReplacement(newDesc, transformedTypes, invokerInfo);

            MethodID methodID = new MethodID(invokerInfo.mixinClass.getInternalName(), mixinMethodName, desc, MethodID.CallType.INTERFACE);

            //Generate the actual argTypes array who's first element is `this`
            TransformSubtype[] newArgTypes = new TransformSubtype[argTypes.length + 1];
            newArgTypes[0] = TransformSubtype.createDefault();
            System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);

            //Generate minimums
            List<MethodTransformChecker.Minimum> minimums = new ArrayList<>();

            for (int j = 0; j < argTypes.length; j++) {
                if (argTypes[j].getTransformType() != null) {
                    TransformSubtype[] min = new TransformSubtype[newArgTypes.length];

                    for (int k = 0; k < min.length; k++) {
                        if (k != j + 1) {
                            min[k] = TransformSubtype.createDefault();
                        } else {
                            min[k] = argTypes[j];
                        }
                    }

                    minimums.add(new MethodTransformChecker.Minimum(TransformSubtype.createDefault(), min));
                }
            }

            MethodParameterInfo info = new MethodParameterInfo(
                methodID,
                TransformSubtype.createDefault(),
                newArgTypes,
                minimums.toArray(new MethodTransformChecker.Minimum[0]),
                new MethodReplacement(replacement, newArgTypes)
            );

            parameterInfo.computeIfAbsent(methodID, k -> new ArrayList<>()).add(info);
        }

        private BytecodeFactory generateReplacement(String newDesc, List<Type> transformedTypes, InvokerInfo invokerInfo) {
            if (transformedTypes.size() == 0) {
                return (__) -> {
                    InsnList list = new InsnList();
                    list.add(new TypeInsnNode(Opcodes.CHECKCAST, invokerInfo.targetClass.getInternalName()));
                    list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, invokerInfo.targetClass.getInternalName(), targetMethodName, newDesc, false));
                    return list;
                };
            } else {
                return (varAllocator) -> {
                    InsnList insnList = new InsnList();

                    //Save the arguments in variables
                    //Step 1: Allocate vars
                    List<Integer> vars = new ArrayList<>();
                    for (Type t : transformedTypes) {
                        vars.add(varAllocator.apply(t));
                    }

                    //Step 2: Save the arguments in the allocated vars
                    for (int i = vars.size() - 1; i >= 0; i--) {
                        insnList.add(new VarInsnNode(transformedTypes.get(i).getOpcode(Opcodes.ISTORE), vars.get(i)));
                    }

                    //Cast the object to the interface
                    insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, invokerInfo.targetClass.getInternalName()));

                    //Load the arguments back
                    for (int i = 0; i < vars.size(); i++) {
                        insnList.add(new VarInsnNode(transformedTypes.get(i).getOpcode(Opcodes.ILOAD), vars.get(i)));
                    }

                    //Call the method
                    insnList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, invokerInfo.targetClass.getInternalName(), targetMethodName, newDesc, false));

                    return insnList;
                };
            }
        }
    }
}
