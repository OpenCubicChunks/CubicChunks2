package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.ConstantFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.AnalysisResults;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.FutureMethodBinding;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingInterpreter;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ClassTransformInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.HierarchyTree;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodReplacement;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FieldID;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

//TODO: Duplicated classes do not pass class verification

/**
 * This class is responsible for transforming the methods and fields of a single class according to the configuration. See {@link io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader}
 * <br><br>
 * <b>Definitions:</b>
 * <ul>Emitter: Any instruction that pushes one or more values onto the stack</ul>
 * <ul>Consumer: Any instruction that pops one or more values from the stack</ul>
 */
public class TypeTransformer {
    //Directory where the transformed classes will be written to for debugging purposes
    private static final Path OUT_DIR = FabricLoader.getInstance().getGameDir().resolve("transformed");
    //Postfix that gets appended to some names to prevent conflicts
    public static final String MIX = "$$cc_transformed";
    //A value that should be passed to transformed constructors. Any other value will cause an error
    public static final int MAGIC = 0xDEADBEEF;
    //When safety is enabled, if a long-pos method is called for a 3-int object a warning will be created. This keeps track of all warnings.
    private static final Set<String> warnings = new HashSet<>();
    //Path to file where errors should be logged
    private static final Path ERROR_LOG = FabricLoader.getInstance().getGameDir().resolve("errors.log");

    //The global configuration loaded by ConfigLoader
    private final Config config;
    //The original class node
    private final ClassNode classNode;
    //When the class is being duplicated this is the new class.
    private final ClassNode newClassNode;
    //Stores all the analysis results per method
    private final Map<MethodID, AnalysisResults> analysisResults = new HashMap<>();
    //Keeps track of bindings to un-analyzed methods
    private final Map<MethodID, List<FutureMethodBinding>> futureMethodBindings = new HashMap<>();
    //Stores values for each field in the class. These can be bound (set same type) like any other values and allows
    //for easy tracking of the transform-type of a field
    private final AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues;
    //Per-class configuration
    private final ClassTransformInfo transformInfo;
    //The field ID (owner, name, desc) of a field which stores whether an instance was created with a transformed constructor and has transformed fields
    private FieldID isTransformedField;
    private boolean hasTransformedFields;
    //Whether safety checks/dispatches/warnings should be inserted into the code.
    private final boolean addSafety;
    //Stores the lambdaTransformers that need to be added
    private final Set<MethodNode> lambdaTransformers = new HashSet<>();
    //Stores any other methods that need to be added. There really isn't much of a reason for these two to be separate.
    private final Set<MethodNode> newMethods = new HashSet<>();

    //If the class is being duplicated, this is the name of the new class.
    private final String renameTo;

    /**
     * Constructs a new TypeTransformer for a given class.
     * @param config The global configuration loaded by ConfigLoader
     * @param classNode The original class node
     * @param duplicateClass Whether the class should be duplicated
     * @param addSafety Whether safety checks/dispatches/warnings should be inserted into the code.
     */
    public TypeTransformer(Config config, ClassNode classNode, boolean duplicateClass, boolean addSafety) {
        if(duplicateClass && addSafety){
            throw new IllegalArgumentException("Cannot duplicate a class and add safety checks at the same time");
        }

        this.config = config;
        this.classNode = classNode;
        this.fieldPseudoValues = new AncestorHashMap<>(config.getHierarchy());
        this.addSafety = addSafety;

        //Create field pseudo values
        for(var field: classNode.fields){
            TransformTrackingValue value = new TransformTrackingValue(Type.getType(field.desc), fieldPseudoValues);
            fieldPseudoValues.put(new FieldID(Type.getObjectType(classNode.name), field.name, Type.getType(field.desc)), value);
        }

        //Extract per-class config from the global config
        this.transformInfo = config.getClasses().get(Type.getObjectType(classNode.name));

        if(duplicateClass){
            //Simple way of copying the class node
            this.newClassNode = new ClassNode();

            classNode.accept(newClassNode);

            renameTo = newClassNode.name + "_transformed";

            //Since we know that any isntance of a class will have transformed fields there is no need to add a field to check for it.
            isTransformedField = null;
        }else{
            this.newClassNode = null;
            renameTo = null;
        }
    }

    /**
     * Should be called after all transforms have been applied.
     */
    public void cleanUpTransform(){
        if(newClassNode != null){
            //If the class is duplicated/renamed, this changes the name of the owner of all method/field accesses in all methods
            ASMUtil.rename(newClassNode, newClassNode.name + "_transformed");
        }

        if(newClassNode != null){
            //There is no support for inner classes so we remove them
            newClassNode.innerClasses.removeIf(
                    c -> c.name.contains(classNode.name)
            );

            //Same thing for nest members
            newClassNode.nestMembers.removeIf(
                    c -> c.contains(classNode.name)
            );
        }else{
            //Add methods that need to be added

            classNode.methods.addAll(lambdaTransformers);
            classNode.methods.addAll(newMethods);
        }

        if(newClassNode == null){
            //See documentation for makeFieldCasts
            makeFieldCasts();
        }
    }

    /**
     * Creates a copy of the method and transforms it according to the config. This method then gets added to the necessary class.
     * The main goal of this method is to create the transform context. It then passes that on to the necessary methods. This method does not modify the method much.
     * @param methodNode The method to transform.
     */
    public void transformMethod(MethodNode methodNode) {
        long start = System.currentTimeMillis();

        //Look up the analysis results for this method
        MethodID methodID = new MethodID(classNode.name, methodNode.name, methodNode.desc, MethodID.CallType.VIRTUAL); //Call subType doesn't matter much
        AnalysisResults results = analysisResults.get(methodID);

        if(results == null){
            throw new RuntimeException("Method " + methodID + " not analyzed");
        }

        //Create or get the new method node
        MethodNode newMethod;

        if(newClassNode != null) {
            //The method is duplicated so we don't need to create a new one
            newMethod = newClassNode.methods.stream().filter(m -> m.name.equals(methodNode.name) && m.desc.equals(methodNode.desc)).findFirst().orElse(null);
        }else{
            //Create a copy of the method
            newMethod = ASMUtil.copy(methodNode);
            //Add it to newMethods so that it gets added later and doesn't cause a ConcurrentModificationException if iterating over the methods.
            newMethods.add(newMethod);
            markSynthetic(newMethod, "AUTO-TRANSFORMED", methodNode.name + methodNode.desc);
        }

        if(newMethod == null){
            throw new RuntimeException("Method " + methodID + " not found in new class");
        }

        if((methodNode.access & Opcodes.ACC_ABSTRACT) != 0){
            //If the method is abstract, we don't need to transform its code, just it's descriptor
            TransformSubtype[] actualParameters = new TransformSubtype[results.argTypes().length - 1];
            System.arraycopy(results.argTypes(), 1, actualParameters, 0, actualParameters.length);

            String oldDesc = methodNode.desc;

            //Change descriptor
            newMethod.desc = MethodParameterInfo.getNewDesc(TransformSubtype.of(null), actualParameters, methodNode.desc);

            if(oldDesc.equals(newMethod.desc)){
                newMethod.name += MIX;
            }

            System.out.println("Transformed method '" + methodID + "' in " + (System.currentTimeMillis() - start) + "ms");

            //Create the parameter name table
            if(newMethod.parameters != null) {
                List<ParameterNode> newParameters = new ArrayList<>();
                for (int i = 0; i < newMethod.parameters.size(); i++) {
                    ParameterNode parameterNode = newMethod.parameters.get(i);
                    TransformSubtype parameterType = actualParameters[i];

                    if(parameterType.getTransformType() == null || !parameterType.getSubtype().equals(TransformSubtype.SubType.NONE)){
                        //There is no transform type for this parameter, so we don't need to change it
                        newParameters.add(parameterNode);
                    }else{
                        //There is a transform type for this parameter, so we need to change it
                        for(String suffix: parameterType.getTransformType().getPostfix()){
                            newParameters.add(new ParameterNode(parameterNode.name + suffix, parameterNode.access));
                        }
                    }
                }
                newMethod.parameters = newParameters;
            }
            return;
        }

        //See TransformContext
        AbstractInsnNode[] insns = newMethod.instructions.toArray();
        boolean[] expandedEmitter = new boolean[insns.length];
        boolean[] expandedConsumer = new boolean[insns.length];
        int[][] vars = new int[insns.length][methodNode.maxLocals];
        TransformSubtype[][] varTypes = new TransformSubtype[insns.length][methodNode.maxLocals];

        int maxLocals = 0;

        //Generate var table
        //Note: This variable table might not work with obfuscated bytecode. It relies on variables being added and removed in a stack-like fashion
        for(int i = 0; i < insns.length; i++){
            Frame<TransformTrackingValue> frame = results.frames()[i];
            if(frame == null) continue;
            int newIndex = 0;
            for(int j = 0; j < methodNode.maxLocals;){
                vars[i][j] = newIndex;
                varTypes[i][j] = frame.getLocal(j).getTransform();
                newIndex += frame.getLocal(j).getTransformedSize();

                j += frame.getLocal(j).getSize();
            }
            maxLocals = Math.max(maxLocals, newIndex);
        }

        VariableManager varCreator = new VariableManager(maxLocals, insns.length);

        //Analysis results come from the original method, and we need to transform the new method, so we need to be able to get the new instructions that correspond to the old ones
        Map<AbstractInsnNode, Integer> indexLookup = new HashMap<>();

        AbstractInsnNode[] oldInsns = methodNode.instructions.toArray();

        for(int i = 0; i < oldInsns.length; i++){
            indexLookup.put(insns[i], i);
            indexLookup.put(oldInsns[i], i);
        }

        BytecodeFactory[][][] syntheticEmitters = new BytecodeFactory[insns.length][][];

        AbstractInsnNode[] instructions = newMethod.instructions.toArray();
        Frame<TransformTrackingValue>[] frames = results.frames();

        //Resolve the method parameter infos
        MethodParameterInfo[] methodInfos = new MethodParameterInfo[insns.length];
        for(int i = 0; i < insns.length; i++){
            AbstractInsnNode insn = instructions[i];
            Frame<TransformTrackingValue> frame = frames[i];
            if(insn instanceof MethodInsnNode methodCall){
                MethodID calledMethod = MethodID.from(methodCall);

                TransformTrackingValue returnValue = null;
                if (calledMethod.getDescriptor().getReturnType() != Type.VOID_TYPE) {
                    returnValue = ASMUtil.getTop(frames[i + 1]);
                }

                int argCount = ASMUtil.argumentCount(calledMethod.getDescriptor().getDescriptor(), calledMethod.isStatic());
                TransformTrackingValue[] args = new TransformTrackingValue[argCount];
                for (int j = 0; j < args.length; j++) {
                    args[j] = frame.getStack(frame.getStackSize() - argCount + j);
                }

                //Lookup the possible method transforms
                List<MethodParameterInfo> infos = config.getMethodParameterInfo().get(calledMethod);

                if(infos != null) {
                    //Check all possible transforms to see if any of them match
                    for (MethodParameterInfo info : infos) {
                        if (info.getTransformCondition().checkValidity(returnValue, args) == 1) {
                            methodInfos[i] = info;
                            break;
                        }
                    }
                }
            }
        }

        //Create context
        TransformContext context = new TransformContext(newMethod, results, instructions, expandedEmitter, expandedConsumer, new boolean[insns.length], syntheticEmitters, vars, varTypes, varCreator, indexLookup, methodInfos);

        detectAllRemovedEmitters(newMethod, context);

        createEmitters(context);

        transformMethod(methodNode, newMethod, context);

        System.out.println("Transformed method '" + methodID + "' in " + (System.currentTimeMillis() - start) + "ms");
    }

