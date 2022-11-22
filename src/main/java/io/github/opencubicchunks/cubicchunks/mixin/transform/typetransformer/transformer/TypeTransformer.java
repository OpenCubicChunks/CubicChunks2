package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.AnalysisResults;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.FutureMethodBinding;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingInterpreter;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ClassTransformInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConstructorReplacer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodReplacement;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TypeInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.FieldID;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import io.github.opencubicchunks.cubicchunks.utils.TestMappingUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * This class is responsible for transforming the methods and fields of a single class according to the configuration. See {@link
 * io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader}
 * <br><br>
 * <b>Definitions:</b>
 * <ul>Emitter: Any instruction that pushes one or more values onto the stack</ul>
 * <ul>Consumer: Any instruction that pops one or more values from the stack</ul>
 */
public class TypeTransformer {
    public static final boolean VERBOSE = true;
    //Postfix that gets appended to some names to prevent conflicts
    public static final String MIX = "$$cc_transformed";
    //A value that should be passed to transformed constructors. Any other value will cause an error
    public static final int MAGIC = 0xDEADBEEF;
    //When safety is enabled, if a long-pos method is called for a 3-int object a warning will be created. This keeps track of all warnings.
    private static final Set<String> WARNINGS = new HashSet<>();
    //Path to file where errors should be logged
    private static final Path ERROR_LOG = TestMappingUtils.getGameDir().resolve("errors.log");

    private static final Map<String, Int2ObjectMap<String>> CC_SYNTHETIC_LOOKUP = new HashMap<>();

    //The global configuration loaded by ConfigLoader
    private final Config config;
    //The original class node
    private final ClassNode classNode;
    //Stores all the analysis results per method
    private final Map<MethodID, AnalysisResults> analysisResults = new HashMap<>();
    //Keeps track of bindings to un-analyzed methods
    private final Map<MethodID, List<FutureMethodBinding>> futureMethodBindings = new HashMap<>();
    //Stores values for each field in the class. These can be bound (set same type) like any other values and allows
    //for easy tracking of the transform-type of a field
    private final AncestorHashMap<FieldID, TransformTrackingValue> fieldPseudoValues;
    //Per-class configuration
    private final ClassTransformInfo transformInfo;
    private final boolean inPlace;
    //The field ID (owner, name, desc) of a field which stores whether an instance was created with a transformed constructor and has transformed fields
    private FieldID isTransformedField;
    private boolean hasTransformedFields;
    //Whether safety checks/dispatches/warnings should be inserted into the code.
    private final boolean addSafety;
    //Stores the lambdaTransformers that need to be added
    private final Set<MethodNode> lambdaTransformers = new HashSet<>();
    //Stores any other methods that need to be added. There really isn't much of a reason for these two to be separate.
    private final Set<MethodNode> newMethods = new HashSet<>();

    private final Map<MethodID, MethodID> externalMethodReplacements = new HashMap<>();


    /**
     * Constructs a new TypeTransformer for a given class.
     *
     * @param config The global configuration loaded by ConfigLoader
     * @param classNode The original class node
     * @param addSafety Whether safety checks/dispatches/warnings should be inserted into the code.
     */
    public TypeTransformer(Config config, ClassNode classNode, boolean addSafety) {
        this.config = config;
        this.classNode = classNode;
        this.fieldPseudoValues = new AncestorHashMap<>(config.getTypeInfo());
        this.addSafety = addSafety;

        //Create field pseudo values
        for (var field : classNode.fields) {
            TransformTrackingValue value = new TransformTrackingValue(Type.getType(field.desc), fieldPseudoValues, config);
            fieldPseudoValues.put(new FieldID(Type.getObjectType(classNode.name), field.name, Type.getType(field.desc)), value);
        }

        //Extract per-class config from the global config
        this.transformInfo = config.getClasses().get(Type.getObjectType(classNode.name));

        if (transformInfo != null) {
            this.inPlace = transformInfo.isInPlace();
        } else {
            this.inPlace = false;
        }
    }

    // ANALYSIS

    /**
     * Analyzes every method (except {@code <init>} and {@code <clinit>}) in the class and stores the results
     */
    public void analyzeAllMethods() {
        long startTime = System.currentTimeMillis();
        for (MethodNode methodNode : classNode.methods) {
            if ((methodNode.access & Opcodes.ACC_NATIVE) != 0) {
                throw new IllegalStateException("Cannot analyze/transform native methods");
            }

            if ((methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>")) && !inPlace) {
                continue;
            }

            if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
                addDummyValues(methodNode);

                continue;
            }
            analyzeMethod(methodNode);
        }

        cleanUpAnalysis();

        if (VERBOSE) {
            printAnalysisResults();
        }

        System.out.println("Finished analysis of " + classNode.name + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private void printAnalysisResults() {
        for (AnalysisResults results : analysisResults.values()) {
            results.print(System.out, false);
        }

        System.out.println("\nField Transforms:");

        for (var entry : fieldPseudoValues.entrySet()) {
            if (entry.getValue().getTransformType() == null) {
                System.out.println(entry.getKey() + ": [NO CHANGE]");
            } else {
                System.out.println(entry.getKey() + ": " + entry.getValue().getTransformType());
            }
        }
    }

    public void analyzeMethod(String name, String desc) {
        MethodNode method = classNode.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);

        if (method == null) {
            throw new IllegalStateException("Method " + name + desc + " not found in class " + classNode.name);
        }

        analyzeMethod(method);
    }

    /**
     * Analyzes a single method and stores the results
     *
     * @param methodNode The method to analyze
     */
    public AnalysisResults analyzeMethod(MethodNode methodNode) {
        long startTime = System.currentTimeMillis();
        config.getInterpreter().reset(); //Clear all info stored about previous methods
        config.getInterpreter().setResultLookup(analysisResults);
        config.getInterpreter().setFutureBindings(futureMethodBindings);
        config.getInterpreter().setCurrentClass(classNode);
        config.getInterpreter().setFieldBindings(fieldPseudoValues);

        MethodID methodID = new MethodID(classNode.name, methodNode.name, methodNode.desc, null);

        //Get any type hints for this method
        Map<Integer, TransformType> typeHints;
        if (transformInfo != null) {
            typeHints = transformInfo.getTypeHints().get(methodID);
        } else {
            typeHints = null;
        }

        if (typeHints != null) {
            //Set the type hints
            config.getInterpreter().setLocalVarOverrides(typeHints);
        }

        try {
            var frames = config.getAnalyzer().analyze(classNode.name, methodNode);
            AnalysisResults results = new AnalysisResults(methodNode, frames);
            analysisResults.put(methodID, results);

            //Bind previous calls
            for (FutureMethodBinding binding : futureMethodBindings.getOrDefault(methodID, List.of())) {
                TransformTrackingInterpreter.bindValuesToMethod(results, binding.offset(), binding.parameters());
            }

            System.out.println("Analyzed method " + methodID + " in " + (System.currentTimeMillis() - startTime) + "ms");

            return results;
        } catch (AnalyzerException e) {
            throw new RuntimeException("Analysis failed for method " + methodNode.name, e);
        }
    }

    private void addDummyValues(MethodNode methodNode) {
        //We still want to infer the argument types of abstract methods so we create a single frame whose locals represent the arguments
        Type[] args = Type.getArgumentTypes(methodNode.desc);

        MethodID methodID = new MethodID(classNode.name, methodNode.name, methodNode.desc, MethodID.CallType.VIRTUAL);

        var typeHints = transformInfo.getTypeHints().get(methodID);

        TransformSubtype[] argTypes = new TransformSubtype[args.length];
        int index = 1; //Abstract methods can't be static, so they have the 'this' argument
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = TransformSubtype.createDefault();

            if (typeHints != null && typeHints.containsKey(index)) {
                argTypes[i] = TransformSubtype.of(typeHints.get(index));
            }

            index += args[i].getSize();
        }

        Frame<TransformTrackingValue>[] frames = new Frame[1];

        int numLocals = methodID.getCallType().getOffset();
        for (Type argType : args) {
            numLocals += argType.getSize();
        }
        frames[0] = new Frame<>(numLocals, 0);

        int varIndex = 0;
        if (!ASMUtil.isStatic(methodNode)) {
            frames[0].setLocal(varIndex, new TransformTrackingValue(Type.getObjectType(classNode.name), fieldPseudoValues, config));
            varIndex++;
        }

        int i = 0;
        for (Type argType : args) {
            TransformSubtype copyFrom = argTypes[i];
            TransformTrackingValue value = new TransformTrackingValue(argType, fieldPseudoValues, config);
            value.getTransform().setArrayDimensionality(copyFrom.getArrayDimensionality());
            value.getTransform().setSubType(copyFrom.getSubtype());
            value.setTransformType(copyFrom.getTransformType());
            frames[0].setLocal(varIndex, value);
            varIndex += argType.getSize();
            i++;
        }

        AnalysisResults results = new AnalysisResults(methodNode, frames);
        analysisResults.put(methodID, results);

        //Bind previous calls
        for (FutureMethodBinding binding : futureMethodBindings.getOrDefault(methodID, List.of())) {
            TransformTrackingInterpreter.bindValuesToMethod(results, binding.offset(), binding.parameters());
        }
    }

