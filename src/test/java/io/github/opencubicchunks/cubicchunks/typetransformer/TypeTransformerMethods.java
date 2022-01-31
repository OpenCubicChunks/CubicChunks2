package io.github.opencubicchunks.cubicchunks.typetransformer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.opencubicchunks.cubicchunks.mixin.ASMConfigPlugin;
import io.github.opencubicchunks.cubicchunks.mixin.transform.MainTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * This class runs the TypeTransformer on all required classes and tracks the methods which are assumed to exist.
 * This test makes the assumption that an untransformed class is completely correct.
 */
public class TypeTransformerMethods {
    private static final boolean LOAD_FROM_MIXIN_OUT = false;

    private static final Path assumedMixinOut = Utils.getGameDir().resolve(".mixin.out/class");
    private static final Map<String, ClassNode> cachedClasses = new HashMap<>();
    private ASMConfigPlugin plugin = new ASMConfigPlugin();

    @Test
    public void transformAndTest() {
        System.out.println("Config: " + MainTransformer.TRANSFORM_CONFIG); //Load MainTransformer

        MappingResolver map = Utils.getMappingResolver();

        final Set<String> classNamesToTransform = Stream.of(
            "net.minecraft.class_3554", //DynamicGraphMixFixedPoint
            "net.minecraft.class_3558", //LayerLightEngine
            "net.minecraft.class_3560", //LayerLightSectionStorage
            "net.minecraft.class_3547", //BlockLightSectionStorage
            "net.minecraft.class_3569", //SkyLightSectionStorage
            "net.minecraft.class_4076",
            "net.minecraft.class_3552",
            "net.minecraft.class_3572"
        ).map(name -> map.mapClassName("intermediary", name)).collect(Collectors.toSet());

        Set<MethodID> methodsUsed = new ObjectOpenCustomHashSet<>(MethodID.HASH_CALL_TYPE);
        Map<MethodID, List<String>> usages = new Object2ObjectOpenCustomHashMap<>(MethodID.HASH_CALL_TYPE);

        for (String className : classNamesToTransform) {
            ClassNode classNode = getClassNode(className);

            //Find all methods which are called
            for (MethodNode methodNode : classNode.methods) {
                InsnList instructions = methodNode.instructions;
                int i = 0;
                for(AbstractInsnNode instruction : instructions.toArray()) {
                    int opcode = instruction.getOpcode();
                    if (opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKESPECIAL) {
                        MethodID methodID = MethodID.from((MethodInsnNode) instruction);
                        methodsUsed.add(methodID);

                        List<String> usagesOfMethod = usages.computeIfAbsent(methodID, k -> new ArrayList<>());

                        usagesOfMethod.add(ASMUtil.onlyClassName(className) + " " + ASMUtil.prettyPrintMethod(methodNode.name, methodNode.desc) + " @ " + i);
                    }
                    i++;
                }
            }
        }

        System.out.println("Identified all used methods");
        Map<MethodID, String> faultyUses = new HashMap<>(); //MethodID -> Reason

        for(MethodID methodID : methodsUsed) {
            String result = checkMethod(methodID);
            if(result != null) {
                faultyUses.put(methodID, result);
            }
        }

        if(!faultyUses.isEmpty()) {
            System.out.println("Found faulty uses:");
            for(Map.Entry<MethodID, String> entry : faultyUses.entrySet()) {
                System.out.println("  - " + entry.getKey() + ": " + entry.getValue());
                for(String usage : usages.get(entry.getKey())) {
                    System.out.println("    - " + usage);
                }
            }
            throw new RuntimeException("Found faulty uses");
        }
    }

