package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns.BlockPosOffsetPattern;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns.BytecodePattern;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns.CheckInvalidPosPattern;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.patterns.PackedInequalityPattern;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;

public class LongPosTransformer {
    private static final String REMAP_PATH = "/remaps.json";
    private static final Map<String, MethodInfo> methodInfoLookup = new HashMap<>();
    private static final Map<String, List<String>> transformsToApply = new HashMap<>();
    private static final String[] UNPACKING_METHODS = new String[3];
    private static boolean loaded = false;

    private static final LightEngineInterpreter interpreter = new LightEngineInterpreter(Opcodes.ASM9);
    private static final Analyzer<LightEngineValue> analyzer = new Analyzer<>(interpreter);

    private static String BLOCK_POS_AS_LONG_METHOD;
    private static String VEC3I_CLASS_NAME;
    private static final String[] BLOCKPOS_VIRTUAL_GET = new String[3];

    public static Set<String> remappedMethods = new HashSet<>();

    public static void modifyClass(ClassNode classNode){
        List<String> transforms = transformsToApply.get(classNode.name);

        List<MethodNode> newMethods = new ArrayList<>();

        for(MethodNode methodNode : classNode.methods){
            String methodNameAndDescriptor = methodNode.name + " " + methodNode.desc;
            if(transforms.contains(methodNameAndDescriptor)){
                interpreter.clearCache();
                try {
                    analyzer.analyze(classNode.name, methodNode);
                }catch (AnalyzerException e){
                    throw new IllegalArgumentException("Could not modify method " + methodNameAndDescriptor + " in class " + classNode.name + ". Analyzer failed", e);
                }

                Frame<LightEngineValue>[] frames = analyzer.getFrames();
                AbstractInsnNode[] instructions = methodNode.instructions.toArray();
                Map<AbstractInsnNode, Integer> instructionIndexMap = new HashMap<>(instructions.length);

                //Create a map to easily get the index of an instruction
                for(int i = 0; i < instructions.length; i++){
                    instructionIndexMap.put(instructions[i], i);
                }

                //TODO: This information could very easily be contained within CCValue itself
                Map<LightEngineValue, Set<Integer>> consumers = mapConsumerCache(interpreter.getConsumers(), instructionIndexMap);

                Set<Integer> expandedVariables = getExpandedVariables(frames, instructions, consumers);

                MethodNode newMethod = modifyMethod(methodNode, frames, consumers, expandedVariables, instructionIndexMap);

                if(newMethod != null){
                    newMethods.add(newMethod);
                }
            }
        }

        classNode.methods.addAll(newMethods);

        System.out.println("Current Remaps:");
        for(String remap: remappedMethods){
            System.out.println("\t" + remap);
        }
    }