    /**
     * Must be called after all analysis and before all transformations
     */
    public void cleanUpAnalysis() {
        //Check for transformed fields
        for (var entry : fieldPseudoValues.entrySet()) {
            if (entry.getValue().getTransformType() != null) {
                hasTransformedFields = true;
                break;
            }
        }

        //Add safety field if necessary
        if (hasTransformedFields && !inPlace) {
            addSafetyField();
        }
    }

    /**
     * Creates a boolean field named isTransformed that stores whether the fields of the class have transformed types
     */
    private void addSafetyField() {
        isTransformedField = new FieldID(Type.getObjectType(classNode.name), "isTransformed" + MIX, Type.BOOLEAN_TYPE);
        classNode.fields.add(isTransformedField.toNode(false, Opcodes.ACC_FINAL));
    }


    // TRANSFORMATION

    public void transformAllMethods() {
        int size = classNode.methods.size();
        for (int i = 0; i < size; i++) {
            MethodNode methodNode = classNode.methods.get(i);
            if (!methodNode.name.equals("<init>") && !methodNode.name.equals("<clinit>") || inPlace) {
                try {
                    generateTransformedMethod(methodNode);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to transform method " + methodNode.name + methodNode.desc, e);
                }
            }
        }

        cleanUpTransform();
    }

