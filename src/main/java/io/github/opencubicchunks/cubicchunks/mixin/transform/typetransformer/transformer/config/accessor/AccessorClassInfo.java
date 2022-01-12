package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.accessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.CustomClassAdder;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class AccessorClassInfo {
    private final Type mixinClass;
    private final Type targetClass;
    private final List<AccessorMethodInfo> methods;
    private final String newClassName;

    private final Set<String> usedNames = new HashSet<>();

    public AccessorClassInfo(Type mixinClass, Type targetClass, List<AccessorMethodInfo> methods) {
        this.mixinClass = mixinClass;
        this.targetClass = targetClass;
        this.methods = methods;

        String classBaseName = mixinClass.getClassName().substring(mixinClass.getClassName().lastIndexOf('.') + 1) + "_transformed_accessor";

        if(usedNames.contains(classBaseName)) {
            int i = 1;
            while(usedNames.contains(classBaseName + i)) {
                i++;
            }
            classBaseName += i;
        }

        this.newClassName = "io/github/opencubicchunks/cubicchunks/runtimegenerated/accessors/" + classBaseName;

        for(AccessorMethodInfo method : methods) {
            method.setAccessorClassInfo(this);
        }
    }

    public void addParameterInfoTo(AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo) {
        for (AccessorMethodInfo method : methods) {
            method.addParameterInfoTo(parameterInfo);
        }
    }

    public ClassNode load() {
        ClassNode classNode = new ClassNode();
        classNode.name = newClassName;
        classNode.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        classNode.version = 60;
        classNode.superName = "java/lang/Object";

        for (AccessorMethodInfo method : methods) {
            classNode.methods.add(method.generateAbstractSignature());
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        byte[] bytes = writer.toByteArray();

        String targetClassName = newClassName;
        String binaryName = targetClassName.replace('/', '.');

        Path savePath = Utils.getGameDir().resolve("longpos-out").resolve(targetClassName.replace('.', '/') + ".class");
        try {
            Files.createDirectories(savePath.getParent());
            Files.write(savePath, bytes);
            System.out.println("Saved " + targetClassName + " to " + savePath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        CustomClassAdder.addCustomClass(binaryName, bytes);

        return classNode;
    }

    public Type getMixinClass() {
        return mixinClass;
    }

    public Type getTargetClass() {
        return targetClass;
    }

    public List<AccessorMethodInfo> getMethods() {
        return methods;
    }

    public String getNewClassName() {
        return newClassName;
    }
}