    /**
     * Finds all emitters that need to be removed and marks them as such.
     * <br><br>
     * What is a removed emitter?<br>
     * In certain cases, multiple values will need to be used out of their normal order. For example, <code>var1</code> and <code>var2</code> both have transform-type
     * long -> (int "x", int "y", int "z"). If some code does <code>var1 == var2</code> then the transformed code needs to do <code>var1_x == var2_x && var1_y == var2_y && var1_z == var2_z</code>.
     * This means var1_x has to be loaded and then var2_x and then var1_y etc... This means we can't just expand the two emitters normally. That would leave the stack with
     * [var1_x, var1_y, var1_z, var2_x, var2_y, var2_z] and comparing that would need a lot of stack magic (DUP, SWAP, etc...). So what we do is remove these emitters from the code
     * and instead create BytecodeFactories that allow the values to be generated in any order that is needed.
     *
     * @param newMethod The method to transform
     * @param context The transform context
     */
    private void detectAllRemovedEmitters(MethodNode newMethod, TransformContext context) {
        boolean[] prev;
        Frame<TransformTrackingValue>[] frames = context.analysisResults().frames();

        //This code keeps trying to find new removed emitters until it can't find any more.

        do{
            //Keep detecting new ones until we don't find any more
            prev = Arrays.copyOf(context.removedEmitter(), context.removedEmitter().length);

            for(int i = 0; i < context.removedEmitter().length; i++){
                AbstractInsnNode instruction = context.instructions()[i];
                Frame<TransformTrackingValue> frame = frames[i];

                int consumed = ASMUtil.stackConsumed(instruction);
                int opcode = instruction.getOpcode();

                if(instruction instanceof MethodInsnNode methodCall){
                    MethodParameterInfo info = context.methodInfos()[i];
                    if(info != null && info.getReplacement() != null){
                        if(info.getReplacement().changeParameters()){
                            //If any method parameters are changed we remove all of it's emitters
                            for (int j = 0; j < consumed; j++) {
                                TransformTrackingValue arg = frame.getStack(frame.getStackSize() - consumed + j);
                                markRemoved(arg, context);
                            }
                        }
                    }
                }else if(opcode == Opcodes.LCMP || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG || opcode == Opcodes.IF_ICMPEQ || opcode == Opcodes.IF_ICMPNE || opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE){
                    //Get two values
                    TransformTrackingValue left = frame.getStack(frame.getStackSize() - 2);
                    TransformTrackingValue right = frame.getStack(frame.getStackSize() - 1);

                    //We can assume the two transforms are the same. This check is just to make sure there isn't a bug in the analyzer
                    if(!left.getTransform().equals(right.getTransform())){
                        throw new RuntimeException("The two transforms should be the same");
                    }

                    //If the transform has more than one subType we will need to separate them so we must remove the emitter
                    if(left.getTransform().transformedTypes(left.getType()).size() > 1){
                        markRemoved(left, context);
                        markRemoved(right, context);
                    }
                }

                //If any of the values used by any instruction are removed we need to remove all the other values emitters
                boolean remove = false;
                for(int j = 0; j < consumed; j++){
                    TransformTrackingValue arg = frame.getStack(frame.getStackSize() - consumed + j);
                    if(isRemoved(arg, context)){
                        remove = true;
                    }
                }

                if(remove){
                    for(int j = 0; j < consumed; j++){
                        TransformTrackingValue arg = frame.getStack(frame.getStackSize() - consumed + j);
                        markRemoved(arg, context);
                    }
                }
            }
        }while(!Arrays.equals(prev, context.removedEmitter()));
    }

    /**
     * Creates the synthetic emitters mentioned in {@link #detectAllRemovedEmitters(MethodNode, TransformContext)}
     * @param context Transform context
     */
    private void createEmitters(TransformContext context) {
        //If a value cna come from multiple paths of execution we need to store it in a temporary variable (because it is simpler). (May use more than one variable for transform type
        // expansions)

        Map<AbstractInsnNode, int[][]> tempVariables = new HashMap<>();

        Map<TransformTrackingValue, int[]> variableSlots = new HashMap<>();

        for (int i = 0; i < context.instructions.length; i++) {
            if(context.removedEmitter()[i]){
                AbstractInsnNode instruction = context.instructions()[i];
                Frame<TransformTrackingValue> frame = context.analysisResults().frames()[i];
                Frame<TransformTrackingValue> nextFrame = context.analysisResults().frames()[i + 1];

                int amountValuesGenerated = ASMUtil.numValuesReturned(frame, instruction);
                TransformTrackingValue[] values = new TransformTrackingValue[amountValuesGenerated];

                int[][] saveInto = new int[amountValuesGenerated][];

                for (int j = 0; j < amountValuesGenerated; j++) {
                    TransformTrackingValue value = values[j] = nextFrame.getStack(nextFrame.getStackSize() - amountValuesGenerated + j);
                    if(variableSlots.containsKey(value)){
                        saveInto[j] = variableSlots.get(value);
                    }else{
                        //Check if we need to create a save slot
                        Set<TransformTrackingValue> relatedValues = value.getAllRelatedValues();

                        Set<AbstractInsnNode> allPossibleSources = relatedValues.stream().map(TransformTrackingValue::getSource).reduce(new HashSet<>(), (a, b) -> {
                            a.addAll(b);
                            return a;
                        }).stream().map(context::getActual).collect(Collectors.toSet());

                        Set<AbstractInsnNode> allPossibleConsumers = relatedValues.stream().map(TransformTrackingValue::getConsumers).reduce(new HashSet<>(), (a, b) -> {
                            a.addAll(b);
                            return a;
                        }).stream().map(context::getActual).collect(Collectors.toSet());

                        //Just a debug check
                        if(!allPossibleSources.contains(instruction)){
                            throw new RuntimeException("The value " + value + " is not related to the instruction " + instruction);
                        }

                        if(allPossibleSources.size() > 1){
                            //We need to create a temporary variable
                            //We find the earliest and last instructions that create/use this instruction
                            int earliest = Integer.MAX_VALUE;
                            int last = Integer.MIN_VALUE;

                            for(AbstractInsnNode source : allPossibleSources){
                                int index = context.indexLookup().get(source);

                                if(index < earliest){
                                    earliest = index;
                                }

                                if(index > last){
                                    last = index;
                                }
                            }

                            for(AbstractInsnNode consumer: allPossibleConsumers){
                                int index = context.indexLookup().get(consumer);

                                if(index > last){
                                    last = index;
                                }

                                if(index < earliest){
                                    earliest = index;
                                }
                            }

                            List<Type> types = value.transformedTypes();
                            int[] saveSlots = new int[types.size()];

                            for(int k = 0; k < types.size(); k++){
                                saveSlots[k] = context.variableManager.allocate(earliest, last, types.get(k));
                            }

                            variableSlots.put(value, saveSlots);
                            saveInto[j] = saveSlots;
                        }
                    }
                }

                tempVariables.put(instruction, saveInto);
            }
        }

        for (int i = 0; i < context.instructions.length; i++) {
            if(context.removedEmitter()[i]){
                generateEmitter(context, tempVariables, i);
            }
        }
    }