    public void transformMethod(String name, String desc) {
        MethodNode methodNode = classNode.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findAny().orElse(null);
        if (methodNode == null) {
            throw new RuntimeException("Method " + name + desc + " not found in class " + classNode.name);
        }
        try {
            generateTransformedMethod(methodNode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform method " + name + desc, e);
        }
    }

    /**
     * Creates a copy of the method and transforms it according to the config. This method then gets added to the necessary class. The main goal of this method is to create the transform
     * context. It then passes that on to the necessary methods. This method does not modify the method much.
     *
     * @param methodNode The method to transform.
     */
    public void generateTransformedMethod(MethodNode methodNode) {
        long start = System.currentTimeMillis();

        //Look up the analysis results for this method
        MethodID methodID = new MethodID(classNode.name, methodNode.name, methodNode.desc, MethodID.CallType.VIRTUAL); //Call subType doesn't matter much
        AnalysisResults results = analysisResults.get(methodID);

        if (results == null) {
            throw new RuntimeException("Method " + methodID + " not analyzed");
        }

        //Create the new method node
        MethodNode newMethod = ASMUtil.copy(methodNode);
        if (this.config.getTypesWithSuffixedTransforms().contains(methodID.getOwner())) {
            newMethod.name += MIX;
        }
        //Add it to newMethods so that it gets added later and doesn't cause a ConcurrentModificationException if iterating over the methods.
        newMethods.add(newMethod);

        //See TransformContext
        AbstractInsnNode[] insns = newMethod.instructions.toArray();
        int[] vars = new int[newMethod.maxLocals];
        TransformSubtype[][] varTypes = new TransformSubtype[insns.length][newMethod.maxLocals];

        //Generate var table
        int[] maxVarWidth = new int[newMethod.maxLocals];
        Arrays.fill(maxVarWidth, 0);

        for (int i = 0; i < insns.length; i++) {
            Frame<TransformTrackingValue> frame = results.frames()[i];
            if (frame == null) continue;

            for (int j = 0; j < newMethod.maxLocals; j += frame.getLocal(j).getSize()) {
                TransformTrackingValue local = frame.getLocal(j);
                maxVarWidth[j] = Math.max(maxVarWidth[j], local.getTransformedSize());
                varTypes[i][j] = local.getTransform();
            }
        }

        int totalSize = 0;
        for (int i = 0; i < newMethod.maxLocals; i++) {
            vars[i] = totalSize;
            totalSize += maxVarWidth[i];
        }

        VariableAllocator varCreator = new VariableAllocator(totalSize, insns.length);

        //Analysis results come from the original method, and we need to transform the new method, so we need to be able to get the new instructions that correspond to the old ones
        Map<AbstractInsnNode, Integer> indexLookup = new HashMap<>();

        AbstractInsnNode[] oldInsns = newMethod.instructions.toArray();

        for (int i = 0; i < oldInsns.length; i++) {
            indexLookup.put(insns[i], i);
            indexLookup.put(oldInsns[i], i);
        }

        AbstractInsnNode[] instructions = newMethod.instructions.toArray();
        Frame<TransformTrackingValue>[] frames = results.frames();

        //Resolve the method parameter infos
        MethodParameterInfo[] methodInfos = new MethodParameterInfo[insns.length];
        getAllMethodInfo(insns, instructions, frames, methodInfos);

        //Create context
        TransformContext context = new TransformContext(newMethod, results, instructions, vars, varTypes, varCreator, indexLookup, methodInfos);

        if ((newMethod.access & Opcodes.ACC_ABSTRACT) != 0) {
            transformAbstractMethod(newMethod, start, methodID, newMethod, context);
            return;
        }

        transformMethod(methodNode, newMethod, context);

        markSynthetic(newMethod, "AUTO-TRANSFORMED", methodNode.name + methodNode.desc, classNode.name);

        System.out.println("Transformed method '" + methodID + "' in " + (System.currentTimeMillis() - start) + "ms");
    }

    /**
     * Actually modifies the method
     *
     * @param oldMethod The original method, may be modified for safety checks
     * @param methodNode The method to modify
     * @param context Transform context
     */
    private void transformMethod(MethodNode oldMethod, MethodNode methodNode, TransformContext context) {
        transformDesc(methodNode, context);

        //Change variable names to make it easier to debug
        modifyLVT(methodNode, context);

        //Change the code
        modifyCode(context);

        if (!ASMUtil.isStatic(methodNode)) {
            if (addSafety && (methodNode.access & Opcodes.ACC_SYNTHETIC) == 0) {
                //This can be disabled by setting addSafety to false in the constructor
                //but this means that if a single piece of code calls the wrong method then everything could crash.
                InsnList dispatch = new InsnList();
                LabelNode label = new LabelNode();

                dispatch.add(jumpIfNotTransformed(label));

                if (!oldMethod.desc.equals(methodNode.desc)) {
                    dispatch.add(generateEmitWarningCall("Incorrect Invocation of " + classNode.name + "." + oldMethod.name + oldMethod.desc, 3));
                }

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

                dispatch.add(label);

                oldMethod.instructions.insertBefore(oldMethod.instructions.getFirst(), dispatch);
            }
        }
    }

    /**
     * Modifies the code of the method to use the transformed types instead of the original types
     *
     * @param context The context of the transformation
     */
    private void modifyCode(TransformContext context) {
        AbstractInsnNode[] instructions = context.instructions();
        Frame<TransformTrackingValue>[] frames = context.analysisResults().frames();

        //Iterate through every instruction of the instructions array. We use the array because it will not change unlike methodNode.instructions
        for (int i = 0; i < instructions.length; i++) {
            try {
                AbstractInsnNode instruction = instructions[i];
                Frame<TransformTrackingValue> frame = frames[i];

                int opcode = instruction.getOpcode();

                if (instruction instanceof MethodInsnNode methodCall) {
                    transformMethodCall(context, frames, i, frame, methodCall);
                } else if (instruction instanceof VarInsnNode varNode) {
                    transformVarInsn(context, i, varNode);
                } else if (instruction instanceof IincInsnNode iincNode) {
                    transformIincInsn(context, iincNode);
                } else if (ASMUtil.isConstant(instruction)) {
                    transformConstantInsn(context, frames[i + 1], i, instruction);
                } else if (isACompare(opcode)) {
                    transformCmp(context, i, instruction, frame, opcode);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamicInsnNode) {
                    transformInvokeDynamicInsn(frames, i, frame, dynamicInsnNode);
                } else if (opcode == Opcodes.NEW) {
                    transformNewInsn(frames[i + 1], (TypeInsnNode) instruction);
                } else if (opcode == Opcodes.ANEWARRAY || opcode == Opcodes.NEWARRAY) {
                    transformNewArray(context, i, frames[i + 1], instruction, 1);
                } else if (instruction instanceof MultiANewArrayInsnNode arrayInsn) {
                    transformNewArray(context, i, frames[i + 1], instruction, arrayInsn.dims);
                } else if (isArrayLoad(instruction.getOpcode())) {
                    transformArrayLoad(context, i, instruction, frame);
                } else if (isArrayStore(instruction.getOpcode())) {
                    transformArrayStore(context, i, instruction, frame);
                }

                if (inPlace) {
                    if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC || opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
                        transformFieldInsn(context, i, (FieldInsnNode) instruction);
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException("Error transforming instruction #" + i + ": " + ASMUtil.textify(instructions[i]), e);
            }
        }
    }

    private void transformMethodCall(TransformContext context, Frame<TransformTrackingValue>[] frames, int i, Frame<TransformTrackingValue> frame, MethodInsnNode methodCall) {
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
        } else {
            //If there is none, we create a default transform
            if (returnValue != null && returnValue.getTransform().resultingTypes().size() > 1) {
                throw new IllegalStateException("Cannot generate default replacement for method with multiple return types '" + methodID + "'");
            }

            applyDefaultReplacement(context, i, methodCall, returnValue, args);
        }
    }

    /**
     * Transform a method call whose replacement is given in the config
     *
     * @param context Transform context
     * @param methodCall The actual method cal insn
     * @param info The replacement to apply
     * @param args The arguments of the method call. This should include the instance ('this') if it is a non-static method
     */
    private void applyReplacement(TransformContext context, MethodInsnNode methodCall, MethodParameterInfo info, TransformTrackingValue[] args) {
        MethodReplacement replacement = info.getReplacement();

        if (!replacement.changeParameters() && info.getReturnType().resultingTypes().size() > 1) {
            throw new IllegalStateException("Multiple return types not supported");
        }

        int insnIndex = context.indexLookup.get(methodCall);

        //Store all the parameters
        InsnList replacementInstructions = new InsnList();

        int totalSize = 0;
        int[][] offsets = new int[args.length][];

        for (int i = 0; i < args.length; i++) {
            TransformSubtype transform = args[i].getTransform();
            offsets[i] = transform.getIndices();

            for (int j = 0; j < offsets[i].length; j++) {
                offsets[i][j] += totalSize;
            }

            totalSize += transform.getTransformType() == null ? args[0].getSize() : transform.getTransformedSize();
        }

        int baseIdx = context.variableAllocator.allocate(insnIndex, insnIndex + 1, totalSize);

        for (int i = args.length; i > 0; i--) {
            storeStackInLocals(args[i - 1].getTransform(), replacementInstructions, baseIdx + offsets[i - 1][0]);
        }

        for (int i = 0; i < replacement.getBytecodeFactories().length; i++) {
            BytecodeFactory factory = replacement.getBytecodeFactories()[i];
            List<Integer>[] indices = replacement.getParameterIndices()[i];

            loadIndices(args, replacementInstructions, offsets, baseIdx, indices);
            replacementInstructions.add(
                factory.generate(s -> context.variableAllocator.allocate(insnIndex, insnIndex + 1, s))
            );
        }

        if (replacement.getFinalizer() != null) {
            loadIndices(args, replacementInstructions, offsets, baseIdx, replacement.getFinalizerIndices());
            replacementInstructions.add(
                replacement.getFinalizer().generate(s -> context.variableAllocator.allocate(insnIndex, insnIndex + 1, s))
            );
        }

        //Insert new code
        context.target().instructions.insert(methodCall, replacementInstructions);
        context.target().instructions.remove(methodCall);
    }

    /**
     * Transform a method call with which doesn't have a provided replacement. This is done by getting the transformed type of every value that is passed to the method and changing the
     * descriptor so as to match that. It will assume that this method exists.
     *
     * @param context Transform context
     * @param methodCall The actual method call
     * @param returnValue The return value of the method call, if the method returns void this should be null
     * @param args The arguments of the method call. This should include the instance ('this') if it is a non-static method
     */
    private void applyDefaultReplacement(TransformContext context, int i, MethodInsnNode methodCall, TransformTrackingValue returnValue, TransformTrackingValue[] args) {
        //Special case Arrays.fill
        if (methodCall.owner.equals("java/util/Arrays") && methodCall.name.equals("fill")) {
            transformArraysFill(context, i, methodCall, args);
            return;
        }

        //Get the actual values passed to the method. If the method is not static then the first value is the instance
        boolean isStatic = (methodCall.getOpcode() == Opcodes.INVOKESTATIC);
        int staticOffset = isStatic ? 0 : 1;

        TransformSubtype returnType = TransformSubtype.createDefault();
        TransformSubtype[] argTypes = new TransformSubtype[args.length - staticOffset];

        if (returnValue != null) {
            returnType = returnValue.getTransform();
        }

        for (int j = staticOffset; j < args.length; j++) {
            argTypes[j - staticOffset] = args[j].getTransform();
        }

        //Create the new descriptor
        String newDescriptor = MethodParameterInfo.getNewDesc(returnType, argTypes, methodCall.desc);
        methodCall.desc = newDescriptor;

        Type methodOwner = Type.getObjectType(methodCall.owner);
        if (this.config.getTypesWithSuffixedTransforms().contains(methodOwner)) {
            if (args.length == staticOffset) {
                methodCall.name += MIX;
            } else {
                for (TransformTrackingValue arg : args) {
                    if (arg.getTransformType() != null) {
                        methodCall.name += MIX;
                        break;
                    }
                }
            }
        }

        if (isStatic) {
            return;
        }

        //Change the method owner if needed
        List<Type> types = args[0].transformedTypes();
        if (types.size() != 1) {
            throw new IllegalStateException(
                "Expected 1 subType but got " + types.size() + ". Define a custom replacement for this method (" + methodCall.owner + "#" + methodCall.name + methodCall.desc + ")");
        }

        TypeInfo hierarchy = config.getTypeInfo();

        Type potentionalOwner = types.get(0);
        if (methodCall.getOpcode() != Opcodes.INVOKESPECIAL) {
            findOwnerNormal(methodCall, hierarchy, potentionalOwner);
        } else {
            findOwnerInvokeSpecial(methodCall, args, hierarchy, potentionalOwner);
        }
    }

    private void transformArraysFill(TransformContext context, int i, MethodInsnNode methodCall, TransformTrackingValue[] args) {
        TransformTrackingValue fillWith = args[1];
        TransformTrackingValue array = args[0];

        if (!array.isTransformed()) return;

        int arrayBase = context.variableAllocator.allocate(i, i + 1, array.getTransformedSize());
        int fillWithBase = context.variableAllocator.allocate(i, i + 1, fillWith.getTransformedSize());

        int[] arrayOffsets = array.getTransform().getIndices();
        int[] fillWithOffsets = fillWith.getTransform().getIndices();

        List<Type> arrayTypes = array.getTransform().resultingTypes();
        List<Type> fillWithTypes = fillWith.getTransform().resultingTypes();

        InsnList replacement = new InsnList();

        storeStackInLocals(fillWith.getTransform(), replacement, fillWithBase);
        storeStackInLocals(array.getTransform(), replacement, arrayBase);

        for (int j = 0; j < arrayOffsets.length; j++) {
            replacement.add(new VarInsnNode(arrayTypes.get(j).getOpcode(Opcodes.ILOAD), arrayBase + arrayOffsets[j]));
            replacement.add(new VarInsnNode(fillWithTypes.get(j).getOpcode(Opcodes.ILOAD), fillWithBase + fillWithOffsets[j]));

            Type baseType = simplify(fillWithTypes.get(j));
            Type arrayType = Type.getType("[" + baseType.getDescriptor());

            replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/util/Arrays",
                "fill",
                Type.getMethodType(Type.VOID_TYPE, arrayType, baseType).getDescriptor(),
                false
            ));
        }

