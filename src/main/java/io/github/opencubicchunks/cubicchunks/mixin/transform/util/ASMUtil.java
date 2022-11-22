package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;

public class ASMUtil {
    public static int argumentSize(String desc, boolean isStatic) {
        Type[] argTypes = Type.getArgumentTypes(desc);

        int size = 0;
        if (!isStatic) {
            size++;
        }

        for (Type subType : argTypes) {
            size += subType.getSize();
        }

        return size;
    }

    public static boolean isStatic(MethodNode methodNode) {
        return (methodNode.access & ACC_STATIC) != 0;
    }

    public static int argumentCount(String desc, boolean isStatic) {
        Type[] argTypes = Type.getArgumentTypes(desc);

        int size = argTypes.length;
        if (!isStatic) {
            size++;
        }

        return size;
    }

    public static <T> void varIndicesToArgIndices(T[] varArr, T[] argArr, String desc, boolean isStatic) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        int staticOffset = isStatic ? 0 : 1;
        if (argArr.length != argTypes.length + staticOffset) {
            throw new IllegalArgumentException("argArr.length != argTypes.length");
        }

        int varIndex = 0;
        int argIndex = 0;

        if (!isStatic) {
            argArr[0] = varArr[0];
            varIndex++;
            argIndex++;
        }