    private void generateEmitter(TransformContext context, Map<AbstractInsnNode, int[][]> tempVariables, int index) {
        AbstractInsnNode instruction = context.instructions[index];
        Frame<TransformTrackingValue> frame = context.analysisResults.frames()[index];
        Frame<TransformTrackingValue> nextFrame = context.analysisResults.frames()[index + 1];

        int[][] saveSlots = tempVariables.get(instruction);

        int numValuesToSave = ASMUtil.numValuesReturned(nextFrame, instruction);
        
        TransformTrackingValue[] valuesToSave = new TransformTrackingValue[numValuesToSave];
        for(int i = 0; i < numValuesToSave; i++){
            valuesToSave[i] = nextFrame.getStack(nextFrame.getStackSize() - numValuesToSave + i);
        }

        if(numValuesToSave > 1){
            //We save them all into local variables to make our lives easier
            InsnList store = new InsnList();
            BytecodeFactory[][] syntheticEmitters = new BytecodeFactory[numValuesToSave][];

            for(int i = 0; i < numValuesToSave; i++){
                Pair<BytecodeFactory, BytecodeFactory[]> storeAndLoad = makeStoreAndLoad(context, valuesToSave[i], saveSlots == null ? null : saveSlots[i]);
                store.add(storeAndLoad.getFirst().generate());
                syntheticEmitters[i] = storeAndLoad.getSecond();
            }

            //Insert the store
            context.target.instructions.insert(instruction, store);

            context.syntheticEmitters[index] = syntheticEmitters;
        }else{
            if(saveSlots != null && saveSlots[0] != null){
                //We NEED to save the value into a local variable
                Pair<BytecodeFactory, BytecodeFactory[]> storeAndLoad = makeStoreAndLoad(context, valuesToSave[0], saveSlots[0]);
                context.target.instructions.insert(instruction, storeAndLoad.getFirst().generate());
                context.syntheticEmitters[index] = new BytecodeFactory[][]{
                    storeAndLoad.getSecond()
                };
            }else {
                boolean useDefault = true;
                context.syntheticEmitters[index] = new BytecodeFactory[1][];

                if (instruction instanceof VarInsnNode varNode) {
                    //Will be a load
                    int slot = varNode.var;
                    //Check that the value is in the slot at every point that we would need it
                    boolean canUseVar = true;

                    TransformTrackingValue varValue = frame.getLocal(slot); //The actual value in the slot does not have the same identity as the one on the stack in the next frame

                    for(AbstractInsnNode consumer: valuesToSave[0].getConsumers()){
                        int insnIndex = context.indexLookup().get(consumer);
                        Frame<TransformTrackingValue> consumerFrame = context.analysisResults.frames()[insnIndex];
                        if(consumerFrame.getLocal(slot) != varValue){
                            canUseVar = false;
                            break;
                        }
                    }

                    if(canUseVar){
                        useDefault = false;
                        int newSlot = context.varLookup[index][slot];

                        List<Type> transformTypes = valuesToSave[0].transformedTypes();

                        BytecodeFactory[] loads = new BytecodeFactory[transformTypes.size()];
                        for(int i = 0; i < loads.length; i++){
                            int finalI = i;
                            int finalSlot = newSlot;
                            loads[i] = () -> {
                                InsnList list = new InsnList();
                                list.add(new VarInsnNode(transformTypes.get(finalI).getOpcode(Opcodes.ILOAD), finalSlot));
                                return list;
                            };
                            newSlot += transformTypes.get(i).getSize();
                        }

                        context.syntheticEmitters[index][0] = loads;

                        //Remove the original load
                        context.target.instructions.remove(instruction);
                    }
                }else if(ASMUtil.isConstant(instruction)){
                    useDefault = false;

                    //If it is a constant we can just copy it
                    Object constant = ASMUtil.getConstant(instruction);

                    context.target.instructions.remove(instruction);

                    //Still need to expand it
                    if(valuesToSave[0].getTransformType() != null && valuesToSave[0].getTransform().getSubtype() == TransformSubtype.SubType.NONE) {
                        BytecodeFactory[] expansion = valuesToSave[0].getTransformType().getConstantReplacements().get(constant);
                        if(expansion == null){
                            throw new IllegalStateException("No expansion for constant " + constant + " of type " + valuesToSave[0].getTransformType());
                        }
                        context.syntheticEmitters[index][0] = expansion;
                    }else{
                        context.syntheticEmitters[index][0] = new BytecodeFactory[]{new ConstantFactory(constant)};
                    }
                }

                if(useDefault){
                    //We need to save the value into a local variable
                    Pair<BytecodeFactory, BytecodeFactory[]> storeAndLoad = makeStoreAndLoad(context, valuesToSave[0], null);
                    context.target.instructions.insert(instruction, storeAndLoad.getFirst().generate());
                    context.syntheticEmitters[index][0] = storeAndLoad.getSecond();
                }
            }
        }
    }