    private static MethodNode modifyMethod(MethodNode methodNode, Frame<LightEngineValue>[] frames, Map<LightEngineValue, Set<Integer>> consumers, Set<Integer> expandedVariables, Map<AbstractInsnNode,
        Integer> instructionIndexMap) {
        //Copy the whole method
        MethodNode newMethod = copy(methodNode);
        boolean changedAnything = false;

        //Create the variable mapper
        LocalVariableMapper variableMapper = new LocalVariableMapper();
        for(int var : expandedVariables){
            variableMapper.addTransformedVariable(var);
            changedAnything = true;
        }
        variableMapper.generate();

        //Generate the descriptor for the modified method
        modifyDescriptor(newMethod, expandedVariables);

        //If the descriptor didn't get modified add -3int to the end of its name to prevent clashes
        if(newMethod.desc.equals(methodNode.desc) && !newMethod.name.startsWith("<")){
            newMethod.name += "3int";
        }

        AbstractInsnNode[] instructions = newMethod.instructions.toArray(); //Generate instructions array

        //Step one: remap all variables instructions + check that all expanded variables correspond to longs
        for(int i = 0; i < instructions.length; i++){
            AbstractInsnNode instruction = newMethod.instructions.get(i);
            if(instruction instanceof VarInsnNode varNode){
                if(variableMapper.isATransformedLong(varNode.var)){
                    if(instruction.getOpcode() != Opcodes.LLOAD && instruction.getOpcode() != Opcodes.LSTORE){
                        throw new IllegalStateException("Accessing mapped local variable but not as a long!");
                    }
                }
                varNode.var = variableMapper.mapLocalVariable(varNode.var);
                changedAnything = true;
            }else if(instruction instanceof IincInsnNode iincNode){
                if(variableMapper.isATransformedLong(iincNode.var)){
                    throw new IllegalStateException("Incrementing mapped variable :(");
                }
                iincNode.var = variableMapper.mapLocalVariable(iincNode.var);
                changedAnything = true;
            }
        }

        //Then change all accesses and uses of packed variables
        for(int i = 0; i < instructions.length; i++){
            AbstractInsnNode instruction = instructions[i];

            if(instruction instanceof MethodInsnNode methodCall){
                String methodID = methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
                boolean wasUnpacked = false;
                for(int axis = 0; axis < 3; axis++){
                    //Find if this is an unpacking method and if so modify it's emitter
                    if(UNPACKING_METHODS[axis].equals(methodID)){
                        wasUnpacked = true;
                        newMethod.instructions.remove(methodCall);

                        Set<AbstractInsnNode> emitters = topOfFrame(frames[i]).getSource();
                        for(AbstractInsnNode otherEmitter: emitters){ //otherEmitter is an instruction from the original method and should NOT be modified
                            int emitterIndex = instructionIndexMap.get(otherEmitter);
                            AbstractInsnNode emitter = instructions[emitterIndex];
                            modifyPosEmitter(frames, instructions, emitter, emitterIndex, newMethod.instructions, variableMapper, axis, instructionIndexMap, consumers);
                        }
                        break;
                    }
                }

                //Only runs if the previous section changed nothing
                if(!wasUnpacked){
                    MethodInfo methodInfo;
                    String newName = methodCall.name;
                    String newOwner = methodCall.owner;
                    String descriptorVerifier = null;
                    if((methodInfo = methodInfoLookup.get(methodID)) != null){
                        if(methodInfo.returnsPackedBlockPos()){
                            continue;
                        }

                        newName = methodInfo.getNewName();
                        newOwner = methodInfo.getNewOwner();
                        descriptorVerifier = methodInfo.getNewDesc();
                    }

                    //Figure out the amount of arguments this method call takes and their locations on the stack
                    boolean isStatic = methodCall.getOpcode() == Opcodes.INVOKESTATIC;

                    int numArgs = MethodInfo.getNumArgs(methodCall.desc);
                    if(!isStatic){
                        numArgs++;
                    }

                    int firstArgIndex = frames[i].getStackSize() - numArgs;

                    //This will record what parameters get turned into 3 ints to change the method signature correctly
                    List<Integer> expandedIndices = new ArrayList<>();


                    for(int offset = 0; offset < numArgs; offset++){
                        //Get argument value from current frame
                        LightEngineValue argument = frames[i].getStack(firstArgIndex + offset);
                        for(AbstractInsnNode otherEmitter: argument.getSource()){
                            int emitterIndex = instructionIndexMap.get(otherEmitter);
                            //Get the emitter
                            AbstractInsnNode emitter = instructions[emitterIndex];
                            //Check if the emitter should be turned into a 3int emitter and if so track that and modify the emitter
                            if(modifyPosEmitter(frames, instructions, emitter, emitterIndex, newMethod.instructions, variableMapper, -1, instructionIndexMap, consumers)){
                                expandedIndices.add(offset);
                            }
                        }
                    }

                    //Modify the descriptor
                    String newDescriptor = modifyDescriptor(methodCall.desc, expandedIndices, isStatic, false);
                    assert descriptorVerifier == null || descriptorVerifier.equals(newDescriptor);

                    //Log transformation and change method call info
                    if(!newDescriptor.equals(methodCall.desc)) {
                        methodCall.owner = newOwner;
                        methodCall.name = newName;
                        methodCall.desc = newDescriptor;

                        remappedMethods.add(methodID + " -> " + newOwner + "#" + newName + " " + newDescriptor);
                    }
                }
            }
        }

        //Apply extra patterns for some more precise changes
        List<BytecodePattern> patterns = new ArrayList<>();
        patterns.add(new BlockPosOffsetPattern());
        patterns.add(new CheckInvalidPosPattern());
        patterns.add(new PackedInequalityPattern());

        applyPatterns(newMethod.instructions, variableMapper, patterns);

        //Create local variable name table
        List<LocalVariableNode> localVariables = new ArrayList<>();
        for(LocalVariableNode var : newMethod.localVariables){
            int mapped = variableMapper.mapLocalVariable(var.index);
            boolean isExpanded = variableMapper.isATransformedLong(var.index);
            if(isExpanded){
                localVariables.add(new LocalVariableNode(var.name + "_x", "I", null, var.start, var.end, mapped));
                localVariables.add(new LocalVariableNode(var.name + "_y", "I", null, var.start, var.end, mapped + 1));
                localVariables.add(new LocalVariableNode(var.name + "_z", "I", null, var.start, var.end, mapped + 2));
            }else{
                localVariables.add(new LocalVariableNode(var.name, var.desc, var.signature, var.start, var.end, mapped));
            }
        }

        System.out.println(localVariables.size());

        newMethod.localVariables = localVariables;
        newMethod.parameters = null;

        return changedAnything ? newMethod : null;
    }

