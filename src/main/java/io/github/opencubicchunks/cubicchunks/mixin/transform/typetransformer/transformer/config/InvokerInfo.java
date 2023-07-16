package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.ArrayList;
import java.util.List;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.DerivedTransformType;
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

    public record InvokerMethodInfo(DerivedTransformType[] argTypes, String mixinMethodName, String targetMethodName, String desc) {
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
            DerivedTransformType[] newArgTypes = new DerivedTransformType[argTypes.length + 1];
            newArgTypes[0] = DerivedTransformType.createDefault(methodID.getOwner());
            System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);

            //Generate minimums
            List<MethodTransformChecker.MinimumConditions> minimumConditions = new ArrayList<>();
            Type[] args = methodID.getDescriptor().getArgumentTypes();

            for (int j = 0; j < argTypes.length; j++) {
                if (argTypes[j].getTransformType() != null) {
                    DerivedTransformType[] min = new DerivedTransformType[newArgTypes.length];

                    for (int k = 0; k < min.length; k++) {
                        if (k != j + 1) {
                            min[k] = DerivedTransformType.createDefault(args[j]);
                        } else {
                            min[k] = argTypes[j];
                        }
                    }

                    minimumConditions.add(new MethodTransformChecker.MinimumConditions(DerivedTransformType.createDefault(args[j]), min));
                }
            }

            MethodParameterInfo info = new MethodParameterInfo(
                methodID,
                DerivedTransformType.createDefault(methodID.getDescriptor().getReturnType()),
                newArgTypes,
                minimumConditions.toArray(new MethodTransformChecker.MinimumConditions[0]),
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
