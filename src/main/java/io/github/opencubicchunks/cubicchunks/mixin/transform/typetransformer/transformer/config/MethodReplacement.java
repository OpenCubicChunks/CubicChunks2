package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.List;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;

public class MethodReplacement {
    private final BytecodeFactory[] bytecodeFactories;
    private final boolean changeParameters;
    private final List<Integer>[][] parameterIndexes;
    private final BytecodeFactory finalizer;
    private final List<Integer>[] finalizerIndices;

    public MethodReplacement(BytecodeFactory factory){
        this.bytecodeFactories = new BytecodeFactory[]{factory};
        this.parameterIndexes = null;
        this.changeParameters = false;
        this.finalizer = null;
        this.finalizerIndices = null;
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

    public List<Integer>[][] getParameterIndexes() {
        return parameterIndexes;
    }

    public BytecodeFactory getFinalizer() {
        return finalizer;
    }

    public List<Integer>[] getFinalizerIndices() {
        return finalizerIndices;
    }
}