    private static void applyPatterns(InsnList instructions, LocalVariableMapper mapper, List<BytecodePattern> patterns) {
        int currentIndex = 0;

        while (currentIndex < instructions.size()){
            for(BytecodePattern pattern: patterns){
                if(pattern.apply(instructions, mapper, currentIndex)){
                    break;
                }
            }
            currentIndex++;
        }
    }

    /**
     * Modifies an instruction or series of instructions that emit a packed position so as to only emit a single int representing one of the 3 coordinates or emit all 3
     * @param frames Array of frames generated by an Evaluator
     * @param instructions Array of instructions
     * @param emitter Instruction that emitted that packed block position
     * @param integer Index into {@code instructions} of emitter
     * @param insnList The {@code InsnList} of the method
     * @param offset Should be 0 for x-coordinate, 1 for y, 2 for z and -1 for all 3
     * @return Whether or not any modifications occurred i.e If the instruction actually emits a packed pos
     */
    private static boolean modifyPosEmitter(Frame<LightEngineValue>[] frames, AbstractInsnNode[] instructions, AbstractInsnNode emitter, Integer integer, InsnList insnList, LocalVariableMapper variableMapper, int offset, Map<AbstractInsnNode, Integer> instructionIndexMap, Map<LightEngineValue, Set<Integer>> consumers) {
        if(emitter.getOpcode() == Opcodes.LLOAD){
            VarInsnNode loader = (VarInsnNode) emitter;
            if(!variableMapper.isARemappedTransformedLong(loader.var)){
                return false; //Only change anything if the loaded long is a tranformed one
            }
            if(offset != -1){ //If you only want a single axis just modify the instruction
                loader.setOpcode(Opcodes.ILOAD);
                loader.var += offset;
            }else{
                //Otherwise insert two more instructions so as to load all 3 ints
                loader.setOpcode(Opcodes.ILOAD);
                insnList.insertBefore(loader, new VarInsnNode(Opcodes.ILOAD, loader.var));
                insnList.insertBefore(loader, new VarInsnNode(Opcodes.ILOAD, loader.var + 1));
                loader.var += 2;
            }
            return true;
        }else if(emitter instanceof VarInsnNode) {
            return false; //Any other VarInsnNode is just a normal local variable
        }else if(emitter instanceof LdcInsnNode constantLoad){
            System.out.println("Expanding Constant");
            //Expand Long.MAX_VALUE to 3 Integer.MAX_VALUE
            if(constantLoad.cst instanceof Long && (Long) constantLoad.cst == Long.MAX_VALUE){
                int amount = offset == -1 ? 3 : 1;
                for(int i = 0; i < amount; i++){
                    insnList.insert(emitter, new LdcInsnNode(Integer.MAX_VALUE));
                }
                insnList.remove(emitter);
                return true;
            }
        }else if(emitter instanceof FieldInsnNode){
            return false;
        }else if(emitter instanceof TypeInsnNode) {
            return false;
        }else if(emitter instanceof MethodInsnNode methodCall){
            String methodID = methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
            MethodInfo methodInfo = methodInfoLookup.get(methodID);
            if(methodInfo != null){
                //Change BlockPos.asLong() calls to three separate calls to getX, getY and getZ
                if(methodInfo.returnsPackedBlockPos()){
                    if(methodCall.name.equals(BLOCK_POS_AS_LONG_METHOD)){
                        methodCall.desc = "()I";
                        if(offset != -1){
                            methodCall.name = BLOCKPOS_VIRTUAL_GET[offset];
                        }else{
                            insnList.insertBefore(emitter, new InsnNode(Opcodes.DUP));
                            insnList.insertBefore(emitter, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BlockPosOffsetPattern.BLOCK_POS_CLASS_NAME, BLOCKPOS_VIRTUAL_GET[0], "()I"));
                            insnList.insertBefore(emitter, new InsnNode(Opcodes.SWAP));
                            insnList.insertBefore(emitter, new InsnNode(Opcodes.DUP));
                            insnList.insertBefore(emitter, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, BlockPosOffsetPattern.BLOCK_POS_CLASS_NAME, BLOCKPOS_VIRTUAL_GET[1], "()I"));
                            insnList.insertBefore(emitter, new InsnNode(Opcodes.SWAP));
                            methodCall.name = BLOCKPOS_VIRTUAL_GET[2];
                        }
                    }
                    return true;
                }
            }
            return false;
        }else if(emitter instanceof InsnNode){

        }else{
            System.out.println("Warning: Don't know what to do with " + insnToString(emitter));
            return false;
        }

        return false;
    }

    private static String insnToString(AbstractInsnNode instruction){
        if(instruction instanceof MethodInsnNode methodCall){
            String callType = switch (instruction.getOpcode()){
                case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
                case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
                case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
                case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
                default -> throw new IllegalStateException("Unexpected value: " + instruction.getOpcode());
            };

            return callType + " " + methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
        }

        return instruction.toString() + " " + instruction.getOpcode();
    }

    public static MethodNode copy(MethodNode method){
        ClassNode classNode = new ClassNode();
        //MethodNode other = new MethodNode();
        method.accept(classNode);
        return classNode.methods.get(0);
    }

    private static Set<Integer> getExpandedVariables(Frame<LightEngineValue>[] frames, AbstractInsnNode[] instructions, Map<LightEngineValue, Set<Integer>> consumerInfo) {
        Set<Integer> expandedVariables = new HashSet<>();
        Set<Integer> placesWherePackedBlockPosAreProduced = new HashSet<>();

        //Inspect ALL method calls. This only has to be done once
        for(int i = 0; i < frames.length; i++){
            AbstractInsnNode instruction = instructions[i];
            if(instruction instanceof MethodInsnNode methodCall){
                String methodName = methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
                MethodInfo methodInfo = methodInfoLookup.get(methodName);
                if(methodInfo == null) continue;

                Frame<LightEngineValue> currentFrame = frames[i];
                int firstArgIndex = currentFrame.getStackSize() - methodInfo.getNumArgs();
                if(methodInfo.hasPackedArguments()){
                    for(int packedArgument : methodInfo.getExpandedIndices()){
                        LightEngineValue valueOnStack = currentFrame.getStack(firstArgIndex + packedArgument);
                        if(valueOnStack != null){
                            expandedVariables.addAll(valueOnStack.getLocalVars());
                        }
                    }
                }

                if(methodInfo.returnsPackedBlockPos()){
                    Frame<LightEngineValue> nextFrame = frames[i + 1];
                    LightEngineValue top = topOfFrame(nextFrame);
                    Set<Integer> consumerIndices = consumerInfo.get(top);
                    placesWherePackedBlockPosAreProduced.add(i);
                    for(int consumerIndex : consumerIndices){
                        AbstractInsnNode consumer = instructions[consumerIndex];
                        if(consumer instanceof VarInsnNode storeInstruction){
                            expandedVariables.add(storeInstruction.var);
                        }else{
                            //System.out.println("Unhandled Consumer Instruction: " + insnToString(instruction));
                        }
                    }
                }
            }
        }

        //Until no more packed local variables are found look at their usages to find more
        boolean changed = true;
        while(changed){
            changed = false;
            for(int i = 0; i < frames.length; i++){
                if(instructions[i].getOpcode() == Opcodes.LLOAD){
                    VarInsnNode loadInsn = (VarInsnNode) instructions[i];
                    if(expandedVariables.contains(loadInsn.var)){
                        if(placesWherePackedBlockPosAreProduced.add(i)){
                            Set<Integer> consumers = consumerInfo.get(topOfFrame(frames[i + 1]));
                            LightEngineValue loadedLong = frames[i+1].getStack(frames[i+1].getStackSize() - 1);

                            for(int consumerIndex: consumers){
                                AbstractInsnNode consumer = instructions[consumerIndex];
                                Frame<LightEngineValue> frame = frames[consumerIndex];
                                if(consumer.getOpcode() == Opcodes.LCMP){
                                    LightEngineValue operandOne = frame.getStack(frame.getStackSize() - 1);
                                    LightEngineValue operandTwo = frame.getStack(frame.getStackSize() - 2);

                                    if(operandOne != loadedLong){
                                        if(expandedVariables.addAll(operandOne.getLocalVars())){
                                            changed = true;
                                        }
                                    }else{
                                        if(expandedVariables.addAll(operandTwo.getLocalVars())){
                                            changed = true;
                                        }
                                    }
                                }else{
                                    //System.out.println("Unhandled Consumer Instruction: " + insnToString(consumer));
                                }
                            }
                        }
                    }
                }
            }
        }

        return expandedVariables;
    }

    private static <T extends Value> T topOfFrame(Frame<T> frame) {
        return frame.getStack(frame.getStackSize() - 1);
    }

    private static Map<LightEngineValue, Set<Integer>> mapConsumerCache(Map<LightEngineValue, Set<AbstractInsnNode>> consumers, Map<AbstractInsnNode, Integer> instructionIndexMap) {
        Map<LightEngineValue, Set<Integer>> mapped = new HashMap<>();

        for(Map.Entry<LightEngineValue, Set<AbstractInsnNode>> entry: consumers.entrySet()){
            mapped.put(entry.getKey(), entry.getValue().stream().map(instructionIndexMap::get).collect(Collectors.toSet()));
        }

        return mapped;
    }

    public static boolean shouldModifyClass(ClassNode classNode, MappingResolver map){
        loadData(map);
        return transformsToApply.containsKey(classNode.name);
    }

    public static void modifyDescriptor(MethodNode methodNode, Set<Integer> expandedVariables) {
        Type returnType = Type.getReturnType(methodNode.desc);
        Type[] args = Type.getArgumentTypes(methodNode.desc);

        List<Type> newArgumentTypes = new ArrayList<>();
        int i = 0;
        if((methodNode.access & Opcodes.ACC_STATIC) == 0) i++;
        for(Type argument: args){
            if(expandedVariables.contains(i)){
                for(int j = 0; j < 3; j++) newArgumentTypes.add(Type.INT_TYPE);
            }else{
                newArgumentTypes.add(argument);
            }

            i += argument.getSize();
        }

        methodNode.desc = modifyDescriptor(methodNode.desc, expandedVariables, (methodNode.access & Opcodes.ACC_STATIC) != 0, true);
    }
    public static String modifyDescriptor(String descriptor, Collection<Integer> expandedVariables, boolean isStatic, boolean adjustForVarWidth){
        Type returnType = Type.getReturnType(descriptor);
        Type[] args = Type.getArgumentTypes(descriptor);

        List<Type> newArgumentTypes = new ArrayList<>();
        int i = 0;
        if(!isStatic) i++;
        for(Type argument: args){
            if(expandedVariables.contains(i)){
                for(int j = 0; j < 3; j++) newArgumentTypes.add(Type.INT_TYPE);
            }else{
                newArgumentTypes.add(argument);
            }

            i += adjustForVarWidth ? argument.getSize() : 1;
        }

        return Type.getMethodDescriptor(returnType, newArgumentTypes.toArray(Type[]::new));
    }

    public static void loadData(MappingResolver map){
        if(loaded) return;

        JsonParser parser = new JsonParser();
        JsonObject root;
        try {
            InputStream is = LongPosTransformer.class.getResourceAsStream(REMAP_PATH);
            root = parser.parse(new String(is.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }catch (IOException e){
            throw new IllegalStateException("Failed to load ASM light engine remap config");
        }

        Map<String, List<String>> superClassesPerClass = new HashMap<>();
        JsonObject classInfoJson = root.getAsJsonObject("class_info");
        for(Map.Entry<String, JsonElement> entry: classInfoJson.entrySet()){
            String className = map.mapClassName("intermediary", entry.getKey().replace('/', '.')).replace('.', '/');
            List<String> superClasses = new ArrayList<>();
            JsonArray superClassArray = entry.getValue().getAsJsonObject().getAsJsonArray("superclasses");
            if(superClassArray != null) {
                superClassArray.forEach((element) -> {
                    superClasses.add(map.mapClassName("intermediary", element.getAsString().replace('/', '.')).replace('.', '/'));
                });
                superClassesPerClass.put(className, superClasses);
            }

            JsonArray transformArray = entry.getValue().getAsJsonObject().getAsJsonArray("transform");
            if(transformArray != null){
                List<String> methodsToTransform = new ArrayList<>();
                for(JsonElement transform: transformArray){
                    JsonObject transformInfo = transform.getAsJsonObject();

                    String owner = entry.getKey();
                    JsonElement ownerElement = transformInfo.get("owner");
                    if(ownerElement != null){
                        owner = ownerElement.getAsString();
                    }

                    String name = transformInfo.get("name").getAsString();
                    String descriptor = transformInfo.get("descriptor").getAsString();

                    String actualName = map.mapMethodName("intermediary", owner.replace('/', '.'), name, descriptor);
                    String actualDescriptor = MethodInfo.mapDescriptor(descriptor, map);

                    methodsToTransform.add(actualName + " " + actualDescriptor);
                }
                transformsToApply.put(className, methodsToTransform);
            }
        }

        JsonObject methodInfoJson = root.getAsJsonObject("method_info");
        for(Map.Entry<String, JsonElement> entry: methodInfoJson.entrySet()){
            String[] parts = entry.getKey().split(" ");
            String[] moreParts = parts[0].split("#");
            MethodInfo methodInfo = new MethodInfo(entry.getValue().getAsJsonObject(), moreParts[0], moreParts[1], parts[1], map);
            methodInfoLookup.put(methodInfo.getMethodID(), methodInfo);

            List<String> superClasses = superClassesPerClass.get(methodInfo.getOriginalOwner());

            if(superClasses != null){
                for(String superClass: superClasses){
                    methodInfoLookup.put(superClass + "#" + methodInfo.getOriginalName() + " " + methodInfo.getOriginalDescriptor(), methodInfo);
                }
            }
        }

        JsonArray unpackers = root.get("unpacking").getAsJsonArray();
        for(int i = 0; i < 3; i++){
            String unpacker = unpackers.get(i).getAsString();
            String[] ownerAndNamePlusDescriptor = unpacker.split(" ");
            String descriptor = ownerAndNamePlusDescriptor[1];
            String[] ownerAndName = ownerAndNamePlusDescriptor[0].split("#");
            String owner = map.mapClassName("intermediary", ownerAndName[0].replace('/', '.'));
            String methodName = map.mapMethodName("intermediary", ownerAndName[0].replace('/', '.'), ownerAndName[1], descriptor);
            UNPACKING_METHODS[i] = (owner + "#" + methodName + " " + descriptor).replace('.', '/');
        }

        JsonObject blockPosUnpacking = root.getAsJsonObject("block_pos_unpacking");
        String vec3iIntermediary = blockPosUnpacking.get("vec3i").getAsString().replace('/', '.');
        VEC3I_CLASS_NAME = map.mapClassName("intermediary", vec3iIntermediary).replace('.', '/');

        int i = 0;
        for(JsonElement getMethod: blockPosUnpacking.getAsJsonArray("get")){
            BLOCKPOS_VIRTUAL_GET[i] = map.mapMethodName("intermediary", vec3iIntermediary, getMethod.getAsString(), "()I");
            i++;
        }

        BlockPosOffsetPattern.readConfig(root.getAsJsonObject("pattern_config").getAsJsonObject("block_pos_offset"), map);

        BLOCK_POS_AS_LONG_METHOD = map.mapMethodName("intermediary", BlockPosOffsetPattern.BLOCK_POS_INTERMEDIARY, blockPosUnpacking.get("as_long").getAsString(), "()J");

        loaded = true;
    }
}
