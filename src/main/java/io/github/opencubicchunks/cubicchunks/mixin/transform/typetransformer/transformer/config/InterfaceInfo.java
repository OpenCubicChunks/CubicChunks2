package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.DynamicGraphMinFixedPointAccess;
import io.github.opencubicchunks.cubicchunks.mixin.transform.CustomClassAdder;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.AnalysisResults;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import net.minecraft.world.Container;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Mixin does not allow us to mixin interfaces. This means that we cannot pass an interface to the transformer. Therefore, we
 * generate copies of these interfaces at runtime.
 */
public class InterfaceInfo {
    private static final int CLASS_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT; //0x601
    private static final int METHOD_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT; //0x401

    private static final Set<String> usedNames = new HashSet<>();

    private final Type interfaceType;
    private final String transformedInterfaceName;

    private final List<Method> methods;
    private final List<TransformSubtype[]> argTypes;

    public InterfaceInfo(Type interfaceType, List<Method> methods, List<TransformSubtype[]> methodTypes) {
        this.interfaceType = interfaceType;
        this.methods = methods;
        this.argTypes = methodTypes;

        //Generate a unique name for the interface
        String transformedInterfaceBaseName = interfaceType.getInternalName();
        transformedInterfaceBaseName = transformedInterfaceBaseName.substring(transformedInterfaceBaseName.lastIndexOf('/') + 1);

        synchronized(usedNames) {
            if (usedNames.contains(transformedInterfaceBaseName)) {
                int i = 1;
                while (usedNames.contains(transformedInterfaceBaseName + i)) {
                    i++;
                }
                transformedInterfaceBaseName += i;
            }
            usedNames.add(transformedInterfaceBaseName);
        }

        transformedInterfaceName = "io/github/opencubicchunks/cubicchunks/runtimeclasses/" + transformedInterfaceBaseName + "_synth";

        CustomClassAdder.addCustomClass(transformedInterfaceName, this::generate);
    }

    private byte[] generate() {
        ClassNode classNode = new ClassNode();

        classNode.visit(60, CLASS_ACCESS, transformedInterfaceName, null, "java/lang/Object", null);

        for (int i = 0; i < methods.size(); i++) {
            TransformSubtype[] argTypes = this.argTypes.get(i);

            if(argTypes == null){
                throw new IllegalStateException("No argument types for method " + methods.get(i));
            }

            //Create descriptor
            Type[] originalTypes = Type.getArgumentTypes(methods.get(i).getDescriptor());

            StringBuilder desc = new StringBuilder();
            desc.append("(");

            for (int j = 0; j < argTypes.length; j++) {
                for(Type t: argTypes[j].transformedTypes(originalTypes[j])){
                    desc.append(t.getDescriptor());
                }
            }

            desc.append(")");
            desc.append(Type.getReturnType(methods.get(i).getDescriptor()).getDescriptor());

            MethodNode methodNode = new MethodNode(METHOD_ACCESS, methods.get(i).getName(), desc.toString(), null, null);

            classNode.methods.add(methodNode);
        }

        ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);

