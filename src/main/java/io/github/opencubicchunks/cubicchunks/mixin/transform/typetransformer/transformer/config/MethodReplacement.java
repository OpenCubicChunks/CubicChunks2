package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.ArrayList;
import java.util.List;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

public class MethodReplacement {
    private final BytecodeFactory[] bytecodeFactories;
    private final boolean changeParameters;
    private final List<Integer>[][] parameterIndexes;
    private final BytecodeFactory finalizer;
    private final List<Integer>[] finalizerIndices;

    public MethodReplacement(BytecodeFactory factory, TransformSubtype[] argTypes) {
        this.bytecodeFactories = new BytecodeFactory[] { factory };
        this.changeParameters = false;
        this.finalizer = null;
        this.finalizerIndices = null;

        //Compute default indices
        this.parameterIndexes = new List[1][argTypes.length];

        for (int i = 0; i < argTypes.length; i++) {
            List<Integer> indices = new ArrayList<>(1);
            parameterIndexes[0][i] = indices;

            if (argTypes[i].getTransformType() == null) {
                indices.add(0);
            } else {
                for (int j = 0; j < argTypes[i].resultingTypes().size(); j++) {
                    indices.add(j);
                }
            }
        }
    }

    public MethodReplacement(BytecodeFactory[] bytecodeFactories, List<Integer>[][] parameterIndexes) {
        this.bytecodeFactories = bytecodeFactories;
        this.parameterIndexes = parameterIndexes;
        this.changeParameters = true;
        this.finalizer = null;
        this.finalizerIndices = null;
    }

    public MethodReplacement(BytecodeFactory[] bytecodeFactories, List<Integer>[][] parameterIndexes, BytecodeFactory finalizer, List<Integer>[] finalizerIndices) {
        this.bytecodeFactories = bytecodeFactories;
        this.parameterIndexes = parameterIndexes;
        this.changeParameters = true;
        this.finalizer = finalizer;
        this.finalizerIndices = finalizerIndices;
    }

    public BytecodeFactory[] getBytecodeFactories() {
        return bytecodeFactories;
    }

    public boolean changeParameters() {
        return changeParameters;
    }

    public @Nullable List<Integer>[][] getParameterIndices() {
        return parameterIndexes;
    }

    public @Nullable BytecodeFactory getFinalizer() {
        return finalizer;
    }

    public @Nullable List<Integer>[] getFinalizerIndices() {
        return finalizerIndices;
    }
}