        context.target.instructions.insert(methodCall, replacement);
        context.target.instructions.remove(methodCall);

        return;
    }

    private void findOwnerNormal(MethodInsnNode methodCall, TypeInfo hierarchy, Type potentionalOwner) {
        int opcode = methodCall.getOpcode();

        if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
            if (!potentionalOwner.equals(Type.getObjectType(methodCall.owner))) {
                boolean isNewTypeInterface = hierarchy.recognisesInterface(potentionalOwner);
                opcode = isNewTypeInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;

                methodCall.itf = isNewTypeInterface;
            }
        }

        methodCall.owner = potentionalOwner.getInternalName();
        methodCall.setOpcode(opcode);
    }

    private void findOwnerInvokeSpecial(MethodInsnNode methodCall, TransformTrackingValue[] args, TypeInfo hierarchy, Type potentionalOwner) {
        String currentOwner = methodCall.owner;
        TypeInfo.Node current = hierarchy.getNode(Type.getObjectType(currentOwner));
        TypeInfo.Node potential = hierarchy.getNode(potentionalOwner);
        TypeInfo.Node given = hierarchy.getNode(args[0].getType());

        if (given == null || current == null) {
            System.err.println("Don't have hierarchy for " + args[0].getType() + " or " + methodCall.owner);
            methodCall.owner = potentionalOwner.getInternalName();
        } else if (given.isDirectDescendantOf(current)) {
            if (potential == null || potential.getSuperclass() == null) {
                throw new IllegalStateException("Cannot change owner of super call if hierarchy for " + potentionalOwner + " is not defined");
            }

            Type newOwner = potential.getSuperclass().getValue();
            methodCall.owner = newOwner.getInternalName();
        } else {
            methodCall.owner = potentionalOwner.getInternalName();
        }
    }

    private void transformVarInsn(TransformContext context, int i, VarInsnNode varNode) {
        /*
         * There are two reasons this is needed.
         * 1. Some values take up different amount of variable slots because of their transforms, so we need to shift all variables accesses'
         * 2. When actually storing or loading a transformed value, we need to store all of it's transformed values correctly
         */

        //Get the shifted variable index
        int originalVarIndex = varNode.var;
        int newVarIndex = context.varLookup()[originalVarIndex];

        //Base opcode makes it easier to determine what kind of instruction we are dealing with
        int baseOpcode = switch (varNode.getOpcode()) {
            case Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.LLOAD -> Opcodes.ILOAD;
            case Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.LSTORE -> Opcodes.ISTORE;
            default -> throw new IllegalStateException("Unknown opcode: " + varNode.getOpcode());
        };

        //If the variable is being loaded, it is in the current frame, if it is being stored, it will be in the next frame
        TransformSubtype varType = context.varTypes()[i + (baseOpcode == Opcodes.ISTORE ? 1 : 0)][originalVarIndex];
        //Get the actual types that need to be stored or loaded
        List<Type> types = varType.resultingTypes();

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
    }

    private void transformIincInsn(TransformContext context, IincInsnNode iincNode) {
        //We just need to shift the index of the variable because incrementing transformed values is not supported
        int originalVarIndex = iincNode.var;
        iincNode.var = context.varLookup()[originalVarIndex];
    }

    private void transformConstantInsn(TransformContext context, Frame<TransformTrackingValue> frame, int i, AbstractInsnNode instruction) {
        //Check if value is transformed
        TransformTrackingValue value = ASMUtil.getTop(frame);
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
                newInstructions.add(factory.generate(t -> context.variableAllocator.allocate(i, i + 1, t)));
            }

            context.target().instructions.insert(instruction, newInstructions);
            context.target().instructions.remove(instruction); //Remove the original instruction
        }
    }

    private void transformCmp(TransformContext context, int insnIdx, AbstractInsnNode instruction, Frame<TransformTrackingValue> frame,
                              int opcode) {
        //Get the actual values that are being compared
        TransformTrackingValue left = frame.getStack(frame.getStackSize() - 2);
        TransformTrackingValue right = frame.getStack(frame.getStackSize() - 1);

        if (left.getTransformType() == null) return; //No transform needed

        if (!left.getTransform().equals(right.getTransform())) {
            //Should be unreachable
            throw new IllegalStateException("Expected same transform, found " + left.getTransform() + " and " + right.getTransform());
        }

        TransformSubtype transformType = left.getTransform();
        List<Type> types = transformType.resultingTypes();
        int[] varOffsets = transformType.getIndices();
        int size = transformType.getTransformedSize();

        InsnList list = new InsnList();

        //Store both values on the stack into locals
        int baseIdx = context.variableAllocator.allocate(insnIdx, insnIdx + 1, size * 2);

        int leftIdx = baseIdx;
        int rightIdx = baseIdx + size;

        storeStackInLocals(transformType, list, rightIdx);
        storeStackInLocals(transformType, list, leftIdx);

        if (opcode == Opcodes.LCMP || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG) {
            //TODO: For now this will return 0 if all the values are equal and 1 otherwise. Could be improved to allow for LE and GE
            LabelNode escape = new LabelNode();

            list.add(new LdcInsnNode(1));

            for (int i = 0; i < types.size(); i++) {
                list.add(new VarInsnNode(types.get(i).getOpcode(Opcodes.ILOAD), leftIdx + varOffsets[i]));
                list.add(new VarInsnNode(types.get(i).getOpcode(Opcodes.ILOAD), rightIdx + varOffsets[i]));

                ASMUtil.jumpIfCmp(list, types.get(i), false, escape);
            }

            list.add(new InsnNode(Opcodes.POP));
            list.add(new LdcInsnNode(0));
            list.add(escape);
        } else {
            JumpInsnNode jump = (JumpInsnNode) instruction;

            LabelNode target = jump.label;
            LabelNode normal = new LabelNode();

            boolean isEq = switch (opcode) {
                case Opcodes.IF_ICMPEQ, Opcodes.IF_ACMPEQ -> true;
                case Opcodes.IF_ICMPNE, Opcodes.IF_ACMPNE -> false;
                default -> throw new IllegalStateException("Unexpected value: " + opcode);
            };

            for (int i = 0; i < types.size(); i++) {
                list.add(new VarInsnNode(types.get(i).getOpcode(Opcodes.ILOAD), leftIdx + varOffsets[i]));
                list.add(new VarInsnNode(types.get(i).getOpcode(Opcodes.ILOAD), rightIdx + varOffsets[i]));

                ASMUtil.jumpIfCmp(list, types.get(i), false, isEq ? normal : target);
            }
        }

        context.target().instructions.insert(instruction, list);
        context.target().instructions.remove(instruction);
    }

    private void transformInvokeDynamicInsn(Frame<TransformTrackingValue>[] frames, int i, Frame<TransformTrackingValue> frame, InvokeDynamicInsnNode dynamicInsnNode) {
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

            String methodName = methodReference.getName();
            String methodDesc = methodReference.getDesc();
            String methodOwner = methodReference.getOwner();
            boolean itf = methodReference.isInterface();

            int tag = methodReference.getTag();
            if (!methodOwner.equals(classNode.name)) {
                MethodID method = new MethodID(Type.getObjectType(methodOwner), methodName, Type.getMethodType(methodDesc), isStatic ? MethodID.CallType.STATIC : MethodID.CallType.VIRTUAL);
                MethodID newMethod = this.makeOwnMethod(method, values, ((Type) dynamicInsnNode.bsmArgs[0]).getReturnType().getSort() == Type.VOID);

                methodName = newMethod.getName();
                methodDesc = newMethod.getDescriptor().getDescriptor();
                methodOwner = newMethod.getOwner().getInternalName();
                itf = false;

                tag = Opcodes.H_INVOKESTATIC;
                staticOffset = 0;
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

            String newName = methodName;
            if (config.getTypesWithSuffixedTransforms().contains(Type.getObjectType(methodOwner))) {
                newName += MIX;
            }

            //This is by no means a good solution but it's good enough for now
            Type[] actualLambdaArgs = new Type[lambdaArgs.length];
            for (int j = 0; j < actualLambdaArgs.length; j++) {
                actualLambdaArgs[j] = simplify(lambdaArgs[j]);
            }
            Type actualLambdaReturnType = simplify(Type.getReturnType(newDesc));

            dynamicInsnNode.bsmArgs = new Object[] {
                Type.getType(Type.getMethodType(actualLambdaReturnType, actualLambdaArgs).getDescriptor()),
                new Handle(tag, methodOwner, newName, newReferenceDesc, itf),
                Type.getMethodType(lambdaDesc)
            };
        }
    }

    private void transformNewInsn(Frame<TransformTrackingValue> frame1, TypeInsnNode instruction) {
        TransformTrackingValue value = ASMUtil.getTop(frame1);
        if (value.getTransform().getTransformType() != null) {
            instruction.desc = value.getTransform().getSingleType().getInternalName();
        }
    }

    private void transformNewArray(TransformContext context, int i, Frame<TransformTrackingValue> frame, AbstractInsnNode instruction, int dimsKnown) {
        TransformTrackingValue top = ASMUtil.getTop(frame);

        if (!top.isTransformed()) return;

        int dimsNeeded = top.getTransform().getArrayDimensionality();

        int dimsSaved = context.variableAllocator.allocate(i, i + 1, dimsKnown);

        for (int j = dimsKnown - 1; j < dimsNeeded; j++) {
            context.target.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ISTORE, dimsSaved + j));
        }

        for (Type result : top.getTransform().resultingTypes()) {
            for (int j = 0; j < dimsNeeded; j++) {
                context.target.instructions.insertBefore(instruction, new VarInsnNode(Opcodes.ILOAD, dimsSaved + j));
            }

            context.target.instructions.insertBefore(instruction, ASMUtil.makeNew(result, dimsKnown));
        }

        context.target.instructions.remove(instruction);
    }

    private void transformArrayLoad(TransformContext context, int i, AbstractInsnNode instruction, Frame<TransformTrackingValue> frame) {
        TransformTrackingValue array = frame.getStack(frame.getStackSize() - 2);

        if (!array.isTransformed()) return;

        InsnList list = new InsnList();

        int indexIdx = context.variableAllocator.allocateSingle(i, i + 1);
        list.add(new VarInsnNode(Opcodes.ISTORE, indexIdx));

        int arrayIdx = context.variableAllocator.allocate(i, i + 1, array.getTransformedSize());
        int[] arrayOffsets = array.getTransform().getIndices();
        storeStackInLocals(array.getTransform(), list, arrayIdx);

        List<Type> types = array.getTransform().resultingTypes();

        for (int j = 0; j < arrayOffsets.length; j++) {
            list.add(new VarInsnNode(Opcodes.ALOAD, arrayIdx + arrayOffsets[j]));
            list.add(new VarInsnNode(Opcodes.ILOAD, indexIdx));

            Type type = types.get(j);
            Type resultType = Type.getType("[".repeat(type.getDimensions() - 1) + type.getElementType().getDescriptor());

            list.add(new InsnNode(resultType.getOpcode(Opcodes.IALOAD)));
        }

        context.target.instructions.insert(instruction, list);
        context.target.instructions.remove(instruction);
    }

    private void transformArrayStore(TransformContext context, int i, AbstractInsnNode instruction, Frame<TransformTrackingValue> frame) {
        TransformTrackingValue array = frame.getStack(frame.getStackSize() - 3);
        TransformTrackingValue value = frame.getStack(frame.getStackSize() - 1);

        if (!array.isTransformed()) return;

        InsnList list = new InsnList();

        int valueIdx = context.variableAllocator.allocate(i, i + 1, value.getTransformedSize());
        int[] valueOffsets = value.getTransform().getIndices();
        storeStackInLocals(value.getTransform(), list, valueIdx);

        int indexIdx = context.variableAllocator.allocateSingle(i, i + 1);
        list.add(new VarInsnNode(Opcodes.ISTORE, indexIdx));

        int arrayIdx = context.variableAllocator.allocate(i, i + 1, array.getTransformedSize());
        int[] arrayOffsets = array.getTransform().getIndices();
        storeStackInLocals(array.getTransform(), list, arrayIdx);

        List<Type> types = value.getTransform().resultingTypes();

        for (int j = 0; j < arrayOffsets.length; j++) {
            Type type = types.get(j);

            list.add(new VarInsnNode(Opcodes.ALOAD, arrayIdx + arrayOffsets[j]));
            list.add(new VarInsnNode(Opcodes.ILOAD, indexIdx));
            list.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), valueIdx + valueOffsets[j]));

            list.add(new InsnNode(type.getOpcode(Opcodes.IASTORE)));
        }

        context.target.instructions.insert(instruction, list);
        context.target.instructions.remove(instruction);
    }

    private void transformFieldInsn(TransformContext context, int i, FieldInsnNode instruction) {
        FieldID fieldID = new FieldID(Type.getObjectType(instruction.owner), instruction.name, Type.getType(instruction.desc));
        boolean isStatic = instruction.getOpcode() == Opcodes.GETSTATIC || instruction.getOpcode() == Opcodes.PUTSTATIC;
        boolean isPut = instruction.getOpcode() == Opcodes.PUTSTATIC || instruction.getOpcode() == Opcodes.PUTFIELD;

        if (!fieldID.owner().getInternalName().equals(this.classNode.name)) {
            return;
        }

        TransformTrackingValue field = this.fieldPseudoValues.get(fieldID);

        if (!field.isTransformed()) return;

        InsnList result = new InsnList();

        int objIdx = -1;
        int arrayIdx = -1;
        int[] offsets = field.getTransform().getIndices();

        if (isPut) {
            arrayIdx = context.variableAllocator.allocate(i, i + 1, field.getTransformedSize());
            storeStackInLocals(field.getTransform(), result, arrayIdx);
        }

        if (!isStatic) {
            objIdx = context.variableAllocator().allocate(i, i + 1, 1);
            result.add(new VarInsnNode(Opcodes.ASTORE, objIdx));
        }

        List<Type> types = field.getTransform().resultingTypes();
        List<String> names = new ArrayList<>();

        for (int j = 0; j < types.size(); j++) {
            names.add(this.getExpandedFieldName(fieldID, j));
        }

        for (int j = 0; j < types.size(); j++) {
            Type type = types.get(j);
            String name = names.get(j);

            if (!isStatic) {
                result.add(new VarInsnNode(Opcodes.ALOAD, objIdx));
            }

            if (isPut) {
                result.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), arrayIdx + offsets[j]));
            }

            result.add(new FieldInsnNode(instruction.getOpcode(), this.classNode.name, name, type.getDescriptor()));
        }

        context.target.instructions.insertBefore(instruction, result);
        context.target.instructions.remove(instruction);
    }

    /**
     * Transform and add a constructor. Replacement info must be provided
     *
     * @param desc The descriptor of the original constructor
     */
    public void makeConstructor(String desc) {
        ConstructorReplacer replacer = transformInfo.getConstructorReplacers().get(desc);

        if (replacer == null) {
            throw new RuntimeException("No replacement info found for constructor " + desc);
        }

        makeConstructor(desc, replacer.make(this));
    }

    /**
     * Add a constructor to the class
     *
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
        for (AbstractInsnNode node : nodes) {
            if (node.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode methodNode = (MethodInsnNode) node;
                if (methodNode.owner.equals(classNode.superName)) {
                    //Insert the safety check right after the super call
                    constructor.insert(methodNode, safetyCheck);
                    break;
                }
            }
        }

        //Shift variables
        for (AbstractInsnNode node : nodes) {
            if (node instanceof VarInsnNode varNode) {
                if (varNode.var >= totalSize) {
                    varNode.var++;
                }
            } else if (node instanceof IincInsnNode iincNode) {
                if (iincNode.var >= totalSize) {
                    iincNode.var++;
                }
            }
        }

        MethodNode methodNode = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", newDesc, null, null);
        methodNode.instructions.add(constructor);

        markSynthetic(methodNode, "CONSTRUCTOR", "<init>" + desc, classNode.name);

        newMethods.add(methodNode);
    }


    private void transformAbstractMethod(MethodNode methodNode, long start, MethodID methodID, MethodNode newMethod, TransformContext context) {
        //If the method is abstract, we don't need to transform its code, just it's descriptor
        transformDesc(newMethod, context);

        if (methodNode.parameters != null) {
            this.modifyParameterTable(newMethod, context);
        }

        System.out.println("Transformed method '" + methodID + "' in " + (System.currentTimeMillis() - start) + "ms");
    }

    /**
     * Modifies the variable and parameter tables (if they exist) to make it easier to read the generated code when decompiled
     *
     * @param methodNode The method to modify
     * @param context The transform context
     */
    private void modifyLVT(MethodNode methodNode, TransformContext context) {
        if (methodNode.localVariables != null) {
            modifyVariableTable(methodNode, context);
        }

        //Similar algorithm for parameters
        if (methodNode.parameters != null) {
            modifyParameterTable(methodNode, context);
        }
    }

    private void modifyParameterTable(MethodNode methodNode, TransformContext context) {
        List<ParameterNode> original = methodNode.parameters;

        List<ParameterNode> newParameters = new ArrayList<>();

        int index = 0;
        if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
            index++;
        }
        for (ParameterNode param : original) {
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

    private void modifyVariableTable(MethodNode methodNode, TransformContext context) {
        List<LocalVariableNode> original = methodNode.localVariables;
        List<LocalVariableNode> newLocalVariables = new ArrayList<>();

        for (LocalVariableNode local : original) {
            int codeIndex = context.indexLookup().get(local.start); //The index of the first frame with that variable
            int newIndex = context.varLookup[local.index];

            TransformTrackingValue value = context.analysisResults().frames()[codeIndex].getLocal(local.index); //Get the value of that variable, so we can get its transform
            if (value.getTransformType() == null || value.getTransform().getSubtype() != TransformSubtype.SubType.NONE) {
                String desc;
                if (value.getTransformType() == null) {
                    Type type = value.getType();
                    if (type == null) {
                        continue;
                    } else {
                        desc = value.getType().getDescriptor();
                    }
                } else {
                    desc = value.getTransform().getSingleType().getDescriptor();
                }
                newLocalVariables.add(new LocalVariableNode(local.name, desc, local.signature, local.start, local.end, newIndex));
            } else {
                String[] postfixes = value.getTransformType().getPostfix();
                int varIndex = newIndex;
                for (int j = 0; j < postfixes.length; j++) {
                    newLocalVariables.add(
                        new LocalVariableNode(
                            local.name + postfixes[j],
                            value.getTransformType().getTo()[j].getDescriptor(),
                            local.signature, local.start, local.end, varIndex
                        )
                    );
                    varIndex += value.getTransformType().getTo()[j].getSize();
                }
            }
        }

        methodNode.localVariables = newLocalVariables;
    }

    private void getAllMethodInfo(AbstractInsnNode[] insns, AbstractInsnNode[] instructions, Frame<TransformTrackingValue>[] frames, MethodParameterInfo[] methodInfos) {
        for (int i = 0; i < insns.length; i++) {
            AbstractInsnNode insn = instructions[i];
            Frame<TransformTrackingValue> frame = frames[i];
            if (insn instanceof MethodInsnNode methodCall) {
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

                if (infos != null) {
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
    }

    private MethodID makeOwnMethod(MethodID method, TransformTrackingValue[] argsForAnalysis, boolean returnVoid) {
        if (this.externalMethodReplacements.containsKey(method)) {
            return this.externalMethodReplacements.get(method);
        }

        Type[] args = method.getDescriptor().getArgumentTypes();
        Type[] actualArgs;
        if (method.isStatic()) {
            actualArgs = args;
        } else {
            actualArgs = new Type[args.length + 1];
            actualArgs[0] = argsForAnalysis[0].getType();
            System.arraycopy(args, 0, actualArgs, 1, args.length);
        }

        String name = "external_" + method.getOwner().getClassName().replace('.', '_') + "_" + method.getName() + "_" + externalMethodReplacements.size();

        if (!method.isStatic()) {
            name += actualArgs[0].getClassName().replace('.', '_');
        }

        MethodNode node = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            name,
            Type.getMethodDescriptor(returnVoid ? Type.VOID_TYPE : method.getDescriptor().getReturnType(), actualArgs),
            null,
            null
        );

        int localSize = 0;
        for (Type actualArg : actualArgs) {
            node.instructions.add(new VarInsnNode(actualArg.getOpcode(Opcodes.ILOAD), localSize));
            localSize += actualArg.getSize();
        }

        boolean itf = config.getTypeInfo().recognisesInterface(method.getOwner());
        node.instructions.add(
            new MethodInsnNode(
                method.isStatic() ? Opcodes.INVOKESTATIC : (itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL),
                method.getOwner().getInternalName(),
                method.getName(),
                method.getDescriptor().getDescriptor(),
                itf
            )
        );

        if (!returnVoid) {
            node.instructions.add(new InsnNode(method.getDescriptor().getReturnType().getOpcode(Opcodes.IRETURN)));
        } else {
            if (method.getDescriptor().getReturnType().getSort() != Type.VOID) {
                if (method.getDescriptor().getReturnType().getSize() == 2) {
                    node.instructions.add(new InsnNode(Opcodes.POP2));
                } else {
                    node.instructions.add(new InsnNode(Opcodes.POP));
                }

                node.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }

        node.maxLocals = localSize;
        node.maxStack = actualArgs.length;

        MethodID newMethod = new MethodID(Type.getObjectType(this.classNode.name), node.name, Type.getMethodType(node.desc), MethodID.CallType.STATIC);
        this.externalMethodReplacements.put(method, newMethod);

        AnalysisResults results = this.analyzeMethod(node);

        int idx = 0;
        for (TransformTrackingValue arg : argsForAnalysis) {
            TransformTrackingValue.setSameType(arg, results.frames()[0].getLocal(idx));
            idx += arg.getTransform().getTransformedSize();
        }

        this.generateTransformedMethod(node);

        this.newMethods.add(node);

        return newMethod;
    }

    /**
     * Makes all call to super constructor add the magic value so that it is initialized transformed
     */
    public void callMagicSuperConstructor() {
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals("<init>")) {
                MethodInsnNode superCall = findSuperCall(methodNode);
                String[] parts = superCall.desc.split("\\)");
                String newDesc = parts[0] + "I)" + parts[1];
                superCall.desc = newDesc;
                methodNode.instructions.insertBefore(superCall, new LdcInsnNode(MAGIC));
            }
        }
    }

    /**
     * Should be called after all transforms have been applied.
     */
    public void cleanUpTransform() {
        //Add methods that need to be added
        classNode.methods.addAll(lambdaTransformers);

        for (MethodNode newMethod : newMethods) {
            MethodNode existing = classNode.methods.stream().filter(m -> m.name.equals(newMethod.name) && m.desc.equals(newMethod.desc)).findFirst().orElse(null);

            if (existing != null) {
                if (!inPlace) {
                    throw new IllegalStateException("Method " + newMethod.name + newMethod.desc + " already exists in class " + classNode.name);
                } else {
                    classNode.methods.remove(existing);
                }
            }

            classNode.methods.add(newMethod);
        }

        if (hasTransformedFields && !inPlace) {
            addSafetyFieldSetter();
        }

        if (inPlace) {
            modifyFields();
        } else {
            makeFieldCasts();
        }
    }

    private void addSafetyFieldSetter() {
        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals("<init>")) {
                if (isSynthetic(methodNode)) continue;

                insertAtReturn(methodNode, (variableAllocator) -> {
                    InsnList instructions = new InsnList();
                    instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new InsnNode(Opcodes.ICONST_0));
                    instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, classNode.name, isTransformedField.name(), "Z"));
                    return instructions;
                });
            }
        }
    }

    private void modifyFields() {
        List<FieldNode> toAdd = new ArrayList<>();
        List<FieldNode> toRemove = new ArrayList<>();

        for (FieldNode field : classNode.fields) {
            FieldID fieldID = new FieldID(Type.getObjectType(classNode.name), field.name, Type.getType(field.desc));
            TransformTrackingValue value = fieldPseudoValues.get(fieldID);

            if (!value.isTransformed()) continue;

            if ((field.access & Opcodes.ACC_PRIVATE) == 0) {
                throw new IllegalStateException("Field " + field.name + " in class " + classNode.name + " is not private");
            }

            List<Type> types = value.getTransform().resultingTypes();
            List<String> names = new ArrayList<>();

            //Make new fields
            for (int i = 0; i < types.size(); i++) {
                Type type = types.get(i);
                String name = getExpandedFieldName(fieldID, i);
                names.add(name);

                FieldNode newField = new FieldNode(field.access, name, type.getDescriptor(), null, null);
                toAdd.add(newField);
            }

            //Remove old field
            toRemove.add(field);
        }

        classNode.fields.removeAll(toRemove);
        classNode.fields.addAll(toAdd);
    }

    /**
     * One of the aspects of this transformer is that if the original methods are called then the behaviour should be normal. This means that if a field's type needs to be changed then old
     * methods would still need to use the old field type and new methods would need to use the new field type. Instead of duplicating each field, we turn the type of each of these fields
     * into {@link Object} and cast them to their needed type. To initialize these fields to their transformed types, we create a new constructor.
     * <br><br><i>(This does not apply for "in place" transformations)</i>
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
    private void makeFieldCasts() {
        for (var entry : fieldPseudoValues.entrySet()) {
            if (entry.getValue().getTransformType() == null) {
                continue;
            }

            TransformSubtype transformType = entry.getValue().getTransform();
            FieldID fieldID = entry.getKey();

            String originalType = entry.getValue().getType().getInternalName();
            String transformedType = transformType.getSingleType().getInternalName();

            ASMUtil.changeFieldType(classNode, fieldID, Type.getObjectType("java/lang/Object"), (method) -> {
                InsnList insnList = new InsnList();
                if (isSynthetic(method)) {
                    insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, transformedType));
                } else {
                    insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, originalType));
                }
                return insnList;
            });
        }
    }

    private String getExpandedFieldName(FieldID field, int idx) {
        TransformTrackingValue value = this.fieldPseudoValues.get(field);

        if (!value.isTransformed()) throw new IllegalArgumentException("Field " + field + " is not transformed");

        return field.name() + "_expanded" + value.getTransformType().getPostfix()[idx];
    }


    // CC-SYNTHETIC METHOD ANNOTATIONS

    /**
     * Adds the {@link CCSynthetic} annotation to the provided method
     *
     * @param methodNode The method to mark
     * @param subType The type of synthetic method this is
     * @param original The original method this is a synthetic version of
     */
    private static void markSynthetic(MethodNode methodNode, String subType, String original, String ownerName) {
        List<AnnotationNode> annotations = methodNode.visibleAnnotations;
        if (annotations == null) {
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

        //Stack traces don't specify the descriptor so we set the line numbers to a known value to detect whether we were in a CC synthetic emthod

        int lineStart = 60000;
        Int2ObjectMap<String> descLookup = CC_SYNTHETIC_LOOKUP.computeIfAbsent(ownerName, k -> new Int2ObjectOpenHashMap<>());
        while (descLookup.containsKey(lineStart)) {
            lineStart += 10;

            if (lineStart >= (1 << 16)) {
                throw new RuntimeException("Too many CC synthetic methods");
            }
        }

        //Remove previous line numbers
        for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
            if (insnNode instanceof LineNumberNode) {
                methodNode.instructions.remove(insnNode);
            }
        }

        descLookup.put(lineStart, methodNode.desc);

        //Add our own
        LabelNode start = new LabelNode();
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), new LineNumberNode(lineStart, start));
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), start);
    }

    public static @Nullable Method getSyntheticMethod(Class<?> owner, String name, int lineNumber) {
        String ownerName = owner.getName().replace('.', '/');

        Int2ObjectMap<String> descLookup = CC_SYNTHETIC_LOOKUP.computeIfAbsent(ownerName, k -> new Int2ObjectOpenHashMap<>());
        String desc = descLookup.get((lineNumber / 10) * 10);

        if (desc == null) {
            return null;
        }

        Class<?>[] types = Arrays.stream(Type.getArgumentTypes(desc))
            .map((t) -> {
                if (t == Type.BYTE_TYPE) {
                    return byte.class;
                } else if (t == Type.SHORT_TYPE) {
                    return short.class;
                } else if (t == Type.INT_TYPE) {
                    return int.class;
                } else if (t == Type.LONG_TYPE) {
                    return long.class;
                } else if (t == Type.FLOAT_TYPE) {
                    return float.class;
                } else if (t == Type.DOUBLE_TYPE) {
                    return double.class;
                } else if (t == Type.BOOLEAN_TYPE) {
                    return boolean.class;
                } else if (t == Type.CHAR_TYPE) {
                    return char.class;
                } else if (t == Type.VOID_TYPE) {
                    return void.class;
                } else {
                    try {
                        return Class.forName(t.getClassName());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).toArray(Class<?>[]::new);

        try {
            Method method = owner.getMethod(name, types);

            CCSynthetic annotation = method.getAnnotation(CCSynthetic.class);

            if (annotation == null) {
                return null;
            }

            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Checks if the provided method has the {@link CCSynthetic} annotation
     *
     * @param methodNode The method to check
     *
     * @return True if the method is synthetic, false otherwise
     */
    private static boolean isSynthetic(MethodNode methodNode) {
        List<AnnotationNode> annotations = methodNode.visibleAnnotations;
        if (annotations == null) {
            return false;
        }

        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(Type.getDescriptor(CCSynthetic.class))) {
                return true;
            }
        }

        return false;
    }

    /**
     * This method is called by safety dispatches (Called from ASM - DO NOT RENAME/REMOVE)
     *
     * @param message The message to print
     */
    public static void emitWarning(String message, int callerDepth) {
        //Gather info about exactly where this was called
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stackTrace[callerDepth];

        String warningID = message + " at " + caller.getClassName() + "." + caller.getMethodName() + ":" + caller.getLineNumber();
        if (WARNINGS.add(warningID)) {
            System.out.println("[CC Warning] " + warningID);
            try {
                FileOutputStream fos = new FileOutputStream(ERROR_LOG.toFile());
                for (String warning : WARNINGS) {
                    fos.write(warning.getBytes());
                    fos.write("\n".getBytes());
                }
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static InsnList generateEmitWarningCall(String message, int callerDepth) {
        InsnList instructions = new InsnList();

        instructions.add(new LdcInsnNode(message));
        instructions.add(new LdcInsnNode(callerDepth));

        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "io/github/opencubicchunks/cubicchunks/mixin/transform/typetransformer/transformer/TypeTransformer", "emitWarning",
            "(Ljava/lang/String;I)V",
            false));

        return instructions;
    }

    private static Type simplify(Type type) {
        if (type.getSort() == Type.ARRAY || type.getSort() == Type.OBJECT) {
            return Type.getType(Object.class);
        } else {
            return type;
        }
    }

    private void storeStackInLocals(TransformSubtype transform, InsnList insnList, int baseIdx) {
        List<Type> types = transform.resultingTypes();
        int[] offsets = transform.getIndices();

        for (int i = types.size(); i > 0; i--) {
            Type type = types.get(i - 1);
            int offset = offsets[i - 1];
            insnList.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), baseIdx + offset));
        }
    }

    private void loadIndices(TransformTrackingValue[] args, InsnList replacementInstructions, int[][] offsets, int baseIdx, List<Integer>[] indices) {
        for (int j = 0; j < indices.length; j++) {
            List<Type> types = args[j].transformedTypes();
            for (int index: indices[j]) {
                int offset = offsets[j][index];
                replacementInstructions.add(new VarInsnNode(types.get(index).getOpcode(Opcodes.ILOAD), baseIdx + offset));
            }
        }
    }

    /**
     * Insert the provided code before EVERY return statement in a method
     *
     * @param methodNode The method to insert the code into
     * @param insn The code to insert
     */
    private static void insertAtReturn(MethodNode methodNode, BytecodeFactory insn) {
        InsnList instructions = methodNode.instructions;
        AbstractInsnNode[] nodes = instructions.toArray();

        for (AbstractInsnNode node : nodes) {
            if (node.getOpcode() == Opcodes.RETURN
                || node.getOpcode() == Opcodes.ARETURN
                || node.getOpcode() == Opcodes.IRETURN
                || node.getOpcode() == Opcodes.FRETURN
                || node.getOpcode() == Opcodes.DRETURN
                || node.getOpcode() == Opcodes.LRETURN) {

                //Since we are inserting code right before the return statement, there are no live variables, so we can use whatever variables we want.
                //For tidyness reasons we won't replace params

                int base = ASMUtil.isStatic(methodNode) ? 0 : 1;
                for (Type t : Type.getArgumentTypes(methodNode.desc)) {
                    base += t.getSize();
                }

                int finalBase = base;
                Function<Type, Integer> varAllocator = new Function<>() {
                    int curr = finalBase;

                    @Override
                    public Integer apply(Type type) {
                        int slot = curr;
                        curr += type.getSize();
                        return slot;
                    }
                };

                instructions.insertBefore(node, insn.generate(varAllocator));
            }
        }
    }

    private MethodInsnNode findSuperCall(MethodNode constructor) {
        for (AbstractInsnNode insn : constructor.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (methodInsn.owner.equals(classNode.superName) && methodInsn.name.equals("<init>")) {
                    return methodInsn;
                }
            }
        }

        throw new RuntimeException("Could not find super constructor call");
    }

    private void transformDesc(MethodNode methodNode, TransformContext context) {
        TransformSubtype[] actualParameters;
        if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
            actualParameters = new TransformSubtype[context.analysisResults().getArgTypes().length - 1];
            System.arraycopy(context.analysisResults().getArgTypes(), 1, actualParameters, 0, actualParameters.length);
        } else {
            actualParameters = context.analysisResults().getArgTypes();
        }

        //Change descriptor
        String newDescriptor = MethodParameterInfo.getNewDesc(TransformSubtype.createDefault(), actualParameters, methodNode.desc);
        methodNode.desc = newDescriptor;
    }

    /**
     * This method creates a jump to the given label if the fields hold transformed types or none of the fields need to be transformed.
     *
     * @param label The label to jump to.
     *
     * @return The instructions to jump to the given label.
     */
    public InsnList jumpIfNotTransformed(LabelNode label) {
        InsnList instructions = new InsnList();
        if (hasTransformedFields && !inPlace) {
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            instructions.add(new FieldInsnNode(Opcodes.GETFIELD, isTransformedField.owner().getInternalName(), isTransformedField.name(), isTransformedField.desc().getDescriptor()));
            instructions.add(new JumpInsnNode(Opcodes.IFEQ, label));
        }

        //If there are no transformed fields then we never jump.
        return instructions;
    }

    private boolean isACompare(int opcode) {
        return opcode == Opcodes.LCMP || opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG || opcode == Opcodes.DCMPL || opcode == Opcodes.DCMPG || opcode == Opcodes.IF_ICMPEQ
            || opcode == Opcodes.IF_ICMPNE || opcode == Opcodes.IF_ACMPEQ || opcode == Opcodes.IF_ACMPNE;
    }

    private boolean isArrayLoad(int opcode) {
        return opcode == Opcodes.IALOAD || opcode == Opcodes.LALOAD || opcode == Opcodes.FALOAD || opcode == Opcodes.DALOAD || opcode == Opcodes.AALOAD || opcode == Opcodes.BALOAD
            || opcode == Opcodes.CALOAD || opcode == Opcodes.SALOAD;
    }

    private boolean isArrayStore(int opcode) {
        return opcode == Opcodes.IASTORE || opcode == Opcodes.LASTORE || opcode == Opcodes.FASTORE || opcode == Opcodes.DASTORE || opcode == Opcodes.AASTORE || opcode == Opcodes.BASTORE
            || opcode == Opcodes.CASTORE || opcode == Opcodes.SASTORE;
    }

    public Map<MethodID, AnalysisResults> getAnalysisResults() {
        return analysisResults;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public Config getConfig() {
        return config;
    }

    /**
     * Stores all information needed to transform a method.
     *
     * @param target The method that is being transformed.
     * @param analysisResults The analysis results for this method that were generated by the analysis phase.
     * @param instructions The instructions of {@code target} before any transformations.
     * @param varLookup Stores the new index of a variable. varLookup[insnIndex][oldVarIndex] gives the new var index.
     * @param variableAllocator The variable manager allows for the creation of new variables.
     * @param indexLookup A map from instruction object to index in the instructions array. This map contains keys for the instructions of both the old and new methods. This is useful
     *     mainly because TransformTrackingValue.getSource() will return instructions from the old method and to manipulate the InsnList of the new method (which is a linked list) we need an
     *     element which is in that InsnList.
     * @param methodInfos If an instruction is a method invocation, this will store information about how to transform it.
     */
    private record TransformContext(
        MethodNode target,
        AnalysisResults analysisResults,
        AbstractInsnNode[] instructions,
        int[] varLookup,
        TransformSubtype[][] varTypes,
        VariableAllocator variableAllocator,
        Map<AbstractInsnNode, Integer> indexLookup,
        MethodParameterInfo[] methodInfos
    ) {}
}