    private String checkMethod(MethodID methodID) {
        ClassNode classNode = getClassNode(methodID.getOwner().getClassName());
        ClassNode earliestDeclaringClass = null;
        MethodNode earliestDefinition = null;

        Set<String> interfacesToCheck = new HashSet<>();

        boolean isInterface = (classNode.access & Opcodes.ACC_INTERFACE) != 0;

        while (true) {
            Optional<MethodNode> methodNodeOptional = classNode.methods.stream().filter(m -> m.name.equals(methodID.getName()) && m.desc.equals(methodID.getDescriptor().getDescriptor())).findFirst();

            if(methodNodeOptional.isPresent()) {
                earliestDeclaringClass = classNode;
                earliestDefinition = methodNodeOptional.get();
            }

            if(methodID.getCallType() == MethodID.CallType.SPECIAL){
                break;
            }

            if(classNode.interfaces != null){
                interfacesToCheck.addAll(classNode.interfaces);
            }

            if(classNode.superName == null) {
                break;
            }

            classNode = getClassNode(classNode.superName);
        }

        //Find all implemented interfaces
        Set<String> implementedInterfaces = new HashSet<>();
        Set<String> toCheck = new HashSet<>(interfacesToCheck);
        while(!toCheck.isEmpty()) {
            Set<String> newToCheck = new HashSet<>();
            for(String interfaceName : toCheck) {
                ClassNode interfaceNode = getClassNode(interfaceName);
                if(interfaceNode.interfaces != null) {
                    newToCheck.addAll(interfaceNode.interfaces);
                }
            }
            implementedInterfaces.addAll(toCheck);
            toCheck = newToCheck;
            toCheck.removeAll(implementedInterfaces);
        }

        //Check interfaces
        for(String interfaceName : implementedInterfaces) {
            ClassNode interfaceNode = getClassNode(interfaceName);
            Optional<MethodNode> methodNodeOptional = interfaceNode.methods.stream().filter(m -> m.name.equals(methodID.getName()) && m.desc.equals(methodID.getDescriptor().getDescriptor())).findFirst();

            if(methodNodeOptional.isPresent()) {
                earliestDeclaringClass = interfaceNode;
                earliestDefinition = methodNodeOptional.get();
                break;
            }
        }

        if(earliestDeclaringClass == null) {
            return "No declaration found";
        }

        boolean isStatic = ASMUtil.isStatic(earliestDefinition);
        if(isStatic){
            isInterface = false;
        }
        boolean isVirtual = !isStatic && !isInterface;

        if(isStatic && methodID.getCallType() != MethodID.CallType.STATIC) {
            return "Static method is not called with INVOKESTATIC";
        }else if(isInterface && methodID.getCallType() != MethodID.CallType.INTERFACE) {
            return "Interface method is not called with INVOKEINTERFACE";
        }else if(isVirtual && (methodID.getCallType() != MethodID.CallType.VIRTUAL && methodID.getCallType() != MethodID.CallType.SPECIAL)) {
            return "Virtual method is not called with INVOKEVIRTUAL or INVOKESPECIAL";
        }

        return null;
    }

    private ClassNode getClassNode(String className) {
        className = className.replace('.', '/');

        ClassNode classNode = cachedClasses.get(className);

        if(classNode == null){

            if(LOAD_FROM_MIXIN_OUT) {
                classNode = loadClassNodeFromMixinOut(className);
            }

            if(classNode == null){
                System.err.println("Couldn't find class " + className + " in .mixin.out");
                classNode = loadClassNodeFromClassPath(className);
                plugin.postApply(className.replace('/', '.'), classNode, null, null);
            }

            cachedClasses.put(className, classNode);
        }

        return classNode;
    }

    private ClassNode loadClassNodeFromClassPath(String className) {
        byte[] bytes;

        InputStream is;

        is = ClassLoader.getSystemResourceAsStream(className + ".class");

        if (is == null) {
            throw new RuntimeException("Could not find class " + className);
        }

        ClassNode classNode = new ClassNode();
        try {
            ClassReader classReader = new ClassReader(is);
            classReader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new RuntimeException("Could not read class " + className, e);
        }

        cachedClasses.put(className, classNode);

        return classNode;
    }

    private ClassNode loadClassNodeFromMixinOut(String className){
        try {
            InputStream is = Files.newInputStream(assumedMixinOut.resolve(className + ".class"));

            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(is);
            classReader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            return classNode;
        }catch (IOException e){
            return null;
        }
    }

    private void verify(ClassNode classNode) {
        ClassWriter verifyWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(verifyWriter);

        CheckClassAdapter.verify(new ClassReader(verifyWriter.toByteArray()), false, new PrintWriter(System.out));
    }

    private static IMixinTransformer transformer;

    private IMixinTransformer getMixinTransformer(){
        if(transformer == null){
            makeTransformer();
        }

        return transformer;
    }

    private void makeTransformer() {
        MixinBootstrap.init();
    }
}
