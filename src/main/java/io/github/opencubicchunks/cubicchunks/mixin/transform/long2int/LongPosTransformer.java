package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.opencubicchunks.cubicchunks.mixin.transform.long2int.bytecodegen.InstructionFactory;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.apache.logging.log4j.core.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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

    private static final LightEngineInterpreter interpreter = new LightEngineInterpreter(methodInfoLookup);
    private static final Analyzer<LightEngineValue> analyzer = new Analyzer<>(interpreter);
    private static final List<String> errors = new ArrayList<>();

    public static Set<String> remappedMethods = new HashSet<>();

    public static void modifyClass(ClassNode classNode) {
        System.out.println("[LongPosTransformer]: Modifying " + classNode.name);
        List<String> transforms = transformsToApply.get(classNode.name);

        List<MethodNode> newMethods = new ArrayList<>();

        for (MethodNode methodNode : classNode.methods) {
            String methodNameAndDescriptor = methodNode.name + " " + methodNode.desc;
            if (transforms.contains(methodNameAndDescriptor)) {
                System.out.println("[LongPosTransformer]: Modifying " + methodNode.name);

                MethodNode newMethod = modifyMethod(methodNode, classNode);

                if (newMethod != null) {
                    newMethods.add(newMethod);
                }
            }
        }

        classNode.methods.addAll(newMethods);
        saveClass(classNode, ""); //Saves class without computing frames so that if that fails there's still this

        System.out.println("Current Remaps:");
        for (String remap : remappedMethods) {
            System.out.println("\t" + remap);
        }

        if(errors.size() > 0){
            for(String error: errors){
                System.out.println(error);
            }
            throw new IllegalStateException("Modifying " + classNode.name + " caused (an) error(s)!");
        }
    }

    public static byte[] saveClass(ClassNode classNode, String suffix){
        ClassWriter classWriter = new ClassWriter(0);

        classNode.accept(classWriter);
        Path savePath = Path.of("longpos-out", classNode.name + suffix + ".class");

        try {
            if(!savePath.toFile().exists()){
                savePath.toFile().getParentFile().mkdirs();
                Files.createFile(savePath);
            }

            FileOutputStream fout = new FileOutputStream(savePath.toAbsolutePath().toString());
            byte[] bytes;
            fout.write(bytes = classWriter.toByteArray());
            fout.close();
            System.out.println("Saved class at " + savePath.toAbsolutePath());
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void trackError(MethodNode node, String message){
        errors.add("Error in " + node.name + ", " + message);
    }

    private static MethodNode modifyMethod(MethodNode methodNode, ClassNode classNode) {
        String methodNameAndDescriptor = methodNode.name + " " + methodNode.desc;
        MethodNode newMethod = copy(methodNode);

        {
            AbstractInsnNode[] originalInstructions = newMethod.instructions.toArray();
            for(AbstractInsnNode insnNode: originalInstructions){
                if(insnNode instanceof FrameNode){
                    newMethod.instructions.remove(insnNode);
                }
            }
        }

        try {
            analyzer.analyze(classNode.name, newMethod);
        } catch (AnalyzerException e) {
            throw new IllegalArgumentException("Could not modify method " + methodNameAndDescriptor + " in class " + classNode.name + ". Analyzer failed", e);
        }

        Frame<LightEngineValue>[] frames = analyzer.getFrames();
        AbstractInsnNode[] instructions = newMethod.instructions.toArray();
        Map<AbstractInsnNode, Integer> instructionIndexMap = new HashMap<>(instructions.length);

        //Create a map to easily get the index of an instruction
        for (int i = 0; i < instructions.length; i++) {
            instructionIndexMap.put(instructions[i], i);
        }

        Set<Integer> expandedVariables = new HashSet<>();
        LocalVariableMapper variableMapper = new LocalVariableMapper();

        //There has got to be a better way of iterating over all values
        for (Frame<LightEngineValue> frame : frames) {
            if (frame == null) continue;
            for (int i = 0; i < frame.getStackSize(); i++) {
                if (frame.getStack(i).isAPackedLong()) {
                    expandedVariables.addAll(frame.getStack(i).getLocalVars());
                }
            }

            for (int i = 0; i < frame.getLocals(); i++) {
                if (frame.getLocal(i).isAPackedLong()) {
                    expandedVariables.addAll(frame.getLocal(i).getLocalVars());
                }
            }
        }

        for (int i : expandedVariables) {
            variableMapper.addTransformedVariable(i);
        }

        variableMapper.generate();

        ExtraVariableManager variableManager = new ExtraVariableManager(methodNode.maxLocals + variableMapper.getLocalVariableOffset());

        modifyDescriptor(newMethod, expandedVariables);

        if (newMethod.desc.equals(methodNode.desc) && !newMethod.name.startsWith("<")) {
            newMethod.name += "3int";
        }

        //Step One: Remap all variable loading / storing (and increments)
        for (AbstractInsnNode instruction : instructions) {
            remapInstruction(instruction, variableMapper);
        }

        //Step Two: Expand method calls and their arguments (not functions that return longs. Those need special treatment)
        for (int i = 0; i < instructions.length; i++) {
            AbstractInsnNode instruction = instructions[i];

            if (instruction instanceof MethodInsnNode methodCall) {
                String methodID = methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
                boolean wasUnpacked = false;
                for (int axis = 0; axis < 3; axis++) {
                    //Find if this is an unpacking method and if so modify it's emitter
                    if (UNPACKING_METHODS[axis].equals(methodID)) {
                        wasUnpacked = true;
                        newMethod.instructions.remove(methodCall);

                        Set<AbstractInsnNode> emitters = topOfFrame(frames[i]).getSource();
                        for (AbstractInsnNode otherEmitter : emitters) { //otherEmitter is an instruction from the original method and should NOT be modified
                            int emitterIndex = instructionIndexMap.get(otherEmitter);
                            AbstractInsnNode emitter = instructions[emitterIndex];
                            expandSingleComponent(emitter, emitterIndex, newMethod.instructions, frames, instructions, instructionIndexMap, variableMapper, axis);
                        }
                        break;
                    }
                }

                if (!wasUnpacked) {
                    MethodInfo methodInfo;
                    String newName = methodCall.name;
                    String newOwner = methodCall.owner;
                    String descriptorVerifier = null;
                    if ((methodInfo = methodInfoLookup.get(methodID)) != null) {
                        if (methodInfo.returnsPackedBlockPos()) {
                            continue;
                        }

                        newName = methodInfo.getNewName();
                        newOwner = methodInfo.getNewOwner();
                        descriptorVerifier = methodInfo.getNewDesc();
                    }else if(!methodCall.desc.endsWith("V")){
                        if(topOfFrame(frames[i + 1]).isAPackedLong()){
                            trackError(methodNode, "'" + methodID + " returns a packed long but has no known expansion");
                        }
                    }

                    //Figure out the amount of arguments this method call takes and their locations on the stack
                    boolean isStatic = methodCall.getOpcode() == Opcodes.INVOKESTATIC;

                    int numArgs = MethodInfo.getNumArgs(methodCall.desc);
                    if (!isStatic) {
                        numArgs++;
                    }

                    int firstArgIndex = frames[i].getStackSize() - numArgs;

                    //This will record what parameters get turned into 3 ints to change the method signature correctly
                    List<Integer> expandedIndices = new ArrayList<>();


                    for (int offset = 0; offset < numArgs; offset++) {
                        //Get argument value from current frame
                        LightEngineValue argument = frames[i].getStack(firstArgIndex + offset);
                        for (AbstractInsnNode otherEmitter : argument.getSource()) {
                            int emitterIndex = instructionIndexMap.get(otherEmitter);
                            //Get the emitter
                            AbstractInsnNode emitter = instructions[emitterIndex];
                            //Check if the emitter should be turned into a 3int emitter and if so track that and modify the emitter
                            if (testAndExpandEmitter(frames, instructions, emitter, emitterIndex, newMethod.instructions, variableMapper, instructionIndexMap, variableManager)) {
                                expandedIndices.add(offset);
                            }
                        }
                    }

                    //Modify the descriptor
                    String newDescriptor = modifyDescriptor(methodCall.desc, expandedIndices, isStatic, false);
                    if(descriptorVerifier != null){
                        if(!descriptorVerifier.equals(newDescriptor)){
                            trackError(methodNode, "Descriptor doesn't match expected. Created " + newDescriptor + " instead of expected " + descriptorVerifier);
                        }
                    }
                    assert descriptorVerifier == null || descriptorVerifier.equals(newDescriptor);

                    //Log transformation and change method call info
                    if (!newDescriptor.equals(methodCall.desc)) {
                        methodCall.owner = newOwner;
                        methodCall.name = newName;
                        methodCall.desc = newDescriptor;

                        remappedMethods.add(methodID + " -> " + newOwner + "#" + newName + " " + newDescriptor);
                    }
                }
            }else if(instruction.getOpcode() == Opcodes.LSTORE){
                if(topOfFrame(frames[i]).isAPackedLong()){
                    VarInsnNode localVar = (VarInsnNode) instruction;

                    for(AbstractInsnNode emitter: topOfFrame(frames[i]).getSource()){
                        int emitterIndex = instructionIndexMap.get(emitter);
                        testAndExpandEmitter(frames, instructions, instructions[emitterIndex], emitterIndex, newMethod.instructions, variableMapper, instructionIndexMap, variableManager);
                    }

                    AbstractInsnNode storeY = new VarInsnNode(Opcodes.ISTORE, localVar.var + 1);
                    AbstractInsnNode storeZ = new VarInsnNode(Opcodes.ISTORE, localVar.var + 2);

                    localVar.setOpcode(Opcodes.ISTORE);

                    newMethod.instructions.insertBefore(localVar, storeY);
                    newMethod.instructions.insertBefore(storeY, storeZ);
                }
            }else if(instruction.getOpcode() == Opcodes.LCMP){
                Frame<LightEngineValue> frame = frames[i];
                int stackSize = frame.getStackSize();
                LightEngineValue operandOne = frame.getStack(stackSize - 1);
                LightEngineValue operandTwo = frame.getStack(stackSize - 2);

                if(operandOne.isAPackedLong() || operandTwo.isAPackedLong()){
                    operandOne.setPackedLong();
                    operandTwo.setPackedLong();

                    if(operandOne.getSource().size() != 1){
                        throw new IllegalStateException("Value #1 doesn't have one source for LCMP");
                    }

                    if(operandTwo.getSource().size() != 1){
                        throw new IllegalStateException("Value #2 doesn't have one source for LCMP");
                    }

                    Set<AbstractInsnNode> consumers = topOfFrame(frames[i + 1]).getConsumers();
                    if(consumers.size() != 1){
                        throw new IllegalStateException("No");
                    }

                    AbstractInsnNode consumer = consumers.iterator().next();
                    if(!(consumer instanceof JumpInsnNode jump)){
                        throw new IllegalStateException("LCMP result must be consumed by IFEQ or IFNE");
                    }

                    int jumpOpcode = jump.getOpcode() == Opcodes.IFEQ ? Opcodes.IF_ICMPEQ : Opcodes.IF_ICMPNE;
                    LabelNode jumpLabel = jump.label;

                    AbstractInsnNode operandOneSource = operandOne.getSource().iterator().next();
                    int operandOneSourceIndex = instructionIndexMap.get(operandOneSource);
                    InstructionFactory[] operandOneGetters = saveEmitterResultInLocalVariable(frames, instructions, newMethod.instructions, operandOneSource, operandOne,
                        operandOneSourceIndex, variableMapper, variableManager, i, instructionIndexMap);

                    AbstractInsnNode operandTwoSource = operandTwo.getSource().iterator().next();
                    int operandTwoSourceIndex = instructionIndexMap.get(operandTwoSource);
                    InstructionFactory[] operandTwoGetters = saveEmitterResultInLocalVariable(frames, instructions, newMethod.instructions, operandTwoSource, operandTwo,
                        operandTwoSourceIndex, variableMapper, variableManager, i, instructionIndexMap);

                    InsnList generated = new InsnList();

                    for(int axis = 0; axis < 3; axis++){
                        if(operandOneGetters.length == 1){
                            generated.add(operandOneGetters[0].create());
                        }else{
                            generated.add(operandOneGetters[axis].create());
                        }

                        if(operandTwoGetters.length == 1){
                            generated.add(operandTwoGetters[0].create());
                        }else{
                            generated.add(operandTwoGetters[axis].create());
                        }

                        generated.add(new JumpInsnNode(jumpOpcode, jumpLabel));
                    }

                    newMethod.instructions.insertBefore(instruction, generated);
                    newMethod.instructions.remove(instruction);
                    newMethod.instructions.remove(jump);
                }
            }
        }

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

        newMethod.localVariables = localVariables;
        newMethod.parameters = null;

        newMethod.maxLocals = 0; //TODO: This ain't correct lol
        newMethod.maxStack = 0;

        return newMethod;
    }

    private static boolean testAndExpandEmitter(Frame<LightEngineValue>[] frames, AbstractInsnNode[] instructions, AbstractInsnNode emitter, int emitterIndex, InsnList insnList,
                                                LocalVariableMapper variableMapper, Map<AbstractInsnNode, Integer> instructionIndexMap, ExtraVariableManager variableManager) {
        if (emitter.getOpcode() == Opcodes.LLOAD) {
            if (topOfFrame(frames[emitterIndex + 1]).isAPackedLong()) {
                VarInsnNode varLoad = (VarInsnNode) emitter;
                varLoad.setOpcode(Opcodes.ILOAD);

                AbstractInsnNode loadY = new VarInsnNode(Opcodes.ILOAD, varLoad.var + 1);
                AbstractInsnNode loadZ = new VarInsnNode(Opcodes.ILOAD, varLoad.var + 2);

                insnList.insert(emitter, loadY);
                insnList.insert(loadY, loadZ);
                return true;
            } else {
                return false;
            }
        } else if (emitter instanceof MethodInsnNode methodCall) {
            String methodID = methodCall.owner + "#" + methodCall.name + " " + methodCall.desc;
            MethodInfo methodInfo = methodInfoLookup.get(methodID);
            if (methodInfo != null) {
                //Change BlockPos.asLong() calls to three separate calls to getX, getY and getZ
                if (methodInfo.returnsPackedBlockPos()) {
                    Frame<LightEngineValue> currentFrame = frames[emitterIndex];
                    List<InstructionFactory[]> varGetters = new ArrayList<>();
                    int stackSize = currentFrame.getStackSize();

                    int numMethodArgs = MethodInfo.getNumArgs(methodCall.desc);
                    if (!methodInfo.isStatic()) {
                        numMethodArgs++;
                    }

                    for (int i = numMethodArgs; i > 0; i--) {
                        LightEngineValue value = currentFrame.getStack(stackSize - i);
                        if (value.getSource().size() != 1) {
                            throw new IllegalStateException("Don't know how to manage this"); //TODO: Manage this
                        }

                        AbstractInsnNode argEmitter = value.getSource().iterator().next();
                        int argEmitterIndex = instructionIndexMap.get(argEmitter);
                        varGetters.add(saveEmitterResultInLocalVariable(frames, instructions, insnList, argEmitter, value, argEmitterIndex, variableMapper, variableManager, emitterIndex,
                            instructionIndexMap));
                    }
                    InsnList replacement = new InsnList();
                    if(methodCall.name.equals("asLong")){
                        System.out.println("yeet");
                    }
                    for (int axis = 0; axis < 3; axis++) {
                        int i = 0;
                        for (InstructionFactory[] generators : varGetters) {
                            if (generators.length > 1) {
                                replacement.add(generators[axis].create());
                            } else {
                                boolean isSeparated = false;
                                for(Integer[] separatedArgs: methodInfo.getSeparatedArguments()){
                                    if(separatedArgs[0] == i || separatedArgs[1] == i || separatedArgs[2] == i){
                                        if(separatedArgs[axis] == i){
                                            replacement.add(generators[0].create());
                                        }
                                        isSeparated = true;
                                        break;
                                    }
                                }
                                if(!isSeparated) {
                                    replacement.add(generators[0].create());
                                }
                            }
                            i++;
                        }

                        replacement.add(methodInfo.getExpansion(axis));
                    }

                    insnList.insert(methodCall, replacement);
                    insnList.remove(methodCall);
                    return true;
                }
            }
            return false;
        } else if (emitter instanceof LdcInsnNode constantLoad) {
            System.out.println("Expanding Constant");
            //Expand Long.MAX_VALUE to 3 Integer.MAX_VALUE
            if (constantLoad.cst instanceof Long && (Long) constantLoad.cst == Long.MAX_VALUE) {
                for (int i = 0; i < 3; i++) {
                    insnList.insert(emitter, new LdcInsnNode(Integer.MAX_VALUE));
                }
                insnList.remove(emitter);
                return true;
            }

            return false;
        }

        return false;
    }

    private static InstructionFactory[] saveEmitterResultInLocalVariable(Frame<LightEngineValue>[] frames, AbstractInsnNode[] instructions, InsnList insnList, AbstractInsnNode emitter,
                                                                         LightEngineValue value,
                                                                         int emitterIndex, LocalVariableMapper variableMapper,
                                                                         ExtraVariableManager variableManager, int usageIndex, Map<AbstractInsnNode, Integer> instructionIndexMap) {
        if (emitter instanceof VarInsnNode) {
            insnList.remove(emitter);
            if (value.isAPackedLong()) {
                return new InstructionFactory[] {
                    () -> new VarInsnNode(Opcodes.ILOAD, ((VarInsnNode) emitter).var),
                    () -> new VarInsnNode(Opcodes.ILOAD, ((VarInsnNode) emitter).var + 1),
                    () -> new VarInsnNode(Opcodes.ILOAD, ((VarInsnNode) emitter).var + 2)
                };
            } else {
                return new InstructionFactory[] { () -> new VarInsnNode(emitter.getOpcode(), ((VarInsnNode) emitter).var) };
            }
        } else if(emitter instanceof LdcInsnNode constantNode){
            if(!(constantNode.cst instanceof Long)){
                return new InstructionFactory[]{() -> new LdcInsnNode(constantNode.cst)};
            }

            Long cst = (Long) constantNode.cst;

            if(cst != Long.MAX_VALUE){
                throw new IllegalStateException("Can only expand Long.MAX_VALUE");
            }

            insnList.remove(emitter);

            return new InstructionFactory[]{
                () -> new LdcInsnNode(Integer.MAX_VALUE),
                () -> new LdcInsnNode(Integer.MAX_VALUE),
                () -> new LdcInsnNode(Integer.MAX_VALUE)
            };
        } else if(emitter.getOpcode() == Opcodes.BIPUSH || emitter.getOpcode() == Opcodes.SIPUSH){
            insnList.remove(emitter);

            IntInsnNode intLoad = (IntInsnNode) emitter;
            return new InstructionFactory[]{
                () -> new IntInsnNode(intLoad.getOpcode(), intLoad.operand)
            };
        }else if(emitter.getOpcode() == Opcodes.GETSTATIC){
            if(value.isAPackedLong()){;
                throw new IllegalStateException("This better never happen");
            }

            insnList.remove(emitter);

            FieldInsnNode fieldInsnNode = (FieldInsnNode) emitter;
            return new InstructionFactory[]{
                () -> new FieldInsnNode(fieldInsnNode.getOpcode(), fieldInsnNode.owner, fieldInsnNode.name, fieldInsnNode.desc)
            };
        }else if(emitter.getOpcode() >= Opcodes.ACONST_NULL && emitter.getOpcode() <= Opcodes.DCONST_1){
            insnList.remove(emitter);

            return new InstructionFactory[]{
                () -> new InsnNode(emitter.getOpcode())
            };
        }else {
            if (value.isAPackedLong()) {
                testAndExpandEmitter(frames, instructions, emitter, emitterIndex, insnList, variableMapper, instructionIndexMap, variableManager);

                int firstVar = variableManager.getExtraVariable(emitterIndex, usageIndex);
                int secondVar = variableManager.getExtraVariable(emitterIndex, usageIndex);
                int thirdVar = variableManager.getExtraVariable(emitterIndex, usageIndex);

                AbstractInsnNode firstStore = new VarInsnNode(Opcodes.ISTORE, firstVar);
                AbstractInsnNode secondStore = new VarInsnNode(Opcodes.ISTORE, secondVar);
                AbstractInsnNode thirdStore = new VarInsnNode(Opcodes.ISTORE, thirdVar);

                insnList.insert(emitter, thirdStore);
                insnList.insert(thirdStore, secondStore);
                insnList.insert(secondStore, firstStore);

                return new InstructionFactory[] {
                    () -> new VarInsnNode(Opcodes.ILOAD, firstVar),
                    () -> new VarInsnNode(Opcodes.ILOAD, secondVar),
                    () -> new VarInsnNode(Opcodes.ILOAD, thirdVar)
                };
            } else {
                Type type = value.getType();
                int storeOpcode = switch (type.getSort()) {
                    case Type.INT, Type.SHORT, Type.CHAR, Type.BYTE -> Opcodes.ISTORE;
                    case Type.LONG -> Opcodes.LSTORE;
                    case Type.FLOAT -> Opcodes.FSTORE;
                    case Type.DOUBLE -> Opcodes.DSTORE;
                    case Type.ARRAY, Type.OBJECT -> Opcodes.ASTORE;
                    default -> throw new IllegalStateException("Unexpected value: " + type.getSort());
                };

                int loadOpcode = switch (type.getSort()){
                    case Type.INT, Type.SHORT, Type.CHAR, Type.BYTE -> Opcodes.ILOAD;
                    case Type.LONG -> Opcodes.LLOAD;
                    case Type.FLOAT -> Opcodes.FLOAD;
                    case Type.DOUBLE -> Opcodes.DLOAD;
                    case Type.ARRAY, Type.OBJECT -> Opcodes.ALOAD;
                    default -> throw new IllegalStateException("Unexpected value: " + type.getSort());
                };

                int var;
                if (type.getSize() == 2) {
                    var = variableManager.getExtraVariableForComputationalTypeTwo(emitterIndex, usageIndex);
                } else {
                    var = variableManager.getExtraVariable(emitterIndex, usageIndex);
                }

                insnList.insert(emitter, new VarInsnNode(storeOpcode, var));

                return new InstructionFactory[] { () -> new VarInsnNode(loadOpcode, var) };
            }
        }
    }

    //Assumes that the emitter emits a packed block pos
    private static void expandSingleComponent(AbstractInsnNode emitter, int emitterIndex, InsnList insnList, Frame<LightEngineValue>[] frames, AbstractInsnNode[] instructions,
                                              Map<AbstractInsnNode, Integer> instructionIndexMap, LocalVariableMapper variableMapper, int axis) {
        if (emitter.getOpcode() == Opcodes.LLOAD) {
            VarInsnNode localVarLoad = (VarInsnNode) emitter;
            localVarLoad.setOpcode(Opcodes.ILOAD);
            localVarLoad.var += axis;
        }
    }

    private static void remapInstruction(AbstractInsnNode instruction, LocalVariableMapper variableMapper) {
        if (instruction instanceof VarInsnNode localVar) {
            localVar.var = variableMapper.mapLocalVariable(localVar.var);
        } else if (instruction instanceof IincInsnNode iincNode) {
            iincNode.var = variableMapper.mapLocalVariable(iincNode.var);
        }
    }

    private static String insnToString(AbstractInsnNode instruction) {
        if (instruction instanceof MethodInsnNode methodCall) {
            String callType = switch (instruction.getOpcode()) {
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

    public static MethodNode copy(MethodNode method) {
        ClassNode classNode = new ClassNode();
        //MethodNode other = new MethodNode();
        method.accept(classNode);
        return classNode.methods.get(0);
    }

    private static <T extends Value> T topOfFrame(Frame<T> frame) {
        return frame.getStack(frame.getStackSize() - 1);
    }

    public static boolean shouldModifyClass(ClassNode classNode, MappingResolver map) {
        loadData(map);
        return transformsToApply.containsKey(classNode.name);
    }

    public static void modifyDescriptor(MethodNode methodNode, Set<Integer> expandedVariables) {
        Type returnType = Type.getReturnType(methodNode.desc);
        Type[] args = Type.getArgumentTypes(methodNode.desc);

        List<Type> newArgumentTypes = new ArrayList<>();
        int i = 0;
        if ((methodNode.access & Opcodes.ACC_STATIC) == 0) i++;
        for (Type argument : args) {
            if (expandedVariables.contains(i)) {
                for (int j = 0; j < 3; j++) newArgumentTypes.add(Type.INT_TYPE);
            } else {
                newArgumentTypes.add(argument);
            }

            i += argument.getSize();
        }

        methodNode.desc = modifyDescriptor(methodNode.desc, expandedVariables, (methodNode.access & Opcodes.ACC_STATIC) != 0, true);
    }

    public static String modifyDescriptor(String descriptor, Collection<Integer> expandedVariables, boolean isStatic, boolean adjustForVarWidth) {
        Type returnType = Type.getReturnType(descriptor);
        Type[] args = Type.getArgumentTypes(descriptor);

        List<Type> newArgumentTypes = new ArrayList<>();
        int i = 0;
        if (!isStatic) i++;
        for (Type argument : args) {
            if (expandedVariables.contains(i)) {
                for (int j = 0; j < 3; j++) newArgumentTypes.add(Type.INT_TYPE);
            } else {
                newArgumentTypes.add(argument);
            }

            i += adjustForVarWidth ? argument.getSize() : 1;
        }

        return Type.getMethodDescriptor(returnType, newArgumentTypes.toArray(Type[]::new));
    }

    public static void loadData(MappingResolver map) {
        if (loaded) return;

        JsonParser parser = new JsonParser();
        JsonObject root;
        try {
            InputStream is = LongPosTransformer.class.getResourceAsStream(REMAP_PATH);
            root = parser.parse(new String(is.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ASM light engine remap config");
        }

        Map<String, List<String>> superClassesPerClass = new HashMap<>();
        JsonObject classInfoJson = root.getAsJsonObject("class_info");
        for (Map.Entry<String, JsonElement> entry : classInfoJson.entrySet()) {
            String className = map.mapClassName("intermediary", entry.getKey().replace('/', '.')).replace('.', '/');
            List<String> superClasses = new ArrayList<>();
            JsonArray superClassArray = entry.getValue().getAsJsonObject().getAsJsonArray("superclasses");
            if (superClassArray != null) {
                superClassArray.forEach((element) -> {
                    superClasses.add(map.mapClassName("intermediary", element.getAsString().replace('/', '.')).replace('.', '/'));
                });
                superClassesPerClass.put(className, superClasses);
            }

            JsonArray transformArray = entry.getValue().getAsJsonObject().getAsJsonArray("transform");
            if (transformArray != null) {
                List<String> methodsToTransform = new ArrayList<>();
                for (JsonElement transform : transformArray) {
                    JsonObject transformInfo = transform.getAsJsonObject();

                    String owner = entry.getKey();
                    JsonElement ownerElement = transformInfo.get("owner");
                    if (ownerElement != null) {
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
        for (Map.Entry<String, JsonElement> entry : methodInfoJson.entrySet()) {
            String[] parts = entry.getKey().split(" ");
            String[] moreParts = parts[0].split("#");
            MethodInfo methodInfo = new MethodInfo(entry.getValue().getAsJsonObject(), moreParts[0], moreParts[1], parts[1], map);
            methodInfoLookup.put(methodInfo.getMethodID(), methodInfo);

            List<String> superClasses = superClassesPerClass.get(methodInfo.getOriginalOwner());

            if (superClasses != null) {
                for (String superClass : superClasses) {
                    methodInfoLookup.put(superClass + "#" + methodInfo.getOriginalName() + " " + methodInfo.getOriginalDescriptor(), methodInfo);
                }
            }
        }

        JsonArray unpackers = root.get("unpacking").getAsJsonArray();
        for (int i = 0; i < 3; i++) {
            String unpacker = unpackers.get(i).getAsString();
            String[] ownerAndNamePlusDescriptor = unpacker.split(" ");
            String descriptor = ownerAndNamePlusDescriptor[1];
            String[] ownerAndName = ownerAndNamePlusDescriptor[0].split("#");
            String owner = map.mapClassName("intermediary", ownerAndName[0].replace('/', '.'));
            String methodName = map.mapMethodName("intermediary", ownerAndName[0].replace('/', '.'), ownerAndName[1], descriptor);
            UNPACKING_METHODS[i] = (owner + "#" + methodName + " " + descriptor).replace('.', '/');
        }

        loaded = true;
    }
}