    private Pair<BytecodeFactory, BytecodeFactory[]> makeStoreAndLoad(TransformContext context, TransformTrackingValue value, @Nullable int[] slots) {
        if(slots == null){
            //Make slots
            slots = new int[value.transformedTypes().size()];

            //Get extent of value
            int earliest = Integer.MAX_VALUE;
            int last = Integer.MIN_VALUE;

            Set<TransformTrackingValue> relatedValues = value.getAllRelatedValues();

            Set<AbstractInsnNode> allPossibleSources = relatedValues.stream().map(TransformTrackingValue::getSource).reduce(new HashSet<>(), (a, b) -> {
                a.addAll(b);
                return a;
            });

            Set<AbstractInsnNode> allPossibleConsumers = relatedValues.stream().map(TransformTrackingValue::getConsumers).reduce(new HashSet<>(), (a, b) -> {
                a.addAll(b);
                return a;
            });

            for(AbstractInsnNode source : allPossibleSources){
                int index = context.indexLookup().get(source);

                if(index < earliest){
                    earliest = index;
                }

                if(index > last){
                    last = index;
                }
            }

            for(AbstractInsnNode consumer: allPossibleConsumers){
                int index = context.indexLookup().get(consumer);

                if(index > last){
                    last = index;
                }

                if(index < earliest){
                    earliest = index;
                }
            }

            List<Type> types = value.transformedTypes();

            for(int k = 0; k < types.size(); k++){
                slots[k] = context.variableManager.allocate(earliest, last, types.get(k));
            }
        }

        int[] finalSlots = slots;
        List<Type> types = value.transformedTypes();
        BytecodeFactory store = () -> {
            InsnList list = new InsnList();
            for (int i = finalSlots.length - 1; i >= 0; i--) {
                int slot = finalSlots[i];
                Type type = types.get(i);
                list.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), slot));
            }
            return list;
        };

        BytecodeFactory[] load = new BytecodeFactory[types.size()];
        for(int i = 0; i < types.size(); i++){
            Type type = types.get(i);
            int finalI = i;
            load[i] = () -> {
                InsnList list = new InsnList();
                list.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), finalSlots[finalI]));
                return list;
            };
        }

        return new Pair<>(store, load);
    }

    /**
     * Determine if the given value's emitters are removed
     * @param value The value to check
     * @param context Transform context
     * @return True if the value's emitters are removed, false otherwise
     */
    private boolean isRemoved(TransformTrackingValue value, TransformContext context){
        boolean isAllRemoved = true;
        boolean isAllPresent = true;

        for(AbstractInsnNode source: value.getSource()){
            int sourceIndex = context.indexLookup().get(source);
            if(context.removedEmitter()[sourceIndex]){
                isAllPresent = false;
            }else{
                isAllRemoved = false;
            }
        }

        if(!(isAllPresent || isAllRemoved)){
            throw new IllegalStateException("Value is neither all present nor all removed");
        }

        return isAllRemoved;
    }

    /**
     * Marks all the emitters of the given value as removed
     * @param value The value whose emitters to mark as removed
     * @param context Transform context
     */
    private void markRemoved(TransformTrackingValue value, TransformContext context){
        for(AbstractInsnNode source: value.getSource()){
            int sourceIndex = context.indexLookup().get(source);
            context.removedEmitter()[sourceIndex] = true;
        }
    }

    /**
     * Actually modifies the method
     * @param oldMethod The original method, may be modified for safety checks
     * @param methodNode The method to modify
     * @param context Transform context
     */
    private void transformMethod(MethodNode oldMethod, MethodNode methodNode, TransformContext context) {
        //Step One: change descriptor
        TransformSubtype[] actualParameters;
        if((methodNode.access & Opcodes.ACC_STATIC) == 0){
            actualParameters =  new TransformSubtype[context.analysisResults().argTypes().length - 1];
            System.arraycopy(context.analysisResults().argTypes(), 1, actualParameters, 0, actualParameters.length);
        }else{
            actualParameters = context.analysisResults().argTypes();
        }

        //Change descriptor
        String newDescriptor = MethodParameterInfo.getNewDesc(TransformSubtype.of(null), actualParameters, methodNode.desc);
        methodNode.desc = newDescriptor;

        boolean renamed = false;

        //If the method's descriptor's didn't change then we  need to change the name otherwise it will throw errors
        if(newDescriptor.equals(oldMethod.desc) && newClassNode == null){
            methodNode.name += MIX;
            renamed = true;
        }

        //Change variable names to make it easier to debug
        modifyVariableTable(methodNode, context);

        //Change the code
        modifyCode(methodNode, context);

        if(!ASMUtil.isStatic(methodNode)) {
            if (renamed) {
                //If the method was renamed then we need to make sure that calls to the normal method end up calling the renamed method
                //TODO: Check if dispatch is actually necessary. This could be done by checking if the method accesses any transformed fields

                InsnList dispatch = new InsnList();
                LabelNode label = new LabelNode();

                //If not transformed then do nothing, otherwise dispatch to the renamed method
                dispatch.add(jumpIfNotTransformed(label));

                //Dispatch to transformed. Because the descriptor didn't change, we don't need to transform any parameters.
                //TODO: This would need to actually transform parameters if say, the transform type was something like int -> (int "double_x")
                //This part pushes all the parameters onto the stack
                int index = 0;

                if (!ASMUtil.isStatic(methodNode)) {
                    dispatch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    index++;
                }

                for (Type arg : Type.getArgumentTypes(newDescriptor)) {
                    dispatch.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), index));
                    index += arg.getSize();
                }

                //Call the renamed method
                int opcode;
                if (ASMUtil.isStatic(methodNode)) {
                    opcode = Opcodes.INVOKESTATIC;
                } else {
                    opcode = Opcodes.INVOKESPECIAL;
                }
                dispatch.add(new MethodInsnNode(opcode, classNode.name, methodNode.name, methodNode.desc, false));

                //Return
                dispatch.add(new InsnNode(Type.getReturnType(methodNode.desc).getOpcode(Opcodes.IRETURN)));

                dispatch.add(label);

                //Insert the dispatch at the start of the method
                oldMethod.instructions.insertBefore(oldMethod.instructions.getFirst(), dispatch);
            } else if (addSafety && (methodNode.access & Opcodes.ACC_SYNTHETIC) == 0) {

                //This is different to the above because it actually emits a warning. This can be disabled by setting addSafety to false in the constructor
                //but this means that if a single piece of code calls the wrong method then everything could crash.
                InsnList dispatch = new InsnList();
                LabelNode label = new LabelNode();

                dispatch.add(jumpIfNotTransformed(label));

                //Emit warning
                dispatch.add(new LdcInsnNode(classNode.name));
                dispatch.add(new LdcInsnNode(oldMethod.name));
                dispatch.add(new LdcInsnNode(oldMethod.desc));
                dispatch.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/opencubicchunks/cubicchunks/mixin/transform/typetransformer/transformer/TypeTransformer", "emitWarning",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                    false));

                if (!ASMUtil.isStatic(methodNode)) {
                    //Push all the parameters onto the stack and transform them if needed
                    dispatch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    int index = 1;
                    for (Type arg : Type.getArgumentTypes(oldMethod.desc)) {
                        TransformSubtype argType = context.varTypes[0][index];
                        int finalIndex = index;
                        dispatch.add(argType.convertToTransformed(() -> {
                            InsnList load = new InsnList();
                            load.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), finalIndex));
                            return load;
                        }, lambdaTransformers, classNode.name));
                        index += arg.getSize();
                    }

                    dispatch.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.name, methodNode.name, methodNode.desc, false));
                    dispatch.add(new InsnNode(Type.getReturnType(methodNode.desc).getOpcode(Opcodes.IRETURN)));
                }

                dispatch.add(label);

                oldMethod.instructions.insertBefore(oldMethod.instructions.getFirst(), dispatch);
            }
        }
    }

    /**
     * Modifies the code of the method to use the transformed types instead of the original types
     * @param methodNode The method to modify
     * @param context The context of the transformation
     */
    private void modifyCode(MethodNode methodNode, TransformContext context) {
        AbstractInsnNode[] instructions = context.instructions();
        Frame<TransformTrackingValue>[] frames = context.analysisResults().frames();

        //Iterate through every instruction of the instructions array. We use the array because it will not change unlike methodNode.instructions
        for (int i = 0; i < instructions.length; i++) {
            try {
                if (context.removedEmitter()[i]) {
                    //If we have removed the emitter, we don't need to modify the code and trying to do so would cause an error anyways
                    continue;
                }

                AbstractInsnNode instruction = instructions[i];
                Frame<TransformTrackingValue> frame = frames[i];

                //Because of removed emitters, we have no guarantee that all the needed values will be on the stack when we need them. If this is set to false,
                //there will be no guarantee that the values will be on the stack. If it is true, the emitters will be inserted where they are needed
                boolean ensureValuesAreOnStack = true;

                int opcode = instruction.getOpcode();

                if (instruction instanceof MethodInsnNode methodCall) {
                    MethodID methodID = MethodID.from(methodCall);

                    //Get the return value (if it exists). It is on the top of the stack if the next frame
                    TransformTrackingValue returnValue = null;
                    if (methodID.getDescriptor().getReturnType() != Type.VOID_TYPE) {
                        returnValue = ASMUtil.getTop(frames[i + 1]);
                    }

                    //Get all the values that are passed to the method call
                    int argCount = ASMUtil.argumentCount(methodID.getDescriptor().getDescriptor(), methodID.isStatic());
                    TransformTrackingValue[] args = new TransformTrackingValue[argCount];
                    for (int j = 0; j < args.length; j++) {
                        args[j] = frame.getStack(frame.getStackSize() - argCount + j);
                    }

                    //Find replacement information for the method call
                    MethodParameterInfo info = context.methodInfos[i];
                    if (info != null && info.getReplacement() != null) {

                        applyReplacement(context, methodCall, info, args);

                        if (info.getReplacement().changeParameters()) {
                            //Because the replacement itself is already taking care of having all the values on the stack, we don't need to do anything, or we'll just have every value being duplicated
                            ensureValuesAreOnStack = false;
                        }
                    } else {
                        //If there is none, we create a default transform
                        if (returnValue != null && returnValue.getTransform().transformedTypes(returnValue.getType()).size() > 1) {
                            throw new IllegalStateException("Cannot generate default replacement for method with multiple return types '" + methodID + "'");
                        }

                        applyDefaultReplacement(context, methodCall, returnValue, args);
                    }
                } else if (instruction instanceof VarInsnNode varNode) {
                    /*
                     * There are two reasons this is needed.
                     * 1. Some values take up different amount of variable slots because of their transforms, so we need to shift all variables accesses'
                     * 2. When actually storing or loading a transformed value, we need to store all of it's transformed values correctly
                     */

                    //Get the shifted variable index
                    int originalVarIndex = varNode.var;
                    int newVarIndex = context.varLookup()[i][originalVarIndex];

                    //Base opcode makes it easier to determine what kind of instruction we are dealing with
                    int baseOpcode = switch (varNode.getOpcode()) {
                        case Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.LLOAD -> Opcodes.ILOAD;
                        case Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.LSTORE -> Opcodes.ISTORE;
                        default -> throw new IllegalStateException("Unknown opcode: " + varNode.getOpcode());
                    };

                    //If the variable is being loaded, it is in the current frame, if it is being stored, it will be in the next frame
                    TransformSubtype varType = context.varTypes()[i + (baseOpcode == Opcodes.ISTORE ? 1 : 0)][originalVarIndex];
                    //Get the actual types that need to be stored or loaded
                    List<Type> types = varType.transformedTypes(ASMUtil.getType(varNode.getOpcode()));

                    //Get the indices for each of these types
                    List<Integer> vars = new ArrayList<>();
                    for (Type subType : types) {
                        vars.add(newVarIndex);
                        newVarIndex += subType.getSize();
                    }

                    /*
                     * If the variable is being stored we must reverse the order of the types.
                     * This is because in the following code if a and b have transform-type long -> (int "x", int "y", int "z"):
                     *
                     * long b = a;
                     *
                     * The loading of a would get expanded to something like:
                     * ILOAD 3 Stack: [] -> [a_x]
                     * ILOAD 4 Stack: [a_x] -> [a_x, a_y]
                     * ILOAD 5 Stack: [a_x, a_y] -> [a_x, a_y, a_z]
                     *
                     * If the storing into b was in the same order it would be:
                     * ISTORE 3 Stack: [a_x, a_y, a_z] -> [a_x, a_y] (a_z gets stored into b_x)
                     * ISTORE 4 Stack: [a_x, a_y] -> [a_x] (a_y gets stored into b_y)
                     * ISTORE 5 Stack: [a_x] -> [] (a_x gets stored into b_z)
                     * And so we see that this ordering is wrong.
                     *
                     * To fix this, we reverse the order of the types.
                     * The previous example becomes:
                     * ISTORE 5 Stack: [a_x, a_y, a_z] -> [a_x, a_y] (a_z gets stored into b_z)
                     * ISTORE 4 Stack: [a_x, a_y] -> [a_x] (a_y gets stored into b_y)
                     * ISTORE 3 Stack: [a_x] -> [] (a_x gets stored into b_x)
                     */
                    if (baseOpcode == Opcodes.ISTORE) {
                        Collections.reverse(types);
                        Collections.reverse(vars);
                    }

                    //For the first operation we can just modify the original instruction instead of creating more
                    varNode.var = vars.get(0);
                    varNode.setOpcode(types.get(0).getOpcode(baseOpcode));

                    InsnList extra = new InsnList();

                    for (int j = 1; j < types.size(); j++) {
                        extra.add(new VarInsnNode(types.get(j).getOpcode(baseOpcode), vars.get(j))); //Creating the new instructions
                    }

                    context.target().instructions.insert(varNode, extra);
                } else if (instruction instanceof IincInsnNode iincNode) {
                    //We just need to shift the index of the variable because incrementing transformed values is not supported
                    int originalVarIndex = iincNode.var;
                    int newVarIndex = context.varLookup()[i][originalVarIndex];
                    iincNode.var = newVarIndex;
                } else if (ASMUtil.isConstant(instruction)) {
                    //Check if value is transformed
                    ensureValuesAreOnStack = false;
                    TransformTrackingValue value = ASMUtil.getTop(frames[i + 1]);
                    if (value.getTransformType() != null) {
                        if (value.getTransform().getSubtype() != TransformSubtype.SubType.NONE) {
                            throw new IllegalStateException("Cannot expand constant value of subType " + value.getTransform().getSubtype());
                        }

                        Object constant = ASMUtil.getConstant(instruction);

                        /*
                         * Check if there is a given constant replacement for this value an example of this is where Long.MAX_VALUE is used as a marker
                         * for an invalid position. To convert it to 3int we turn it into (Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                         */
                        BytecodeFactory[] replacement = value.getTransformType().getConstantReplacements().get(constant);
                        if (replacement == null) {
                            throw new IllegalStateException("Cannot expand constant value of subType " + value.getTransformType());
                        }

                        InsnList newInstructions = new InsnList();
                        for (BytecodeFactory factory : replacement) {
                            newInstructions.add(factory.generate());
                        }

                        context.target().instructions.insert(instruction, newInstructions);
                        context.target().instructions.remove(instruction); //Remove the original instruction
                    }
                } else if (
                    opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE || opcode == Opcodes.IF_ICMPEQ || opcode == Opcodes.IF_ICMPNE ||
                        opcode == Opcodes.LCMP || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG
                ) {
                    /*
                     * Transforms for equality comparisons
                     * How it works:
                     *
                     * If these values have transform-type long -> (int "x", int "y", int "z")
                     *
                     * LLOAD 1
                     * LLOAD 2
                     * LCMP
                     * IF_EQ -> LABEL
                     * ...
                     * LABEL:
                     * ...
                     *
                     * Becomes
                     *
                     * ILOAD 1
                     * ILOAD 4
                     * IF_ICMPNE -> FAILURE
                     * ILOAD 2
                     * ILOAD 5
                     * IF_ICMPNE -> FAILURE
                     * ILOAD 3
                     * ILOAD 6
                     * IF_ICMPEQ -> SUCCESS
                     * FAILURE:
                     * ...
                     * SUCCESS:
                     * ...
                     *
                     * Similarly
                     * LLOAD 1
                     * LLOAD 2
                     * LCMP
                     * IF_NE -> LABEL
                     * ...
                     * LABEL:
                     * ...
                     *
                     * Becomes
                     *
                     * ILOAD 1
                     * ILOAD 4
                     * IF_ICMPNE -> SUCCESS
                     * ILOAD 2
                     * ILOAD 5
                     * IF_ICMPNE -> SUCCESS
                     * ILOAD 3
                     * ILOAD 6
                     * IF_ICMPNE -> SUCCESS
                     * FAILURE:
                     * ...
                     * SUCCESS:
                     * ...
                     */

                    //Get the actual values that are being compared
                    TransformTrackingValue left = frame.getStack(frame.getStackSize() - 2);
                    TransformTrackingValue right = frame.getStack(frame.getStackSize() - 1);

                    JumpInsnNode jump; //The actual jump instruction. Note: LCMP, FCMPL, FCMPG, DCMPL, DCMPG are not jump, instead, the next instruction (IFEQ, IFNE etc..) is jump
                    int baseOpcode; //The type of comparison. IF_IMCPEQ or IF_ICMPNE

                    //Used to remember to delete CMP instructions
                    boolean separated = false;

                    if (opcode == Opcodes.LCMP || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG) {
                        TransformTrackingValue result =
                            ASMUtil.getTop(frames[i + 1]); //The result is on the top of the next frame and gets consumer by the jump. This is how we find the jump

                        if (result.getConsumers().size() != 1) {
                            throw new IllegalStateException("Expected one consumer, found " + result.getConsumers().size());
                        }

                        //Because the consumers are from the old method we have to call context.getActual
                        jump = context.getActual((JumpInsnNode) result.getConsumers().iterator().next());

                        baseOpcode = switch (jump.getOpcode()) {
                            case Opcodes.IFEQ -> Opcodes.IF_ICMPEQ;
                            case Opcodes.IFNE -> Opcodes.IF_ICMPNE;
                            default -> throw new IllegalStateException("Unknown opcode: " + jump.getOpcode());
                        };

                        separated = true;
                    } else {
                        jump = context.getActual((JumpInsnNode) instruction); //The instruction is the jump

                        baseOpcode = switch (opcode) {
                            case Opcodes.IF_ACMPEQ, Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPEQ;
                            case Opcodes.IF_ACMPNE, Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPNE;
                            default -> throw new IllegalStateException("Unknown opcode: " + opcode);
                        };
                    }

                    if (!left.getTransform().equals(right.getTransform())) {
                        throw new IllegalStateException("Expected same transform, found " + left.getTransform() + " and " + right.getTransform());
                    }

                    //Only modify the jump if both values are transformed
                    if (left.getTransformType() != null && right.getTransformType() != null) {
                        ensureValuesAreOnStack = false;
                        List<Type> types = left.transformedTypes(); //Get the actual types that will be converted

                        if (types.size() == 1) {
                            InsnList replacement = ASMUtil.generateCompareAndJump(types.get(0), baseOpcode, jump.label);
                            context.target.instructions.insert(jump, replacement);
                            context.target.instructions.remove(jump); //Remove the previous jump instruction
                        } else {
                            //Get the replacements for each component
                            BytecodeFactory[] replacementLeft = context.getSyntheticEmitter(left);
                            BytecodeFactory[] replacementRight = context.getSyntheticEmitter(right);

                            //Create failure and success label
                            LabelNode success = jump.label;
                            LabelNode failure = new LabelNode();

                            InsnList newCmp = new InsnList();

                            for (int j = 0; j < types.size(); j++) {
                                Type subType = types.get(j);
                                //Load the single components from left and right
                                newCmp.add(replacementLeft[j].generate());
                                newCmp.add(replacementRight[j].generate());

                                int op = Opcodes.IF_ICMPNE;
                                LabelNode labelNode = success;

                                if (j == types.size() - 1 && baseOpcode == Opcodes.IF_ICMPEQ) {
                                    op = Opcodes.IF_ICMPEQ;
                                }

                                if (j != types.size() - 1 && baseOpcode == Opcodes.IF_ICMPEQ) {
                                    labelNode = failure;
                                }

                                //Add jump
                                newCmp.add(ASMUtil.generateCompareAndJump(subType, op, labelNode));
                            }

                            //Insert failure label. Success label is already inserted
                            newCmp.add(failure);

                            //Replace old jump with new jumo
                            context.target().instructions.insertBefore(jump, newCmp);
                            context.target().instructions.remove(jump);

                            if (separated) {
                                context.target().instructions.remove(instruction); //Remove the CMP instruction
                            }
                        }
                    }
                } else if (instruction instanceof InvokeDynamicInsnNode dynamicInsnNode) {
                    //Check if it LambdaMetafactory.metafactory
                    if (dynamicInsnNode.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")) {
                        Handle methodReference = (Handle) dynamicInsnNode.bsmArgs[1];
                        boolean isStatic = methodReference.getTag() == Opcodes.H_INVOKESTATIC;
                        int staticOffset = isStatic ? 0 : 1;

                        //Create new descriptor
                        Type[] args = Type.getArgumentTypes(dynamicInsnNode.desc);
                        TransformTrackingValue[] values = new TransformTrackingValue[args.length];
                        for (int j = 0; j < values.length; j++) {
                            values[j] = frame.getStack(frame.getStackSize() - args.length + j);
                        }

                        //The return value (the lambda) is on the top of the stack of the next frame
                        TransformTrackingValue returnValue = ASMUtil.getTop(frames[i + 1]);

                        dynamicInsnNode.desc = MethodParameterInfo.getNewDesc(returnValue, values, dynamicInsnNode.desc);

                        Type referenceDesc = (Type) dynamicInsnNode.bsmArgs[0]; //Basically lambda parameters
                        assert referenceDesc.equals(dynamicInsnNode.bsmArgs[2]);

                        String methodName = methodReference.getName();
                        String methodDesc = methodReference.getDesc();
                        String methodOwner = methodReference.getOwner();
                        if (!methodOwner.equals(getTransformed().name)) {
                            throw new IllegalStateException("Method reference must be in the same class");
                        }

                        //Get analysis results of the actual method
                        //For lookups we do need to use the old owner
                        MethodID methodID = new MethodID(classNode.name, methodName, methodDesc, MethodID.CallType.VIRTUAL); // call subType doesn't matter
                        AnalysisResults results = analysisResults.get(methodID);
                        if (results == null) {
                            throw new IllegalStateException("Method not analyzed '" + methodID + "'");
                        }

                        //Create new lambda descriptor
                        String newDesc = results.getNewDesc();
                        Type[] newArgs = Type.getArgumentTypes(newDesc);
                        Type[] referenceArgs = newArgs;

                        Type[] lambdaArgs = new Type[newArgs.length - values.length + staticOffset];
                        System.arraycopy(newArgs, values.length - staticOffset, lambdaArgs, 0, lambdaArgs.length);

                        String newReferenceDesc = Type.getMethodType(Type.getReturnType(newDesc), referenceArgs).getDescriptor();
                        String lambdaDesc = Type.getMethodType(Type.getReturnType(newDesc), lambdaArgs).getDescriptor();

                    /*
                    dynamicInsnNode.bsmArgs[0] = Type.getMethodType(lambdaDesc);
                    dynamicInsnNode.bsmArgs[1] = new Handle(methodReference.getTag(), methodReference.getOwner(), methodReference.getName(), newReferenceDesc, methodReference.isInterface());
                    dynamicInsnNode.bsmArgs[2] = dynamicInsnNode.bsmArgs[0];
                     */

                        dynamicInsnNode.bsmArgs = new Object[] {
                            Type.getMethodType(lambdaDesc),
                            new Handle(methodReference.getTag(), methodReference.getOwner(), methodReference.getName(), newReferenceDesc, methodReference.isInterface()),
                            Type.getMethodType(lambdaDesc)
                        };
                    }
                } else if (opcode == Opcodes.NEW) {
                    TransformTrackingValue value = ASMUtil.getTop(frames[i + 1]);
                    TypeInsnNode newInsn = (TypeInsnNode) instruction;
                    if (value.getTransform().getTransformType() != null) {
                        newInsn.desc = value.getTransform().getSingleType().getInternalName();
                    }
                }

                if (ensureValuesAreOnStack) {
                    //We know that either all values are on the stack or none are so we just check the first
                    int consumers = ASMUtil.stackConsumed(instruction);
                    if (consumers > 0) {
                        TransformTrackingValue value = ASMUtil.getTop(frame);
                        int producerIndex = context.indexLookup().get(value.getSource().iterator().next());
                        if (context.removedEmitter()[producerIndex]) {
                            //None of the values are on the stack
                            InsnList load = new InsnList();
                            for (int j = 0; j < consumers; j++) {
                                //We just get the emitter of every value and insert it
                                TransformTrackingValue arg = frame.getStack(frame.getStackSize() - consumers + j);
                                BytecodeFactory[] emitters = context.getSyntheticEmitter(arg);
                                for (BytecodeFactory emitter : emitters) {
                                    load.add(emitter.generate());
                                }
                            }
                            context.target().instructions.insertBefore(instruction, load);
                        }
                    }
                }
            }catch (Exception e){
                throw new RuntimeException("Error transforming instruction #" + i + ": " + ASMUtil.textify(instructions[i]), e);
            }
        }
    }


    /**
     * Transform a method call with which doesn't have a provided replacement. This is done by getting the transformed
     * type of every value that is passed to the method and changing the descriptor so as to match that. It will assume
     * that this method exists.
     * @param context Transform context
     * @param methodCall The actual method call
     * @param returnValue The return value of the method call, if the method returns void this should be null
     * @param args The arguments of the method call. This should include the instance ('this') if it is a non-static method
     */
    private void applyDefaultReplacement(TransformContext context, MethodInsnNode methodCall, TransformTrackingValue returnValue, TransformTrackingValue[] args) {
        //Get the actual values passed to the method. If the method is not static then the first value is the instance
        boolean isStatic = (methodCall.getOpcode() == Opcodes.INVOKESTATIC);
        int staticOffset = isStatic ? 0 : 1;
        TransformSubtype returnType = TransformSubtype.of(null);
        TransformSubtype[] argTypes = new TransformSubtype[args.length - staticOffset];

        if(returnValue != null){
            returnType = returnValue.getTransform();
        }

        for (int j = staticOffset; j < args.length; j++) {
            argTypes[j - staticOffset] = args[j].getTransform();
        }

        //Create the new descriptor
        String newDescriptor = MethodParameterInfo.getNewDesc(returnType, argTypes, methodCall.desc);

        methodCall.desc = newDescriptor;

        if(!isStatic){
            //Change the method owner if needed
            List<Type> types = args[0].transformedTypes();
            if(types.size() != 1){
                throw new IllegalStateException("Expected 1 subType but got " + types.size() + ". Define a custom replacement for this method (" + methodCall.owner + "#" + methodCall.name + methodCall.desc + ")");
            }

            HierarchyTree hierarchy = config.getHierarchy();

            Type potentionalOwner = types.get(0);
            if(methodCall.getOpcode() != Opcodes.INVOKESPECIAL) {
                int opcode = methodCall.getOpcode();

                if(opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
                    if(!potentionalOwner.equals(Type.getObjectType(methodCall.owner))) {
                        boolean isNewTypeInterface = hierarchy.recognisesInterface(potentionalOwner);
                        opcode = isNewTypeInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;

                        methodCall.itf = isNewTypeInterface;
                    }
                }

                methodCall.owner = potentionalOwner.getInternalName();
                methodCall.setOpcode(opcode);
            }else{

                String currentOwner = methodCall.owner;
                HierarchyTree.Node current = hierarchy.getNode(Type.getObjectType(currentOwner));
                HierarchyTree.Node potential = hierarchy.getNode(potentionalOwner);
                HierarchyTree.Node given = hierarchy.getNode(args[0].getType());

                if(given == null || current == null){
                    System.err.println("Don't have hierarchy for " + args[0].getType() + " or " + methodCall.owner);
                    methodCall.owner = potentionalOwner.getInternalName();
                }else if(given.isDirectDescendantOf(current)){
                    if(potential == null || potential.getParent() == null){
                        throw new IllegalStateException("Cannot change owner of super call if hierarchy for " + potentionalOwner + " is not defined");
                    }

                    Type newOwner = potential.getParent().getValue();
                    methodCall.owner = newOwner.getInternalName();
                }else{
                    methodCall.owner = potentionalOwner.getInternalName();
                }
            }
        }
    }

    /**
     * Transform a method call who's replacement is given in the config
     * @param context Transform context
     * @param methodCall The actual method cal insn
     * @param info The replacement to apply
     * @param args The arguments of the method call. This should include the instance ('this') if it is a non-static method
     */
    private void applyReplacement(TransformContext context, MethodInsnNode methodCall, MethodParameterInfo info, TransformTrackingValue[] args) {
        //Step 1: Check that all the values will be on the stack
        boolean allValuesOnStack = true;

        for(TransformTrackingValue value: args){
            for(AbstractInsnNode source: value.getSource()){
                int index = context.indexLookup().get(source);
                if(context.removedEmitter()[index]){
                    allValuesOnStack = false;
                    break;
                }
            }
            if(!allValuesOnStack){
                break;
            }
        }

        MethodReplacement replacement = info.getReplacement();
        if(replacement.changeParameters()){
            allValuesOnStack = false;
        }

        Type returnType = Type.getReturnType(methodCall.desc);
        if(!replacement.changeParameters() && info.getReturnType().transformedTypes(returnType).size() > 1){
            throw new IllegalStateException("Multiple return types not supported");
        }

        if(allValuesOnStack){
            //Simply remove the method call and replace it
            context.target().instructions.insert(methodCall, replacement.getBytecodeFactories()[0].generate());
            context.target().instructions.remove(methodCall);
        }else{
            //Store all the parameters
            BytecodeFactory[][] paramGenerators = new BytecodeFactory[args.length][];
            for(int j = 0; j < args.length; j++){
                paramGenerators[j] = context.getSyntheticEmitter(args[j]);
            }

            InsnList replacementInstructions = new InsnList();

            for (int j = 0; j < replacement.getBytecodeFactories().length; j++) {
                //Generate each part of the replacement
                List<Integer>[] indices = replacement.getParameterIndexes()[j];
                for (int k = 0; k < indices.length; k++) {
                    for (int index : indices[k]) {
                        replacementInstructions.add(paramGenerators[k][index].generate());
                    }
                }
                replacementInstructions.add(replacement.getBytecodeFactories()[j].generate());
            }

            //Call finalizer
            if(replacement.getFinalizer() != null){
                List<Integer>[] indices = replacement.getFinalizerIndices();
                //Add required parameters to finalizer
                for(int j = 0; j < indices.length; j++){
                    for(int index: indices[j]){
                        replacementInstructions.add(paramGenerators[j][index].generate());
                    }
                }
                replacementInstructions.add(replacement.getFinalizer().generate());
            }

            //Step 2: Insert new code
            context.target().instructions.insert(methodCall, replacementInstructions);
            context.target().instructions.remove(methodCall);
        }
    }

    /**
     * Modifies the variable and parameter tables (if they exist) to make it easier to read the generated code when decompiled
     * @param methodNode The method to modify
     * @param context The transform context
     */
    private void modifyVariableTable(MethodNode methodNode, TransformContext context) {
        if(methodNode.localVariables != null) {
            List<LocalVariableNode> original = methodNode.localVariables;
            List<LocalVariableNode> newLocalVariables = new ArrayList<>();

            for (LocalVariableNode local : original) {
                int codeIndex = context.indexLookup().get(local.start); //The index of the first frame with that variable
                int newIndex = context.varLookup[codeIndex][local.index]; //codeIndex is used to get the newIndex from varLookup

                TransformTrackingValue value = context.analysisResults().frames()[codeIndex].getLocal(local.index); //Get the value of that variable, so we can get its transform
                if (value.getTransformType() == null || value.getTransform().getSubtype() != TransformSubtype.SubType.NONE) {
                    String desc;// = value.getTransformType() == null ? value.getType().getDescriptor() : value.getTransform().getSingleType().getDescriptor();
                    if(value.getTransformType() == null){
                        Type type = value.getType();
                        if(type == null){
                            continue;
                        }else{
                            desc = value.getType().getDescriptor();
                        }
                    }else{
                        desc = value.getTransform().getSingleType().getDescriptor();
                    }
                    newLocalVariables.add(new LocalVariableNode(local.name, desc, local.signature, local.start, local.end, newIndex));
                } else {
                    String[] postfixes = value.getTransformType().getPostfix();
                    int varIndex = newIndex;
                    for (int j = 0; j < postfixes.length; j++) {
                        newLocalVariables.add(new LocalVariableNode(local.name + postfixes[j], value.getTransformType().getTo()[j].getDescriptor(), local.signature, local.start, local.end, varIndex));
                        varIndex += value.getTransformType().getTo()[j].getSize();
                    }
                }
            }

            methodNode.localVariables = newLocalVariables;
        }

        //Similar algorithm for parameters
        if(methodNode.parameters != null){
            List<ParameterNode> original = methodNode.parameters;
            List<ParameterNode> newParameters = new ArrayList<>();

            int index = 0;
            if((methodNode.access & Opcodes.ACC_STATIC) == 0){
                index++;
            }
            for(ParameterNode param : original){
                TransformTrackingValue value = context.analysisResults.frames()[0].getLocal(index);
                if (value.getTransformType() == null || value.getTransform().getSubtype() != TransformSubtype.SubType.NONE) {
                    newParameters.add(new ParameterNode(param.name, param.access));
                } else {
                    String[] postfixes = value.getTransformType().getPostfix();
                    for (String postfix : postfixes) {
                        newParameters.add(new ParameterNode(param.name + postfix, param.access));
                    }
                }
                index += value.getSize();
            }

            methodNode.parameters = newParameters;
        }
    }

    /**
     * Analyzes every method (except {@code <init>} and {@code <clinit>}) in the class and stores the results
     */
    public void analyzeAllMethods(){
        long startTime = System.currentTimeMillis();
        for(MethodNode methodNode: classNode.methods){
            if((methodNode.access & Opcodes.ACC_NATIVE) != 0){
                throw new IllegalStateException("Cannot analyze/transform native methods");
            }

            if(methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>")){
                continue;
            }

            if((methodNode.access & Opcodes.ACC_ABSTRACT) != 0){
                //We still want to infer the argument types of abstract methods so we create a single frame whose locals represent the arguments
                Type[] args = Type.getArgumentTypes(methodNode.desc);

                MethodID methodID = new MethodID(classNode.name, methodNode.name, methodNode.desc, MethodID.CallType.VIRTUAL);

                var typeHints = transformInfo.getTypeHints().get(methodID);

                TransformSubtype[] argTypes = new TransformSubtype[args.length];
                int index = 1; //Abstract methods can't be static, so they have the 'this' argument
                for(int i = 0; i < args.length; i++){
                    argTypes[i] = TransformSubtype.of(null);

                    if(typeHints != null && typeHints.containsKey(index)){
                        argTypes[i] = TransformSubtype.of(typeHints.get(index));
                    }

                    index += args[i].getSize();
                }

                Frame<TransformTrackingValue>[] frames = new Frame[1];

                int numLocals = 0;
                if(!ASMUtil.isStatic(methodNode)){
                    numLocals++;
                }
                for(Type argType: args){
                    numLocals += argType.getSize();
                }
                frames[0] = new Frame<>(numLocals, 0);

                int varIndex = 0;
                if(!ASMUtil.isStatic(methodNode)){
                    frames[0].setLocal(varIndex, new TransformTrackingValue(Type.getObjectType(classNode.name), fieldPseudoValues));
                    varIndex++;
                }

                int i = 0;
                for(Type argType: args){
                    TransformSubtype copyFrom = argTypes[i];
                    TransformTrackingValue value = new TransformTrackingValue(argType, fieldPseudoValues);
                    value.getTransform().setArrayDimensionality(copyFrom.getArrayDimensionality());
                    value.getTransform().setSubType(copyFrom.getSubtype());
                    value.setTransformType(copyFrom.getTransformType());
                    frames[0].setLocal(varIndex, value);
                    varIndex += argType.getSize();
                    i++;
                }

                AnalysisResults results = new AnalysisResults(methodNode, argTypes, frames);

                analysisResults.put(methodID, results);

                //Bind previous calls
                for(FutureMethodBinding binding: futureMethodBindings.getOrDefault(methodID, List.of())){
                    TransformTrackingInterpreter.bindValuesToMethod(results, binding.offset(), binding.parameters());
                }

                continue;
            }
            analyzeMethod(methodNode);
        }

        cleanUpAnalysis();

        for(AnalysisResults results: analysisResults.values()){
            results.print(System.out, false);
        }

        /*System.out.println("\nField Transforms:");

        for(var entry: fieldPseudoValues.entrySet()){
            if(entry.getValue().getTransformType() == null){
                System.out.println(entry.getKey() + ": [NO CHANGE]");
            }else{
                System.out.println(entry.getKey() + ": " + entry.getValue().getTransformType());
            }
        }*/

        System.out.println("Finished analysis of " + classNode.name + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    /**
     * Must be called after all analysis and before all transformations
     */
    public void cleanUpAnalysis(){
        for(MethodID methodID: analysisResults.keySet()){
            //Get the actual var types. Value bindings may have changed them
            AnalysisResults results = analysisResults.get(methodID);
            boolean isStatic = ASMUtil.isStatic(results.methodNode());

            TransformSubtype[] varTypes = new TransformSubtype[ASMUtil.argumentSize(results.methodNode().desc, isStatic)]; //Indices are local variable indices
            TransformSubtype[] argTypes = new TransformSubtype[ASMUtil.argumentCount(results.methodNode().desc, isStatic)]; //Indices are argument indices

            Frame<TransformTrackingValue> firstFrame = results.frames()[0];
            for(int i = 0; i < varTypes.length; i++){
                TransformTrackingValue local = firstFrame.getLocal(i);
                if(local == null){
                    varTypes[i] = TransformSubtype.of(null);
                }else {
                    varTypes[i] = local.getTransform();
                }
            }

            ASMUtil.varIndicesToArgIndices(varTypes, argTypes, results.methodNode().desc, isStatic);

            AnalysisResults finalResults = new AnalysisResults(results.methodNode(), argTypes, results.frames());
            analysisResults.put(methodID, finalResults);
        }

        //Change field type in duplicate class
        if(newClassNode != null) {
            for (var entry : fieldPseudoValues.entrySet()) {
                if (entry.getValue().getTransformType() == null) {
                    continue;
                }

                hasTransformedFields = true;

                TransformSubtype transformType = entry.getValue().getTransform();
                FieldID fieldID = entry.getKey();
                ASMUtil.changeFieldType(newClassNode, fieldID, transformType.getSingleType(), (m) -> new InsnList());
            }
        }else{
            //Still check for transformed fields
            for(var entry: fieldPseudoValues.entrySet()){
                if(entry.getValue().getTransformType() != null){
                    hasTransformedFields = true;
                    break;
                }
            }
        }

        //Add safety field if necessary
        if(hasTransformedFields){
            addSafetyField();
        }
    }

    /**
     * Creates a boolean field named isTransformed that stores whether the fields of the class have transformed types
     */
    private void addSafetyField() {
        isTransformedField = new FieldID(Type.getObjectType(classNode.name), "isTransformed" + MIX, Type.BOOLEAN_TYPE);
        classNode.fields.add(isTransformedField.toNode(false, Opcodes.ACC_FINAL));

        //For every constructor already in the method, add 'isTransformed = false' to it.
        for(MethodNode methodNode: classNode.methods){
            if(methodNode.name.equals("<init>")){
                insertAtReturn(methodNode, () -> {
                    InsnList instructions = new InsnList();
                    instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new InsnNode(Opcodes.ICONST_0));
                    instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, classNode.name, isTransformedField.name(), "Z"));
                    return instructions;
                });
            }
        }
    }

    /**
     * One of the aspects of this transformer is that if the original methods are called then the behaviour should be normal.
     * This means that if a field's type needs to be changed then old methods would still need to use the old field type and new methods would need to use the new field type.
     * Instead of duplicating each field, we turn the type of each of these fields into {@link Object} and cast them to their needed type. To initialize these fields to their transformed types, we
     * create a new constructor.
     * <br><br>
     * <b>Example:</b>
     * <pre>
     *     public class A {
     *         private final LongList list;
     *
     *         public A() {
     *             Initialization...
     *         }
     *
     *         public void exampleMethod() {
     *             long pos = list.get(0);
     *             ...
     *         }
     *     }
     * </pre>
     * Would become
     * <pre>
     *     public class A {
     *         private final Object list;
     *
     *         public A() {
     *             Initialization...
     *         }
     *
     *         //This constructor would need to be added by makeConstructor
     *         public A(int magic){
     *             Transformed initialization...
     *         }
     *
     *         public void exampleMethod() {
     *             long pos = ((LongList)list).get(0);
     *             ...
     *         }
     *     }
     * </pre>
     */
    private void makeFieldCasts(){
        for(var entry: fieldPseudoValues.entrySet()){
            if(entry.getValue().getTransformType() == null){
                continue;
            }

            TransformSubtype transformType = entry.getValue().getTransform();
            FieldID fieldID = entry.getKey();

            String originalType = entry.getValue().getType().getInternalName();
            String transformedType = transformType.getSingleType().getInternalName();

            ASMUtil.changeFieldType(classNode, fieldID, Type.getObjectType("java/lang/Object"), (method) -> {
                InsnList insnList = new InsnList();
                if(isSynthetic(method)) {
                    insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, transformType.getSingleType().getInternalName()));
                }else{
                    insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, originalType));
                }
                return insnList;
            });
        }
    }

    /**
     * This method creates a jump to the given label if the fields hold transformed types or none of the fields need to be transformed.
     * @param label The label to jump to.
     * @return The instructions to jump to the given label.
     */
    private InsnList jumpIfNotTransformed(LabelNode label){
        InsnList instructions = new InsnList();
        if(hasTransformedFields){
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            instructions.add(new FieldInsnNode(Opcodes.GETFIELD, isTransformedField.owner().getInternalName(), isTransformedField.name(), isTransformedField.desc().getDescriptor()));
            instructions.add(new JumpInsnNode(Opcodes.IFEQ, label));
        }

        //If there are no transformed fields then we never jump.
        return instructions;
    }

    public void analyzeMethod(String name, String desc){
        MethodNode method = classNode.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);

        if(method == null){
            throw new IllegalStateException("Method " + name + desc + " not found in class " + classNode.name);
        }

        analyzeMethod(method);
    }

    /**
     * Analyzes a single method and stores the results
     * @param methodNode The method to analyze
     */
    public void analyzeMethod(MethodNode methodNode){
        long startTime = System.currentTimeMillis();
        config.getInterpreter().reset(); //Clear all info stored about previous methods
        config.getInterpreter().setResultLookup(analysisResults);
        config.getInterpreter().setFutureBindings(futureMethodBindings);
        config.getInterpreter().setCurrentClass(classNode);
        config.getInterpreter().setFieldBindings(fieldPseudoValues);

        MethodID methodID = new MethodID(classNode.name, methodNode.name, methodNode.desc, null);

        //Get any type hints for this method
        Map<Integer, TransformType> typeHints;
        if(transformInfo != null) {
            typeHints = transformInfo.getTypeHints().get(methodID);
        }else{
            typeHints = null;
        }

        if(typeHints != null){
            //Set the type hints
            config.getInterpreter().setLocalVarOverrides(typeHints);
        }

        try {
            var frames = config.getAnalyzer().analyze(classNode.name, methodNode);
            boolean isStatic = ASMUtil.isStatic(methodNode);

            TransformSubtype[] varTypes = new TransformSubtype[ASMUtil.argumentSize(methodNode.desc, isStatic)]; //Indices are local variable indices
            TransformSubtype[] argTypes = new TransformSubtype[ASMUtil.argumentCount(methodNode.desc, isStatic)]; //Indices are argument indices

            //Create argument type array
            Frame<TransformTrackingValue> firstFrame = frames[0];
            for(int i = 0; i < varTypes.length; i++){
                varTypes[i] = firstFrame.getLocal(i).getTransform();
            }

            ASMUtil.varIndicesToArgIndices(varTypes, argTypes, methodNode.desc, isStatic);

            AnalysisResults results = new AnalysisResults(methodNode, argTypes, frames);
            analysisResults.put(methodID, results);

            //Bind previous calls
            for(FutureMethodBinding binding: futureMethodBindings.getOrDefault(methodID, List.of())){
                TransformTrackingInterpreter.bindValuesToMethod(results, binding.offset(), binding.parameters());
            }

            System.out.println("Analyzed method " + methodID + " in " + (System.currentTimeMillis() - startTime) + "ms");
        }catch (AnalyzerException e){
            throw new RuntimeException("Analysis failed for method " + methodNode.name, e);
        }
    }

    public void saveTransformedClass(){
        Path outputPath = OUT_DIR.resolve(getTransformed().name + ".class");
        try {
            Files.createDirectories(outputPath.getParent());
            ClassWriter writer = new ClassWriter(0);
            getTransformed().accept(writer);
            Files.write(outputPath, writer.toByteArray());
        }catch (IOException e){
            throw new RuntimeException("Failed to save transformed class", e);
        }
    }

    public void transformMethod(String name, String desc) {
        MethodNode methodNode = classNode.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findAny().orElse(null);
        if(methodNode == null){
            throw new RuntimeException("Method " + name + desc + " not found in class " + classNode.name);
        }
        try {
            transformMethod(methodNode);
        }catch (Exception e){
            throw new RuntimeException("Failed to transform method " + name + desc, e);
        }
    }

    public void transformMethod(String name) {
        List<MethodNode> methods = classNode.methods.stream().filter(m -> m.name.equals(name)).toList();
        if(methods.isEmpty()){
            throw new RuntimeException("Method " + name + " not found in class " + classNode.name);
        }else if(methods.size() > 1){
            throw new RuntimeException("Multiple methods named " + name + " found in class " + classNode.name);
        }else{
            try{
                transformMethod(methods.get(0));
            }catch (Exception e){
                throw new RuntimeException("Failed to transform method " + name + methods.get(0).desc, e);
            }
        }
    }

    public Class<?> loadTransformedClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        getTransformed().accept(writer);
        byte[] bytes = writer.toByteArray();

        String targetName = getTransformed().name.replace('/', '.');

        ClassLoader classLoader = new ClassLoader(this.getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if(name.equals(targetName)) {
                    return defineClass(name, bytes, 0, bytes.length);
                }else{
                    return super.loadClass(name);
                }
            }
        };

        try {
            return classLoader.loadClass(targetName);
        }catch (ClassNotFoundException e){
            throw new RuntimeException("Failed to load transformed class", e);
        }
    }

    private ClassNode getTransformed(){
        if(newClassNode == null){
            return classNode;
        }else{
            return newClassNode;
        }
    }

    public void transformAllMethods() {
        int size = classNode.methods.size();
        for (int i = 0; i < size; i++) {
            MethodNode methodNode = classNode.methods.get(i);
            if (!methodNode.name.equals("<init>") && !methodNode.name.equals("<clinit>")) {
                try {
                    transformMethod(methodNode);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to transform method " + methodNode.name + methodNode.desc, e);
                }
            }
        }

        cleanUpTransform();
    }

    /**
     * Add a constructor to the class
     * @param desc The descriptor of the original constructor
     * @param constructor Code for the new constructor. This code is expected to initialize all fields (except 'isTransformed') with transformed values
     */
    public void makeConstructor(String desc, InsnList constructor) {
        //Add int to end of descriptor signature so we can call this new constructor
        Type[] args = Type.getArgumentTypes(desc);
        int totalSize = 1;
        for (Type arg : args) {
            totalSize += arg.getSize();
        }

        Type[] newArgs = new Type[args.length + 1];
        newArgs[newArgs.length - 1] = Type.INT_TYPE;
        System.arraycopy(args, 0, newArgs, 0, args.length);
        String newDesc = Type.getMethodDescriptor(Type.VOID_TYPE, newArgs);

        //If the extra integer passed is not equal to MAGIC (0xDEADBEEF), then we throw an error. This is to prevent accidental use of this constructor
        InsnList safetyCheck = new InsnList();
        LabelNode label = new LabelNode();
        safetyCheck.add(new VarInsnNode(Opcodes.ILOAD, totalSize));
        safetyCheck.add(new LdcInsnNode(MAGIC));
        safetyCheck.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, label));
        safetyCheck.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalArgumentException"));
        safetyCheck.add(new InsnNode(Opcodes.DUP));
        safetyCheck.add(new LdcInsnNode("Wrong magic value '"));
        safetyCheck.add(new VarInsnNode(Opcodes.ILOAD, totalSize));
        safetyCheck.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;", false));
        safetyCheck.add(new LdcInsnNode("'"));
        safetyCheck.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
        safetyCheck.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
        safetyCheck.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false));
        safetyCheck.add(new InsnNode(Opcodes.ATHROW));
        safetyCheck.add(label);
        safetyCheck.add(new VarInsnNode(Opcodes.ALOAD, 0));
        safetyCheck.add(new InsnNode(Opcodes.ICONST_1));
        safetyCheck.add(new FieldInsnNode(Opcodes.PUTFIELD, classNode.name, isTransformedField.name(), "Z"));

        AbstractInsnNode[] nodes = constructor.toArray();

        //Find super call
        for(AbstractInsnNode node : nodes){
            if(node.getOpcode() == Opcodes.INVOKESPECIAL){
                MethodInsnNode methodNode = (MethodInsnNode) node;
                if(methodNode.owner.equals(classNode.superName)){
                    //Insert the safety check right after the super call
                    constructor.insert(safetyCheck);
                    break;
                }
            }
        }

        //Shift variables
        for(AbstractInsnNode node : nodes){
            if(node instanceof VarInsnNode varNode){
                if(varNode.var >= totalSize){
                    varNode.var++;
                }
            }else if(node instanceof IincInsnNode iincNode){
                if(iincNode.var >= totalSize){
                    iincNode.var++;
                }
            }
        }

        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", newDesc, null, null);
        methodNode.instructions.add(constructor);

        markSynthetic(methodNode, "CONSTRUCTOR", "<init>" + desc);

        newMethods.add(methodNode);
    }

    /**
     * Insert the provided code before EVERY return statement in a method
     * @param methodNode The method to insert the code into
     * @param insn The code to insert
     */
    private void insertAtReturn(MethodNode methodNode, BytecodeFactory insn) {
        InsnList instructions = methodNode.instructions;
        AbstractInsnNode[] nodes = instructions.toArray();

        for (AbstractInsnNode node: nodes) {
            if (   node.getOpcode() == Opcodes.RETURN
                || node.getOpcode() == Opcodes.ARETURN
                || node.getOpcode() == Opcodes.IRETURN
                || node.getOpcode() == Opcodes.FRETURN
                || node.getOpcode() == Opcodes.DRETURN
                || node.getOpcode() == Opcodes.LRETURN) {
                instructions.insertBefore(node, insn.generate());
            }
        }
    }

    /**
     * Adds the {@link CCSynthetic} annotation to the provided method
     * @param methodNode The method to mark
     * @param subType The type of synthetic method this is
     * @param original The original method this is a synthetic version of
     */
    private static void markSynthetic(MethodNode methodNode, String subType, String original){
        List<AnnotationNode> annotations = methodNode.visibleAnnotations;
        if(annotations == null){
            annotations = new ArrayList<>();
            methodNode.visibleAnnotations = annotations;
        }

        AnnotationNode synthetic = new AnnotationNode(Type.getDescriptor(CCSynthetic.class));

        synthetic.values = new ArrayList<>();
        synthetic.values.add("subType");
        synthetic.values.add(subType);
        synthetic.values.add("original");
        synthetic.values.add(original);

        annotations.add(synthetic);
    }

    /**
     * Checks if the provided method has the {@link CCSynthetic} annotation
     * @param methodNode The method to check
     * @return True if the method is synthetic, false otherwise
     */
    private static boolean isSynthetic(MethodNode methodNode){
        List<AnnotationNode> annotations = methodNode.visibleAnnotations;
        if(annotations == null){
            return false;
        }

        for(AnnotationNode annotation : annotations){
            if(annotation.desc.equals(Type.getDescriptor(CCSynthetic.class))){
                return true;
            }
        }

        return false;
    }

    /**
     * This method is called by safety dispatches
     * @param methodOwner The owner of the method called
     * @param methodName The name of the method called
     * @param methodDesc The descriptor of the method called
     */
    public static void emitWarning(String methodOwner, String methodName, String methodDesc){
        //Gather info about exactly where this was called
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stackTrace[3];

        String warningID = methodOwner + "." + methodName + methodDesc + " at " + caller.getClassName() + "." + caller.getMethodName() + ":" + caller.getLineNumber();
        if(warnings.add(warningID)){
            System.out.println("[CC] Incorrect Invocation: " + warningID);
            try{
                FileOutputStream fos = new FileOutputStream(ERROR_LOG.toFile(), true);
                for(String warning : warnings){
                    fos.write(warning.getBytes());
                }
                fos.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Makes all call to super constructor add the magic value so that it is initialized transformed
     */
    public void callMagicSuperConstructor() {
        for(MethodNode methodNode : classNode.methods){
            if(methodNode.name.equals("<init>")){
                MethodInsnNode superCall = findSuperCall(methodNode);
                String[] parts = superCall.desc.split("\\)");
                String newDesc = parts[0] + "I)" + parts[1];
                superCall.desc = newDesc;
                methodNode.instructions.insertBefore(superCall, new LdcInsnNode(MAGIC));
            }
        }
    }

    private MethodInsnNode findSuperCall(MethodNode constructor){
        for(AbstractInsnNode insn : constructor.instructions.toArray()){
            if(insn.getOpcode() == Opcodes.INVOKESPECIAL){
                MethodInsnNode methodInsn = (MethodInsnNode)insn;
                if(methodInsn.owner.equals(classNode.superName) && methodInsn.name.equals("<init>")){
                    return methodInsn;
                }
            }
        }

        throw new RuntimeException("Could not find super constructor call");
    }

    /**
     * Stores all information needed to transform a method.
     *
     * @param target The method that is being transformed.
     * @param analysisResults The analysis results for this method that were generated by the analysis phase.
     * @param instructions The instructions of {@code target} before any transformations.
     * @param expandedEmitter For each index in {@code instructions}, the corresponding element in this array indicates whether the emitter at that index has been expanded.
     * @param expandedConsumer For each index in {@code instructions}, the corresponding element in this array indicates whether the consumer at that index has been expanded.
     * @param removedEmitter If, for a given index, removedEmitter is true, than the instruction at that index was removed and so its value will no longer be on the stack. To retrieve the value use the syntheticEmitters field
     * @param syntheticEmitters Stores code generators that will replicate the value of the instruction at the given index. For a given instruction index, there is an array. Each element
     * of an array corresponds to a value generated by the corresponding emitter (DUP and others can create more than one value). This value is itself represented by an array of
     * {@link BytecodeFactory}s. If the value has no transform type then that array will have a single element which will generate code that will push that value onto the stack. Otherwise,
     * each element of the array will push the element of that transform type onto the stack. So for a value with transform type int -> (int "x", long "y", String "name"). The first
     * element will push the int 'x' onto the stack, the second element will push the long 'y' onto the stack, and the third element will push the String 'name' onto the stack.
     * @param varLookup Stores the new index of a variable. varLookup[insnIndex][oldVarIndex] gives the new var index.
     * @param variableManager The variable manager allows for the creation of new variables.
     * @param indexLookup A map from instruction object to index in the instructions array. This map contains keys for the instructions of both the old and new methods. This is useful mainly because TransformTrackingValue.getSource() will return
     *                    instructions from the old method and to manipulate the InsnList of the new method (which is a linked list) we need an element which is in that InsnList.
     * @param methodInfos If an instruction is a method invocation, this will store information about how to transform it.
     */
    private record TransformContext(MethodNode target, AnalysisResults analysisResults, AbstractInsnNode[] instructions, boolean[] expandedEmitter, boolean[] expandedConsumer,
                                    boolean[] removedEmitter, BytecodeFactory[][][] syntheticEmitters, int[][] varLookup, TransformSubtype[][] varTypes, VariableManager variableManager, Map<AbstractInsnNode, Integer> indexLookup,
                                    MethodParameterInfo[] methodInfos){

        <T extends AbstractInsnNode> T getActual(T node){
            return (T) instructions[indexLookup.get(node)];
        }

        BytecodeFactory[] getSyntheticEmitter(TransformTrackingValue value){
            if(value.getSource().size() == 0){
                throw new RuntimeException("Cannot get synthetic emitter for value with no source");
            }

            int index = indexLookup.get(value.getSource().iterator().next());
            BytecodeFactory[][] emitters = syntheticEmitters[index];
            Frame<TransformTrackingValue> frame = analysisResults.frames()[index + 1];

            Set<TransformTrackingValue> lookingFor = value.getAllRelatedValues();

            int i = 0;
            for(; i < frame.getStackSize(); i++){
                if(lookingFor.contains(frame.getStack(frame.getStackSize() - 1 - i))){
                    break;
                }
            }

            if(i == frame.getStackSize()){
                value.getFurthestAncestors();
                value.getAllRelatedValues();
                throw new RuntimeException("Could not find value in frame");
            }

            return emitters[emitters.length - 1 - i];
        }
    }
}
