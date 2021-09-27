package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns.BytecodePattern;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns.Patterns;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class LongPosTransformer {
    private static final String REMAP_PATH = "/remaps.json";
    private static final Map<String, ClassTransformationInfo> transforms = new HashMap<>();
    private static boolean loaded = false;

    public static void transform(ClassNode classNode){
        if(!loaded){
            try {
                loadTransformInfo();
            }catch (IOException e){
                throw new IllegalStateException("Failed to load remapping info", e);
            }
            loaded = true;
        }

        ClassTransformationInfo transformsToApply = transforms.get(classNode.name);
        if(transformsToApply == null) return;

        List<MethodNode> newMethods = new ArrayList<>();
        for(MethodNode method : classNode.methods){
            MethodTransformationInfo transform = transformsToApply.getMethod(method.name, method.desc);
            if(transform != null) {
                MethodNode newMethod = transformMethod(method, transform);

                if(transform.copy){
                    newMethods.add(newMethod);
                }
            }
        }

        classNode.methods.addAll(newMethods);
    }

    private static MethodNode transformMethod(MethodNode method, MethodTransformationInfo transform) {
        MethodNode newMethod = transform.copy ? copy(method) : method;

        newMethod.desc = modifyDescriptor(method, transform.expandedVariables);

        LocalVariableMapper variableMapper = new LocalVariableMapper();
        for(int expandedVariable : transform.expandedVariables){
            variableMapper.addTransformedVariable(expandedVariable);
        }

        newMethod.instructions = modifyCode(newMethod.instructions, variableMapper, transform);

        newMethod.localVariables = null;
        newMethod.parameters = null;

        return newMethod;
    }

    private static InsnList modifyCode(InsnList instructions, LocalVariableMapper variableMapper, MethodTransformationInfo transform) {
        for(AbstractInsnNode instruction : instructions){
            if(instruction instanceof FrameNode){
                instructions.remove(instruction);
            }
        }

        remapLocalVariables(instructions, variableMapper);
        applyPatterns(instructions, variableMapper, transform);

        return instructions;
    }

    private static void applyPatterns(InsnList instructions, LocalVariableMapper variableMapper, MethodTransformationInfo transform) {
        //Resolve pattern names
        List<BytecodePattern> patterns = new ArrayList<>();
        for(String patternName : transform.patterns){
            BytecodePattern pattern = Patterns.getPattern(patternName, transform.remappedMethods);
            if(pattern != null) patterns.add(pattern);
        }

        int currentIndex = 0;

        while (currentIndex < instructions.size()){
            for(BytecodePattern pattern: patterns){
                if(pattern.apply(instructions, variableMapper, currentIndex)){
                    break;
                }
            }
            currentIndex++;
        }
    }

    private static void remapLocalVariables(InsnList instructions, LocalVariableMapper variableMapper) {
        for(AbstractInsnNode instruction: instructions){
            if(instruction instanceof VarInsnNode localVarInstruction){
                localVarInstruction.var = variableMapper.mapLocalVariable(localVarInstruction.var);
            }else if(instruction instanceof IincInsnNode incrementInstruction){
                incrementInstruction.var = variableMapper.mapLocalVariable((incrementInstruction).var);
            }
        }
    }

    private static String modifyDescriptor(MethodNode method, List<Integer> expandedVariables) {
        var descriptor = ParameterInfo.parseDescriptor(method.desc);

        List<ParameterInfo> newParameters = new ArrayList<>();

        int originalLocalVariableOffset = 0;

        if((method.access & Opcodes.ACC_STATIC) == 0){
            originalLocalVariableOffset++;
        }

        for(ParameterInfo originalParameter : descriptor.getFirst()){
            if (originalParameter == ParameterInfo.LONG && expandedVariables.contains(originalLocalVariableOffset)) {
                originalLocalVariableOffset += 2;
                for(int i = 0; i < 3; i++)
                    newParameters.add(ParameterInfo.INT);
            }else{
                originalLocalVariableOffset += originalParameter.numSlots();
                newParameters.add(originalParameter);
            }
        }

        System.out.println("Modified Parameters:");
        for(ParameterInfo parameter: newParameters){
            System.out.println("\t" + parameter);
        }

        return ParameterInfo.writeDescriptor(newParameters, descriptor.getSecond());
    }

    private static MethodNode copy(MethodNode method) {
        ClassNode classNode = new ClassNode();
        //MethodNode other = new MethodNode();
        method.accept(classNode);
        return classNode.methods.get(0);
    }

    private static void loadTransformInfo() throws IOException {
        JsonParser parser = new JsonParser();

        String stringData = new String(LongPosTransformer.class.getResourceAsStream(REMAP_PATH).readAllBytes(), StandardCharsets.UTF_8);
        JsonElement root = parser.parse(stringData);

        JsonObject data = root.getAsJsonObject();

        data.entrySet().forEach((entry) -> {
            ClassTransformationInfo classInfo = new ClassTransformationInfo(entry.getKey());
            entry.getValue().getAsJsonObject().get("methods").getAsJsonArray().forEach((method) -> {
                classInfo.addMethod(new MethodTransformationInfo(method));
            });
            transforms.put(entry.getKey(), classInfo);
        });
    }

    public static class ClassTransformationInfo{
        private final Map<String, MethodTransformationInfo> methods = new HashMap<>();
        private final String name;

        public ClassTransformationInfo(String name){
            this.name = name;
        }

        public void addMethod(MethodTransformationInfo method){
            methods.put(method.name + " " + method.desc, method);
        }

        public MethodTransformationInfo getMethod(String name, String descriptor){
            return methods.get(name + " " + descriptor);
        }
    }

    public static class MethodTransformationInfo{
        private final String name; //The methods name
        private final String desc; //The methods descriptor
        private final List<Integer> expandedVariables = new ArrayList<>(); //Indices into the local variable array that point to longs that should be transformed into triple ints
        private final List<String> patterns = new ArrayList<>(); //Patterns that should be used
        private final Map<String, String> remappedMethods = new HashMap<>(); //Methods which will be assumed to have been transformed so as to use a certain descriptor
        private final boolean copy;

        public MethodTransformationInfo(JsonElement jsonElement){
            JsonObject methodInfo = jsonElement.getAsJsonObject();
            name = methodInfo.get("name").getAsString();
            desc = methodInfo.get("descriptor").getAsString();

            methodInfo.get("expanded_variables").getAsJsonArray().forEach((e) -> expandedVariables.add(e.getAsInt()));
            methodInfo.get("patterns").getAsJsonArray().forEach((e) -> patterns.add(e.getAsString()));
            methodInfo.get("transformed_methods").getAsJsonObject().entrySet().forEach((entry) -> remappedMethods.put(entry.getKey(), entry.getValue().getAsString()));

            boolean copy = true;
            JsonElement copyElement = methodInfo.get("copy");
            if(copyElement != null){
                copy = copyElement.getAsBoolean();
            }

            this.copy = copy;
        }
    }
}