        return classWriter.toByteArray();
    }

    public void tryApplyTo(ClassNode classNode) {
        //Check if the class implements the interface
        boolean apply = false;

        for(String interfaceName : classNode.interfaces){
            if(interfaceName.equals(interfaceType.getInternalName())){
                //The class implements the interface, so we can apply it
                apply = true;
                break;
            }
        }

        if(apply){
            applyTo(classNode);
        }
    }

    public void addTransformsTo(Map<MethodID, List<MethodParameterInfo>> parameterInfo) {
        for (int i = 0; i < methods.size(); i++) {
            Method method = methods.get(i);
            TransformSubtype[] argTypes = this.argTypes.get(i);
            Type[] originalTypes = Type.getArgumentTypes(method.getDescriptor());
            List<Type> transformedTypes = new ArrayList<>();

            for(int j = 0; j < argTypes.length; j++){
                transformedTypes.addAll(argTypes[j].transformedTypes(originalTypes[j]));
            }

            StringBuilder newDescBuilder = new StringBuilder();
            newDescBuilder.append("(");
            for(Type t: transformedTypes){
                newDescBuilder.append(t.getDescriptor());
            }
            newDescBuilder.append(")");
            newDescBuilder.append(Type.getReturnType(method.getDescriptor()).getDescriptor());

            String newDesc = newDescBuilder.toString();

            String nameToCall = method.getName();
            if(newDesc.equals(method.getDescriptor())){
                nameToCall += TypeTransformer.MIX;
            }

            BytecodeFactory replacement = generateReplacement(method, nameToCall, newDesc, transformedTypes);

            MethodID methodID = new MethodID(interfaceType.getInternalName(), method.getName(), method.getDescriptor(), MethodID.CallType.INTERFACE);

            //Generate the actual argTypes array who's first element is `this`
            TransformSubtype[] newArgTypes = new TransformSubtype[argTypes.length + 1];
            newArgTypes[0] = TransformSubtype.of(null);
            System.arraycopy(argTypes, 0, newArgTypes, 1, argTypes.length);

            //Generate minimums
            List<MethodTransformChecker.Minimum> minimums = new ArrayList<>();

            for(int j = 0; j < argTypes.length; j++){
                if(argTypes[j].getTransformType() != null){
                    TransformSubtype[] min = new TransformSubtype[newArgTypes.length];

                    for(int k = 0; k < min.length; k++){
                        if(k != j + 1){
                            min[k] = TransformSubtype.of(null);
                        }else{
                            min[k] = argTypes[j];
                        }
                    }

                    minimums.add(new MethodTransformChecker.Minimum(TransformSubtype.of(null), min));
                }
            }

            MethodParameterInfo info = new MethodParameterInfo(
                methodID,
                TransformSubtype.of(null),
                newArgTypes,
                minimums.toArray(new MethodTransformChecker.Minimum[0]),
                new MethodReplacement(replacement)
            );

            parameterInfo.computeIfAbsent(methodID, k -> new ArrayList<>()).add(info);
        }
    }

    private BytecodeFactory generateReplacement(Method originalMethod, String nameToCall, String newDesc, List<Type> transformedTypes) {
        if(transformedTypes.size() == 0){
            return (__) -> {
                InsnList insnList = new InsnList();
                insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, transformedInterfaceName));
                insnList.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, transformedInterfaceName, nameToCall, originalMethod.getDescriptor(), true));
                return insnList;
            };
        }else{
            return (varAllocator) -> {
                InsnList insnList = new InsnList();

                //Save the arguments in variables
                //Step 1: Allocate vars
                List<Integer> vars = new ArrayList<>();
                for(Type t: transformedTypes){
                    vars.add(varAllocator.apply(t));
                }

                //Step 2: Save the arguments in the allocated vars
                for (int i = vars.size() - 1; i >= 0; i--) {
                    insnList.add(new VarInsnNode(transformedTypes.get(i).getOpcode(Opcodes.ISTORE), vars.get(i)));
                }

                //Cast the object to the interface
                insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, transformedInterfaceName));

                //Load the arguments back
                for(int i = 0; i < vars.size(); i++){
                    insnList.add(new VarInsnNode(transformedTypes.get(i).getOpcode(Opcodes.ILOAD), vars.get(i)));
                }

                //Call the method
                insnList.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, transformedInterfaceName, nameToCall, newDesc, true));

                return insnList;
            };
        }
    }

    private void applyTo(ClassNode classNode) {
        //Add the interface
        classNode.interfaces.add(transformedInterfaceName);
    }

    private static TransformSubtype[] getArgTypesFor(Method method, ClassNode classNode, TypeTransformer transformer){
        Map<MethodID, AnalysisResults> analysisResultsMap = transformer.getAnalysisResults();
        AnalysisResults analysisResults = null;

        for(Map.Entry<MethodID, AnalysisResults> entry : analysisResultsMap.entrySet()){
            MethodID id = entry.getKey();
            if(id.getName().equals(method.getName()) && id.getDescriptor().getDescriptor().equals(method.getDescriptor())){
                analysisResults = entry.getValue();
                break;
            }
        }

        if(analysisResults == null){
            return null;
        }

        //The arg types include the 'this' argument so we remove it
        TransformSubtype[] argTypes = analysisResults.argTypes();
        TransformSubtype[] newArgTypes = new TransformSubtype[argTypes.length - 1];
        System.arraycopy(argTypes, 1, newArgTypes, 0, newArgTypes.length);

        return newArgTypes;
    }
}
