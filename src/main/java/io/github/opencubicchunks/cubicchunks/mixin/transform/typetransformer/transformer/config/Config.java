package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingInterpreter;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformTrackingValue;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

public class Config {
    private final HierarchyTree hierarchy;
    private final Map<String, TransformType> types;
    private final AncestorHashMap<MethodID, List<MethodParameterInfo>> methodParameterInfo;
    private final Map<Type, ClassTransformInfo> classes;
    private final Map<Type, InvokerInfo> invokers;

    private TransformTrackingInterpreter interpreter;
    private Analyzer<TransformTrackingValue> analyzer;

    public Config(HierarchyTree hierarchy, Map<String, TransformType> transformTypeMap, AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo,
                  Map<Type, ClassTransformInfo> classes,
                  Map<Type, InvokerInfo> invokers) {
        this.types = transformTypeMap;
        this.methodParameterInfo = parameterInfo;
        this.hierarchy = hierarchy;
        this.classes = classes;
        this.invokers = invokers;

        TransformSubtype.init(this);
    }

    public void print(PrintStream out) {
        System.out.println("Hierarchy:");
        hierarchy.print(out);

        for (Map.Entry<String, TransformType> entry : types.entrySet()) {
            out.println(entry.getValue());
        }

        System.out.println("\nMethod Parameter Info:");

        for (Map.Entry<MethodID, List<MethodParameterInfo>> entry : methodParameterInfo.entrySet()) {
            for (MethodParameterInfo info : entry.getValue()) {
                out.println(info);
            }
        }
    }

    public HierarchyTree getHierarchy() {
        return hierarchy;
    }

    public Map<String, TransformType> getTypes() {
        return types;
    }

    public Map<MethodID, List<MethodParameterInfo>> getMethodParameterInfo() {
        return methodParameterInfo;
    }

    public TransformTrackingInterpreter getInterpreter() {
        if (interpreter == null) {
            interpreter = new TransformTrackingInterpreter(Opcodes.ASM9, this);
        }

        return interpreter;
    }

    public Analyzer<TransformTrackingValue> getAnalyzer() {
        if (analyzer == null) {
            makeAnalyzer();
        }

        return analyzer;
    }

    public void makeAnalyzer() {
        analyzer = new Analyzer<>(getInterpreter()) {
            @Override protected Frame<TransformTrackingValue> newFrame(int numLocals, int numStack) {
                return new DuplicatorFrame<>(numLocals, numStack);
            }

            @Override protected Frame<TransformTrackingValue> newFrame(Frame<? extends TransformTrackingValue> frame) {
                return new DuplicatorFrame<>(frame);
            }
        };
    }

    public Map<Type, ClassTransformInfo> getClasses() {
        return classes;
    }

    public Map<Type, InvokerInfo> getInvokers() {
        return invokers;
    }

    /**
     * Makes DUP instructions (DUP, DUP_X1, SWAP, etc...) actually make duplicates for all values e.g Old: [Value@1] -> [Value@1, Value@2 (copyOperation(Value@1))] New: [Value@1] -> [Value@2
     * (copyOperation(Value@1)), Value@3 (copyOperation(Value@1))]
     *
     * @param <T>
     */
    private static final class DuplicatorFrame<T extends Value> extends Frame<T> {
        DuplicatorFrame(int numLocals, int maxStack) {
            super(numLocals, maxStack);
        }

        DuplicatorFrame(Frame<? extends T> frame) {
            super(frame);
        }

