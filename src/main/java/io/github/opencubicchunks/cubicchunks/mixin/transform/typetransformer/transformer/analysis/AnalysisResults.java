package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis;

import java.io.PrintStream;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.ASMUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Holds the results of the analysis of a single method.
 * @param methodNode The method these results are for
 * If the method is not static, this includes information for the 'this' parameter
 */
public record AnalysisResults(MethodNode methodNode, Frame<TransformTrackingValue>[] frames) {
    /**
     * Prints information about the analysis results.
     * @param out Where to print the information.
     * @param printFrames Whether information should be printed for every frame.
     */
    public void print(PrintStream out, boolean printFrames) {
        out.println("Analysis Results for " + methodNode.name);
        out.println("  Arg Types:");
        for (TransformSubtype argType : this.getArgTypes()) {
            out.println("    " + argType);
        }

        if (printFrames) {
            out.println("  Frames:");
            for (int i = 0; i < frames.length; i++) {
                Frame<TransformTrackingValue> frame = frames[i];
                if (frame != null) {
                    out.println("    Frame " + i);
                    out.println("      Stack:");
                    for (int j = 0; j < frames[i].getStackSize(); j++) {
                        out.println("        " + frames[i].getStack(j));
                    }
                    out.println("      Locals:");
                    for (int j = 0; j < frames[i].getLocals(); j++) {
                        out.println("        " + frames[i].getLocal(j));
                    }
                }
            }
        }
    }

    public TransformSubtype[] getArgTypes() {
        int offset = ASMUtil.isStatic(methodNode) ? 0 : 1;
        Type[] args = Type.getArgumentTypes(methodNode.desc);
        TransformSubtype[] argTypes = new TransformSubtype[args.length + offset];

        int idx = 0;
        for (int i = 0; idx < argTypes.length; i += frames[0].getLocal(i).getSize()) {
            argTypes[idx++] = frames[0].getLocal(i).getTransform();
        }

        return argTypes;
    }

    /**
     * Creates the new description using the transformed argument types
     * @return A descriptor as a string
     */
    public String getNewDesc() {
        TransformSubtype[] argTypes = getArgTypes();
        TransformSubtype[] types = argTypes;
        if (!ASMUtil.isStatic(methodNode)) {
            //If the method is not static then the first element of this.types is the 'this' argument.
            //This argument is not shown is method descriptors, so we must exclude it
            types = new TransformSubtype[types.length - 1];
            System.arraycopy(argTypes, 1, types, 0, types.length);
        }

        return MethodParameterInfo.getNewDesc(TransformSubtype.createDefault(Type.getReturnType(methodNode.desc)), types, methodNode.desc);
    }
}
