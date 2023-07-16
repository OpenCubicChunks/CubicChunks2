package io.github.opencubicchunks.cubicchunks.typetransformer;

import java.util.ArrayList;
import java.util.List;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.VariableAllocator;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.DerivedTransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.Config;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodReplacement;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TypeInfo;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

public class ConfigTest {
    public static final Config CONFIG = TypeInferenceTest.CONFIG;

    @Test
    public void verifyHierarchy() throws ClassNotFoundException {
        TypeInfo tree = CONFIG.getTypeInfo();

        //Verify known interfaces
        for (Type itf : tree.getKnownInterfaces()) {
            Class<?> clazz = loadClass(itf.getClassName());
            if (!clazz.isInterface()) {
                throw new AssertionError("Class " + clazz.getName() + " is not an interface");
            }
        }

        //Verify super info
        for (TypeInfo.Node node : tree.nodes()) {
            Class<?> clazz = loadClass(node.getValue().getClassName());

            //Check interfaces
            for (Type itf : node.getInterfaces()) {
                Class<?> itfClazz = loadClass(itf.getClassName());

                if (!itfClazz.isAssignableFrom(clazz)) {
                    throw new AssertionError("Class " + clazz.getName() + " does not implement " + itfClazz.getName());
                }

                if (!itfClazz.isInterface()) {
                    throw new AssertionError("Class " + itfClazz.getName() + " is not an interface");
                }
            }

            //Check super
            if (clazz.isInterface()) {
                continue;
            }

            if (node.getSuperclass() != null) {
                if (clazz.getSuperclass() == null) {
                    throw new AssertionError("Class " + clazz.getName() + " does not have a superclass");
                }

                if (!clazz.getSuperclass().getName().equals(node.getSuperclass().getValue().getClassName())) {
                    throw new AssertionError(
                        "Class " + clazz.getName() + " has a superclass " + clazz.getSuperclass().getName() + " but config gives " + node.getSuperclass().getValue().getClassName());
                }
            } else if (clazz.getSuperclass() != null) {
                throw new AssertionError("Class " + clazz.getName() + " has a superclass");
            }
        }
    }

    @Test
    public void verifyReplacements() {
        for (List<MethodParameterInfo> info : CONFIG.getMethodParameterInfo().values()) {
            for (MethodParameterInfo methodInfo : info) {
                if (methodInfo.getReplacement() != null) {
                    verifyReplacement(methodInfo);
                }
            }
        }
    }

    private void verifyReplacement(MethodParameterInfo methodInfo) {
        MethodReplacement replacement = methodInfo.getReplacement();

        Type returnType = methodInfo.getMethod().getDescriptor().getReturnType();
        Type[] argTypesWithoutThis = methodInfo.getMethod().getDescriptor().getArgumentTypes();

        Type[] argTypes;
        if (!methodInfo.getMethod().isStatic()) {
            argTypes = new Type[argTypesWithoutThis.length + 1];
            argTypes[0] = methodInfo.getMethod().getOwner();
            System.arraycopy(argTypesWithoutThis, 0, argTypes, 1, argTypesWithoutThis.length);
        } else {
            argTypes = argTypesWithoutThis;
        }

        Type[] returnTypes = methodInfo.getReturnType() == null ?
            new Type[] { Type.VOID_TYPE } :
            methodInfo.getReturnType().resultingTypes().toArray(new Type[0]);

        for (int i = 0; i < replacement.getParameterIndices().length; i++) {
            List<Type> types = getTypesFromIndices(methodInfo, argTypes, replacement.getParameterIndices()[i]);

            verifyFactory(replacement.getBytecodeFactories()[i], types, returnTypes[i]);
        }

        //Verify finalizer
        if (replacement.getFinalizer() == null) return;

        List<Type> types = getTypesFromIndices(methodInfo, argTypes, replacement.getFinalizerIndices());

        verifyFactory(replacement.getFinalizer(), types, Type.VOID_TYPE);
    }

    private List<Type> getTypesFromIndices(MethodParameterInfo methodInfo, Type[] argTypes, List<Integer>[] indices) {
        List<Type> types = new ArrayList<>();

        for (int j = 0; j < indices.length; j++) {
            DerivedTransformType derivedType = methodInfo.getParameterTypes()[j];
            List<Type> transformedTypes = derivedType.resultingTypes();

            for (int index : indices[j]) {
                if (transformedTypes.get(index).getSort() == Type.VOID) {
                    types.add(argTypes[j]);
                } else {
                    types.add(transformedTypes.get(index));
                }
            }
        }
        return types;
    }

    private void verifyFactory(BytecodeFactory bytecodeFactory, List<Type> argTypes, Type expectedReturnType) {
        String desc = Type.getMethodDescriptor(expectedReturnType, argTypes.toArray(new Type[0]));

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", desc, null, null);
        InsnList insns = method.instructions;

        //Load args onto stack
        int varIndex = 0;
        for (Type argType : argTypes) {
            insns.add(new VarInsnNode(argType.getOpcode(Opcodes.ILOAD), varIndex));
            varIndex += argType.getSize();
        }

        //We don't actually know these, so we make them big enough for anything reasonable
        method.maxLocals = 100;
        method.maxStack = 100;

        insns.add(bytecodeFactory.generate(VariableAllocator.makeBasicAllocator(varIndex)));

        //Return
        insns.add(new InsnNode(expectedReturnType.getOpcode(Opcodes.IRETURN)));

        //Verify!
        SimpleVerifier verifier = new SimpleVerifier();
        Analyzer<BasicValue> analyzer = new Analyzer<>(verifier);
        try {
            analyzer.analyze("no/such/Class", method);
        } catch (AnalyzerException e) {
            throw new AssertionError("Failed to verify bytecode factory", e);
        }
    }

    /**
     * Loads class without initializing it
     * @param name The full binary name of the class to load (separated by dots)
     */
    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Class " + name + " not found", e);
        }
    }
}