        @Override public void execute(AbstractInsnNode insn, Interpreter<T> interpreter) throws AnalyzerException {
            T value1, value2, value3, value4;

            switch (insn.getOpcode()) {
                case Opcodes.DUP -> {
                    value1 = pop();
                    if (value1.getSize() != 1) {
                        throw new AnalyzerException(insn, "DUP expects a value of size 1");
                    }
                    push(interpreter.copyOperation(insn, value1));
                    push(interpreter.copyOperation(insn, value1));
                }
                case Opcodes.DUP_X1 -> {
                    value1 = pop();
                    value2 = pop();
                    if (value1.getSize() != 1 || value2.getSize() != 1) {
                        throw new AnalyzerException(insn, "DUP_X1 expects values of size 1");
                    }
                    push(interpreter.copyOperation(insn, value1));
                    push(interpreter.copyOperation(insn, value2));
                    push(interpreter.copyOperation(insn, value1));
                }
                case Opcodes.DUP_X2 -> {
                    value1 = pop();
                    value2 = pop();
                    if (value1.getSize() == 1 && value2.getSize() == 2) {
                        push(interpreter.copyOperation(insn, value1));
                        push(interpreter.copyOperation(insn, value2));
                        push(interpreter.copyOperation(insn, value1));
                    } else {
                        value3 = pop();
                        if (value1.getSize() == 1 && value2.getSize() == 1 && value3.getSize() == 1) {
                            push(interpreter.copyOperation(insn, value1));
                            push(interpreter.copyOperation(insn, value3));
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                        } else {
                            throw new AnalyzerException(insn, "DUP_X2 expects values of size (1, 2) or (1, 1, 1)");
                        }
                    }
                }
                case Opcodes.DUP2 -> {
                    value1 = pop();
                    if (value1.getSize() == 2) {
                        push(interpreter.copyOperation(insn, value1));
                        push(interpreter.copyOperation(insn, value1));
                    } else {
                        value2 = pop();
                        if (value1.getSize() == 1 && value2.getSize() == 1) {
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                        } else {
                            throw new AnalyzerException(insn, "DUP2 expects values of size (1, 1) or (2)");
                        }
                    }
                }
                case Opcodes.DUP2_X1 -> {
                    value1 = pop();
                    value2 = pop();
                    if (value1.getSize() == 2 && value2.getSize() == 1) {
                        push(interpreter.copyOperation(insn, value1));
                        push(interpreter.copyOperation(insn, value2));
                        push(interpreter.copyOperation(insn, value1));
                    } else {
                        value3 = pop();
                        if (value1.getSize() == 1 && value2.getSize() == 1 && value3.getSize() == 1) {
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                            push(interpreter.copyOperation(insn, value3));
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                        } else {
                            throw new AnalyzerException(insn, "DUP2_X1 expects values of size (1, 1, 1) or (2, 1)");
                        }
                    }
                }
                case Opcodes.DUP2_X2 -> {
                    value1 = pop();
                    value2 = pop();
                    if (value1.getSize() == 2 && value2.getSize() == 2) {
                        push(interpreter.copyOperation(insn, value1));
                        push(interpreter.copyOperation(insn, value2));
                        push(interpreter.copyOperation(insn, value1));
                    } else {
                        value3 = pop();
                        if (value1.getSize() == 1 && value2.getSize() == 1 && value3.getSize() == 2) {
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                            push(interpreter.copyOperation(insn, value3));
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                        } else if (value1.getSize() == 2 && value2.getSize() == 1 && value3.getSize() == 1) {
                            push(interpreter.copyOperation(insn, value1));
                            push(interpreter.copyOperation(insn, value3));
                            push(interpreter.copyOperation(insn, value2));
                            push(interpreter.copyOperation(insn, value1));
                        } else {
                            value4 = pop();
                            if (value1.getSize() == 1 && value2.getSize() == 1 && value3.getSize() == 1 && value4.getSize() == 1) {
                                push(interpreter.copyOperation(insn, value2));
                                push(interpreter.copyOperation(insn, value1));
                                push(interpreter.copyOperation(insn, value4));
                                push(interpreter.copyOperation(insn, value3));
                                push(interpreter.copyOperation(insn, value2));
                                push(interpreter.copyOperation(insn, value1));
                            } else {
                                throw new AnalyzerException(insn, "DUP2_X2 expects values of size (1, 1, 1, 1), (1, 1, 2), (2, 1, 1) or (2, 2)");
                            }
                        }
                    }
                }
                case Opcodes.SWAP -> {
                    value1 = pop();
                    value2 = pop();

                    if (value1.getSize() == 2 || value2.getSize() == 2) {
                        throw new AnalyzerException(insn, "SWAP expects values of size 1");
                    }

                    push(interpreter.copyOperation(insn, value1));
                    push(interpreter.copyOperation(insn, value2));
                }
                default -> super.execute(insn, interpreter);
            }
        }


    }
}