        for (Type subType : argTypes) {
            argArr[argIndex] = varArr[varIndex];
            varIndex += subType.getSize();
            argIndex++;
        }
    }

    public static void jumpIfCmp(InsnList list, Type type, boolean equal, LabelNode label) {
        switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> list.add(new JumpInsnNode(equal ? IF_ICMPEQ : IF_ICMPNE, label));
            case Type.ARRAY, Type.OBJECT -> list.add(new JumpInsnNode(equal ? IF_ACMPEQ : IF_ACMPNE, label));
            default -> {
                list.add(new InsnNode(switch (type.getSort()) {
                    case Type.FLOAT -> FCMPL;
                    case Type.LONG -> LCMP;
                    case Type.DOUBLE -> DCMPL;
                    default -> throw new IllegalArgumentException("Invalid type: " + type);
                }));
                list.add(new JumpInsnNode(equal ? IFEQ : IFNE, label));
            }
        }
    }

    public static String onlyClassName(String name) {
        name = name.replace('/', '.');
        int index = name.lastIndexOf('.');
        if (index == -1) {
            return name;
        }
        return name.substring(index + 1);
    }

    public static <T extends Value> T getTop(Frame<T> frame) {
        return frame.getStack(frame.getStackSize() - 1);
    }

    public static Type getType(int opcode) {
        return switch (opcode) {
            case ALOAD, ASTORE -> Type.getType("Ljava/lang/Object;");
            case DLOAD, DSTORE -> Type.DOUBLE_TYPE;
            case FLOAD, FSTORE -> Type.FLOAT_TYPE;
            case ILOAD, ISTORE -> Type.INT_TYPE;
            case LLOAD, LSTORE -> Type.LONG_TYPE;
            default -> {
                throw new UnsupportedOperationException("Opcode " + opcode + " is not supported yet!");
            }
        };
    }

    public static int stackConsumed(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode methodCall) {
            return argumentCount(methodCall.desc, methodCall.getOpcode() == INVOKESTATIC);
        } else if (insn instanceof InvokeDynamicInsnNode dynamicCall) {
            return argumentCount(dynamicCall.desc, true);
        } else {
            return switch (insn.getOpcode()) {
                case ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, ANEWARRAY, ARETURN, ARRAYLENGTH, ATHROW, CHECKCAST, D2F, D2I, D2L, DNEG, DRETURN, F2D, F2I, F2L, FNEG, FRETURN, GETFIELD,
                    TABLESWITCH, PUTSTATIC, POP2, L2I, L2F, LNEG, LRETURN, MONITORENTER, MONITOREXIT, POP, I2B, I2C, I2D, I2F, I2L, I2S, INEG, IRETURN, L2D, DUP -> 1;
                case AALOAD, BALOAD, CALOAD, DADD, DALOAD, DCMPG, DCMPL, DDIV, DMUL, DREM, DSUB, FADD, FALOAD, FCMPG, FCMPL, FDIV, FMUL, FREM, FSUB, SALOAD, PUTFIELD, LSHR, LSUB, LALOAD,
                    LCMP, LDIV, LMUL, LOR, LREM, LSHL, LUSHR, LXOR, LADD, IADD, IALOAD, IAND, IDIV, IMUL, IOR, IREM, ISHL, ISHR, ISUB, IUSHR, IXOR -> 2;
                case AASTORE, BASTORE, CASTORE, DASTORE, FASTORE, SASTORE, LASTORE, IASTORE -> 3;
                default -> 0;
            };
        }
    }

    public static boolean isConstant(AbstractInsnNode insn) {
        if (insn instanceof LdcInsnNode) {
            return true;
        } else if (insn instanceof IntInsnNode && insn.getOpcode() != NEWARRAY) {
            return true;
        }

        int opcode = insn.getOpcode();
        return opcode == ICONST_M1 || opcode == ICONST_0 || opcode == ICONST_1 || opcode == ICONST_2 || opcode == ICONST_3 || opcode == ICONST_4 || opcode == ICONST_5 || opcode == LCONST_0
            || opcode == LCONST_1 || opcode == FCONST_0 || opcode == FCONST_1 || opcode == FCONST_2 || opcode == DCONST_0 || opcode == DCONST_1;
    }

    public static int getCompare(Type subType) {
        if (subType == Type.FLOAT_TYPE) {
            return FCMPL;
        } else if (subType == Type.DOUBLE_TYPE) {
            return DCMPL;
        } else if (subType == Type.LONG_TYPE) {
            return LCMP;
        } else {
            throw new IllegalArgumentException("Type " + subType + " is not allowed!");
        }
    }

    public static Object getConstant(AbstractInsnNode insn) {
        if (!isConstant(insn)) {
            throw new IllegalArgumentException("Not a constant instruction!");
        }

        if (insn instanceof LdcInsnNode cst) {
            return cst.cst;
        } else if (insn instanceof IntInsnNode cst) {
            return cst.operand;
        }

        int opcode = insn.getOpcode();

        return switch (opcode) {
            case ICONST_M1 -> -1;
            case ICONST_0 -> 0;
            case ICONST_1 -> 1;
            case ICONST_2 -> 2;
            case ICONST_3 -> 3;
            case ICONST_4 -> 4;
            case ICONST_5 -> 5;
            case LCONST_0 -> 0L;
            case LCONST_1 -> 1L;
            case FCONST_0 -> 0.0f;
            case FCONST_1 -> 1.0f;
            case FCONST_2 -> 2.0f;
            case DCONST_0 -> 0.0;
            case DCONST_1 -> 1.0;
            default -> {
                throw new UnsupportedOperationException("Opcode " + opcode + " is not supported!");
            }
        };
    }

    public static MethodNode copy(MethodNode original) {
        ClassNode classNode = new ClassNode();
        original.accept(classNode);
        return classNode.methods.get(0);
    }

    public static void changeFieldType(ClassNode target, FieldID fieldID, Type newType, Function<MethodNode, InsnList> postLoad) {
        String owner = target.name;
        String name = fieldID.name();
        String desc = fieldID.desc().getDescriptor();

        FieldNode field = target.fields.stream().filter(f -> f.name.equals(name) && f.desc.equals(desc)).findFirst().orElse(null);
        if (field == null) {
            throw new IllegalArgumentException("Field " + name + " not found!");
        }

        field.desc = newType.getDescriptor();

        for (MethodNode method : target.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode fieldInsn) {
                    if (fieldInsn.owner.equals(owner) && fieldInsn.name.equals(name) && fieldInsn.desc.equals(desc)) {
                        fieldInsn.desc = newType.getDescriptor();

                        if (fieldInsn.getOpcode() == GETFIELD || fieldInsn.getOpcode() == GETSTATIC) {
                            method.instructions.insert(insn, postLoad.apply(method));
                        }
                    }
                }
            }
        }

    }

    public static int getDimensions(Type t) {
        if (t.getSort() == Type.ARRAY) {
            return t.getDimensions();
        } else {
            return 0;
        }
    }

    /**
     * Converts an instruction into a human-readable string. This is not made to be fast, but it is meant to be used for debugging.
     *
     * @param instruction The instruction.
     *
     * @return The string.
     */
    public static String textify(AbstractInsnNode instruction) {
        StringBuilder builder = new StringBuilder();
        textify(instruction, builder);
        return builder.toString();
    }

    private static void textify(AbstractInsnNode instruction, StringBuilder builder) {
        if (instruction instanceof LabelNode labelNode) {
            builder.append(labelNode.getLabel().toString()).append(": ");
        } else if (instruction instanceof LineNumberNode lineNumberNode) {
            builder.append("Line ").append(lineNumberNode.line).append(": ");
        } else if (instruction instanceof FrameNode frameNode) {
            builder.append("Frame Node");
        } else {
            builder.append(opcodeName(instruction.getOpcode()).toLowerCase()).append(" ");
            if (instruction instanceof FieldInsnNode fieldInsnNode) {
                builder.append(fieldInsnNode.owner).append(".").append(fieldInsnNode.name).append(" ").append(fieldInsnNode.desc);
            } else if (instruction instanceof MethodInsnNode methodInsnNode) {
                builder.append(methodInsnNode.owner).append(".").append(methodInsnNode.name).append(" ").append(methodInsnNode.desc);
            } else if (instruction instanceof TableSwitchInsnNode tableSwitchInsnNode) {
                builder.append("TableSwitchInsnNode");
            } else if (instruction instanceof LookupSwitchInsnNode lookupSwitchInsnNode) {
                builder.append("LookupSwitchInsnNode");
            } else if (instruction instanceof IntInsnNode intInsnNode) {
                builder.append(intInsnNode.operand);
            } else if (instruction instanceof LdcInsnNode ldcInsnNode) {
                builder.append(ldcInsnNode.cst);
            }
        }
    }

    /**
     * Get the name of a JVM Opcode
     *
     * @param opcode The opcode as an integer
     *
     * @return The mnemonic of the opcode
     */
    public static String opcodeName(int opcode) {
        return switch (opcode) {
            case NOP -> "nop";
            case ACONST_NULL -> "aconst_null";
            case ICONST_M1 -> "iconst_m1";
            case ICONST_0 -> "iconst_0";
            case ICONST_1 -> "iconst_1";
            case ICONST_2 -> "iconst_2";
            case ICONST_3 -> "iconst_3";
            case ICONST_4 -> "iconst_4";
            case ICONST_5 -> "iconst_5";
            case LCONST_0 -> "lconst_0";
            case LCONST_1 -> "lconst_1";
            case FCONST_0 -> "fconst_0";
            case FCONST_1 -> "fconst_1";
            case FCONST_2 -> "fconst_2";
            case DCONST_0 -> "dconst_0";
            case DCONST_1 -> "dconst_1";
            case BIPUSH -> "bipush";
            case SIPUSH -> "sipush";
            case LDC -> "ldc";
            case ILOAD -> "iload";
            case LLOAD -> "lload";
            case FLOAD -> "fload";
            case DLOAD -> "dload";
            case ALOAD -> "aload";
            case IALOAD -> "iaload";
            case LALOAD -> "laload";
            case FALOAD -> "faload";
            case DALOAD -> "daload";
            case AALOAD -> "aaload";
            case BALOAD -> "baload";
            case CALOAD -> "caload";
            case SALOAD -> "saload";
            case ISTORE -> "istore";
            case LSTORE -> "lstore";
            case FSTORE -> "fstore";
            case DSTORE -> "dstore";
            case ASTORE -> "astore";
            case IASTORE -> "iastore";
            case LASTORE -> "lastore";
            case FASTORE -> "fastore";
            case DASTORE -> "dastore";
            case AASTORE -> "aastore";
            case BASTORE -> "bastore";
            case CASTORE -> "castore";
            case SASTORE -> "sastore";
            case POP -> "pop";
            case POP2 -> "pop2";
            case DUP -> "dup";
            case DUP_X1 -> "dup_x1";
            case DUP_X2 -> "dup_x2";
            case DUP2 -> "dup2";
            case DUP2_X1 -> "dup2_x1";
            case DUP2_X2 -> "dup2_x2";
            case SWAP -> "swap";
            case IADD -> "iadd";
            case LADD -> "ladd";
            case FADD -> "fadd";
            case DADD -> "dadd";
            case ISUB -> "isub";
            case LSUB -> "lsub";
            case FSUB -> "fsub";
            case DSUB -> "dsub";
            case IMUL -> "imul";
            case LMUL -> "lmul";
            case FMUL -> "fmul";
            case DMUL -> "dmul";
            case IDIV -> "idiv";
            case LDIV -> "ldiv";
            case FDIV -> "fdiv";
            case DDIV -> "ddiv";
            case IREM -> "irem";
            case LREM -> "lrem";
            case FREM -> "frem";
            case DREM -> "drem";
            case INEG -> "ineg";
            case LNEG -> "lneg";
            case FNEG -> "fneg";
            case DNEG -> "dneg";
            case ISHL -> "ishl";
            case LSHL -> "lshl";
            case ISHR -> "ishr";
            case LSHR -> "lshr";
            case IUSHR -> "iushr";
            case LUSHR -> "lushr";
            case IAND -> "iand";
            case LAND -> "land";
            case IOR -> "ior";
            case LOR -> "lor";
            case IXOR -> "ixor";
            case LXOR -> "lxor";
            case IINC -> "iinc";
            case I2L -> "i2l";
            case I2F -> "i2f";
            case I2D -> "i2d";
            case L2I -> "l2i";
            case L2F -> "l2f";
            case L2D -> "l2d";
            case F2I -> "f2i";
            case F2L -> "f2l";
            case F2D -> "f2d";
            case D2I -> "d2i";
            case D2L -> "d2l";
            case D2F -> "d2f";
            case I2B -> "i2b";
            case I2C -> "i2c";
            case I2S -> "i2s";
            case LCMP -> "lcmp";
            case FCMPL -> "fcmpl";
            case FCMPG -> "fcmpg";
            case DCMPL -> "dcmpl";
            case DCMPG -> "dcmpg";
            case IFEQ -> "ifeq";
            case IFNE -> "ifne";
            case IFLT -> "iflt";
            case IFGE -> "ifge";
            case IFGT -> "ifgt";
            case IFLE -> "ifle";
            case IF_ICMPEQ -> "if_icmpeq";
            case IF_ICMPNE -> "if_icmpne";
            case IF_ICMPLT -> "if_icmplt";
            case IF_ICMPGE -> "if_icmpge";
            case IF_ICMPGT -> "if_icmpgt";
            case IF_ICMPLE -> "if_icmple";
            case IF_ACMPEQ -> "if_acmpeq";
            case IF_ACMPNE -> "if_acmpne";
            case GOTO -> "goto";
            case JSR -> "jsr";
            case RET -> "ret";
            case TABLESWITCH -> "tableswitch";
            case LOOKUPSWITCH -> "lookupswitch";
            case IRETURN -> "ireturn";
            case LRETURN -> "lreturn";
            case FRETURN -> "freturn";
            case DRETURN -> "dreturn";
            case ARETURN -> "areturn";
            case RETURN -> "return";
            case GETSTATIC -> "getstatic";
            case PUTSTATIC -> "putstatic";
            case GETFIELD -> "getfield";
            case PUTFIELD -> "putfield";
            case INVOKEVIRTUAL -> "invokevirtual";
            case INVOKESPECIAL -> "invokespecial";
            case INVOKESTATIC -> "invokestatic";
            case INVOKEINTERFACE -> "invokeinterface";
            case INVOKEDYNAMIC -> "invokedynamic";
            case NEW -> "new";
            case NEWARRAY -> "newarray";
            case ANEWARRAY -> "anewarray";
            case ARRAYLENGTH -> "arraylength";
            case ATHROW -> "athrow";
            case CHECKCAST -> "checkcast";
            case INSTANCEOF -> "instanceof";
            case MONITORENTER -> "monitorenter";
            case MONITOREXIT -> "monitorexit";
            default -> "UNKNOWN (" + opcode + ")";
        };
    }

    /**
     * Returns the amount of values that are pushed onto the stack by the given opcode. This will usually be 0 or 1 but some DUP and SWAPs can have higher values (up to six). If you know
     * that none of the instructions are DUP_X2, DUP2, DUP2_X1, DUP2_X2, POP2 you can use the {@link #numValuesReturnedBasic(AbstractInsnNode)} method instead which does not require the
     * frame.
     *
     * @param frame
     * @param insnNode
     *
     * @return
     */
    public static int numValuesReturned(Frame<?> frame, AbstractInsnNode insnNode) {
        //Manage DUP and SWAPs
        int opcode = insnNode.getOpcode();
        int top = frame.getStackSize();
        if (opcode == DUP) {
            return 2;
        } else if (opcode == DUP_X1) {
            return 3;
        } else if (opcode == DUP_X2) {
            Value value2 = frame.getStack(top - 2);
            if (value2.getSize() == 2) {
                return 3;
            } else {
                return 4;
            }
        } else if (opcode == DUP2) {
            Value value1 = frame.getStack(top - 1);
            if (value1.getSize() == 2) {
                return 2;
            } else {
                return 4;
            }
        } else if (opcode == DUP2_X1) {
            Value value1 = frame.getStack(top - 1);
            if (value1.getSize() == 2) {
                return 3;
            } else {
                return 5;
            }
        } else if (opcode == DUP2_X2) {
            return handleDup2X2(frame, top);
        } else if (opcode == SWAP) {
            return 2;
        } else if (opcode == POP2) {
            Value value1 = frame.getStack(top - 1);
            if (value1.getSize() == 2) {
                return 1;
            } else {
                return 2;
            }
        }

        //The remaining do not need the frame context
        return numValuesReturnedBasic(insnNode);
    }

    private static int handleDup2X2(Frame<?> frame, int top) {
    /*
     Here are the forms of the instruction:
     The rows are the forms, the columns are the value nums from https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html and the number is the computational type of the
     argument. '-' Represents a value that is not used. On the right is the resulting stack and the amount of values that are pushed.

           | 1 | 2 | 3 | 4 |
     Form 1| 1 | 1 | 1 | 1 | -> [2, 1, 4, 3, 2, 1] (6)
     Form 2| 2 | 1 | 1 | - | -> [1, 3, 2, 1] (4)
     Form 3| 1 | 1 | 2 | - | -> [2, 1, 3, 2, 1] (5)
     Form 4| 2 | 2 | - | - | -> [1, 2, 1] (3)
     */

        Value value1 = frame.getStack(top - 1);
        if (value1.getSize() == 2) {
            Value value2 = frame.getStack(top - 2);
            if (value2.getSize() == 2) {
                return 3; //Form 4
            } else {
                return 4; //Form 2
            }
        } else {
            Value value3 = frame.getStack(top - 3);
            if (value3.getSize() == 2) {
                return 5; //Form 3
            } else {
                return 6; //Form 1
            }
        }
    }


    private static int numValuesReturnedBasic(AbstractInsnNode insnNode) {
        if (insnNode.getOpcode() == -1) {
            return 0;
        }

        if (numValuesReturnedNeedsFrame(insnNode)) {
            throw new IllegalArgumentException("The frame is required for the following opcodes: " + opcodeName(insnNode.getOpcode()));
        }

        return numValuesReturnUnsafe(insnNode);
    }

    private static int numValuesReturnUnsafe(AbstractInsnNode insnNode) {
        return switch (insnNode.getOpcode()) {
            case AALOAD, ACONST_NULL, ALOAD, ANEWARRAY, ARRAYLENGTH, BALOAD, BIPUSH, CALOAD, CHECKCAST, D2F, D2I, D2L, DADD, DALOAD, DCMPG, DCMPL, DCONST_0, DCONST_1, DDIV, DLOAD, DMUL,
                DNEG, DREM, DSUB, F2D, F2I, F2L, FADD, FALOAD, FCMPG, FCMPL, FCONST_0, FCONST_1, FCONST_2, FDIV, FLOAD, FMUL, FNEG, FREM, FSUB, GETFIELD, GETSTATIC, I2B, I2C, I2D, I2F,
                I2L, I2S, IADD, IALOAD, IAND, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, ICONST_M1, IDIV, ILOAD, IMUL, INEG, INSTANCEOF, IOR, IREM, ISHL, ISHR, ISUB,
                IUSHR, IXOR, JSR, L2D, L2F, L2I, LADD, LALOAD, LAND, LCMP, LCONST_0, LCONST_1, LDC, LDIV, LLOAD, LMUL, LNEG, LOR, LREM, LSHL, LSHR, LSUB, LUSHR, LXOR, MULTIANEWARRAY, NEW,
                NEWARRAY, SALOAD, SIPUSH -> 1;
            case AASTORE, ARETURN, ASTORE, ATHROW, BASTORE, CASTORE, DRETURN, DSTORE, FASTORE, FRETURN, FSTORE, GOTO, IASTORE, IF_ACMPEQ, IF_ACMPNE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPGE,
                IF_ICMPLE, IF_ICMPGT, IF_ICMPLT, IFEQ, IFNE, IFGE, IFLE, IFGT, IFLT, IFNONNULL, IFNULL, IINC, IRETURN, ISTORE, LASTORE, LOOKUPSWITCH, TABLESWITCH, LRETURN, LSTORE,
                MONITORENTER, MONITOREXIT, NOP, POP, PUTFIELD, PUTSTATIC, RET, RETURN, SASTORE -> 0;
            case DUP, SWAP -> 2;
            case DUP_X1 -> 3;
            default -> {
                if (insnNode instanceof MethodInsnNode methodCall) {
                    yield methodPush(methodCall.desc);
                } else if (insnNode instanceof InvokeDynamicInsnNode methodCall) {
                    yield methodPush(methodCall.desc);
                } else {
                    throw new IllegalArgumentException("Unsupported instruction: " + insnNode.getClass().getSimpleName());
                }
            }
        };
    }

    private static boolean numValuesReturnedNeedsFrame(AbstractInsnNode insnNode) {
        int op = insnNode.getOpcode();
        return op == DUP_X2 || op == DUP2_X1 || op == DUP2_X2 || op == POP2;
    }

    private static int methodPush(String desc) {
        return Type.getReturnType(desc) == Type.VOID_TYPE ? 0 : 1;
    }

    public static String prettyPrintMethod(String name, String descriptor) {
        Type[] types = Type.getArgumentTypes(descriptor);
        Type returnType = Type.getReturnType(descriptor);

        StringBuilder sb = new StringBuilder();
        sb.append(onlyClassName(returnType.getClassName()));
        sb.append(" ");
        sb.append(name);
        sb.append("(");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(onlyClassName(types[i].getClassName()));
        }

        sb.append(")");

        return sb.toString();
    }

    @Nullable
    public static AbstractInsnNode getFirstMatch(InsnList instructions, Predicate<AbstractInsnNode> predicate) {
        return getFirstMatch(instructions.getFirst(), predicate);
    }

    /**
     * Finds the first instruction in a linked list of instructions that matches a provided condition.
     *
     * @param start The head of the linked list of instructions to search.
     * @param predicate The condition to match.
     *
     * @return The first instruction in the linked list that matches the condition. If {@code start} is null or no instruction is found, null is returned.
     */
    @Nullable
    public static AbstractInsnNode getFirstMatch(@Nullable final AbstractInsnNode start, final Predicate<AbstractInsnNode> predicate) {
        AbstractInsnNode current = start;
        while (current != null) {
            if (predicate.test(current)) {
                return current;
            }
            current = current.getNext();
        }
        return null;
    }

    public static ClassNode loadClassNode(Class<?> clazz) {
        return loadClassNode(clazz.getName().replace('.', '/') + ".class");
    }

    public static ClassNode loadClassNode(Type type) {
        return loadClassNode(type.getInternalName()+ ".class");
    }

    public static ClassNode loadClassNode(String path) {
        try {
            ClassNode classNode = new ClassNode();
            InputStream is = ClassLoader.getSystemResourceAsStream(path);
            if (is == null) {
                throw new IllegalArgumentException("Could not find class: " + path);
            }
            ClassReader classReader = new ClassReader(is);
            classReader.accept(classNode, 0);
            return classNode;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    public static MethodNode getMethod(ClassNode classNode, Predicate<MethodNode> condition) {
        //Ensure there us only one match

        List<MethodNode> matches = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            if (condition.test(method)) {
                matches.add(method);
            }
        }

        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected one match, but found " + matches.size());
        }

        return matches.get(0);
    }

    public static String getDescriptor(Method method) {
        Type[] types = new Type[method.getParameterCount()];

        for (int i = 0; i < types.length; i++) {
            types[i] = Type.getType(method.getParameterTypes()[i]);
        }

        return Type.getMethodDescriptor(Type.getType(method.getReturnType()), types);
    }

    public static AbstractInsnNode makeNew(Type type, int dimsAmount) {
        int totalDims = type.getDimensions();

        if (totalDims == 0) {
            return new TypeInsnNode(NEW, type.getInternalName());
        } else {
            return new MultiANewArrayInsnNode(type.getDescriptor(), dimsAmount);
        }
    }

    public static Type getArrayElement(Type type) {
        if (type.getSort() != Type.ARRAY) {
            throw new IllegalArgumentException("Type is not an array: " + type);
        }

        return Type.getType(type.getDescriptor().substring(1));
    }

    public static record MethodCondition(String name, @Nullable String desc) implements Predicate<MethodNode> {
        @Override
        public boolean test(MethodNode methodNode) {
            if (!methodNode.name.equals(name)) {
                return false;
            }

            if (desc != null && !methodNode.desc.equals(desc)) {
                return false;
            }

            return true;
        }

        public boolean testMethodID(MethodID id) {
            if (!id.getName().equals(name)) {
                return false;
            }

            if (desc != null && !id.getDescriptor().getDescriptor().equals(desc)) {
                return false;
            }

            return true;
        }
    }
}
