package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import net.fabricmc.loader.api.MappingResolver;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class JSONBytecodeFactory implements BytecodeFactory {
    private static final String NAMESPACE = "intermediary";

    private static final String[] VAR_INSNS = {
        "ILOAD", "LLOAD", "FLOAD", "DLOAD", "ALOAD",
        "ISTORE", "LSTORE", "FSTORE", "DSTORE", "ASTORE"
    };

    private static final Type[] TYPES = {
        Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.DOUBLE_TYPE, Type.getType(Object.class),
        Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.DOUBLE_TYPE, Type.getType(Object.class)
    };

    private static final int[] VAR_OPCODES = {
        ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
        ISTORE, LSTORE, FSTORE, DSTORE, ASTORE
    };

    private final List<BiConsumer<InsnList, int[]>> instructionGenerators = new ArrayList<>();
    private final List<Type> varTypes = new ArrayList<>();

    public JSONBytecodeFactory(JsonArray data, MappingResolver mappings, Map<String, MethodID> methodIDMap) {
        //Find all variable names
        Map<String, Integer> varNames = new HashMap<>();

        for (JsonElement element : data) {
            if (element.isJsonPrimitive()) {
                String name = element.getAsString();
                for (int i = 0; i < VAR_INSNS.length; i++) {
                    String insnName = VAR_INSNS[i];
                    if (name.startsWith(insnName)) {
                        String namePart = name.substring(insnName.length() + 1);

                        if (!namePart.matches("\\{[0-9a-zA-Z_]+}")) {
                            throw new IllegalArgumentException("Variables instructions must be of the form 'OPCODE {NAME}'");
                        }

                        String actualName = namePart.substring(1, namePart.length() - 1);
                        Type t = TYPES[i];

                        if (varNames.containsKey(actualName)) {
                            if (!varTypes.get(varNames.get(actualName)).equals(t)) {
                                throw new IllegalArgumentException("Variable " + actualName + " has already been defined with a different type");
                            }
                        } else {
                            varNames.put(actualName, varNames.size());
                            varTypes.add(t);
                        }
                    }
                }
            }
        }

        InsnList insns = new InsnList();

        for (JsonElement element : data) {
            if (element.isJsonPrimitive()) {
                instructionGenerators.add(Objects.requireNonNull(createInstructionFactoryFromName(element.getAsString(), varNames)));
            } else {
                instructionGenerators.add(Objects.requireNonNull(createInstructionFactoryFromObject(element.getAsJsonObject(), mappings, methodIDMap)));
            }
        }
    }

    private BiConsumer<InsnList, int[]> createInstructionFactoryFromObject(JsonObject object, MappingResolver mappings, Map<String, MethodID> methodIDMap) {
        String type = object.get("type").getAsString();

        if (type.equals("INVOKEVIRTUAL") || type.equals("INVOKESTATIC") || type.equals("INVOKESPECIAL") || type.equals("INVOKEINTERFACE")) {
            return generateMethodCall(object, mappings, methodIDMap, type);
        } else if (type.equals("LDC")) {
            return generateConstantInsn(object);
        } else if (type.equals("NEW") || type.equals("ANEWARRAY") || type.equals("CHECKCAST") || type.equals("INSTANCEOF")) {
            return generateTypeInsn(object, mappings, type);
        }

        return null;
    }

    @NotNull private BiConsumer<InsnList, int[]> generateMethodCall(JsonObject object, MappingResolver mappings, Map<String, MethodID> methodIDMap, String type) {
        JsonElement method = object.get("method");
        MethodID methodID = null;

        if (method.isJsonPrimitive()) {
            methodID = methodIDMap.get(method.getAsString());
        }

        if (methodID == null) {
            MethodID.CallType callType = switch (type) {
                case "INVOKEVIRTUAL" -> MethodID.CallType.VIRTUAL;
                case "INVOKESTATIC" -> MethodID.CallType.STATIC;
                case "INVOKESPECIAL" -> MethodID.CallType.SPECIAL;
                case "INVOKEINTERFACE" -> MethodID.CallType.INTERFACE;

                default -> throw new IllegalArgumentException("Invalid call type: " + type); //This will never be reached but the compiler gets angry if it isn't here
            };
            methodID = ConfigLoader.loadMethodID(method, mappings, callType);
        }

        MethodID finalMethodID = methodID;
        return (insnList, __) -> {
            insnList.add(finalMethodID.callNode());
        };
    }

    @NotNull private BiConsumer<InsnList, int[]> generateConstantInsn(JsonObject object) {
        String constantType = object.get("constant_type").getAsString();

        JsonElement element = object.get("value");

        Object constant = switch (constantType) {
            case "string" -> element.getAsString();
            case "int" -> element.getAsInt();
            case "float" -> element.getAsFloat();
            case "long" -> element.getAsLong();
            case "double" -> element.getAsDouble();
            default -> throw new IllegalArgumentException("Invalid constant type: " + constantType);
        };

        InstructionFactory generator = new ConstantFactory(constant);

        return (insnList, __) -> {
            insnList.add(generator.create());
        };
    }

    @NotNull private BiConsumer<InsnList, int[]> generateTypeInsn(JsonObject object, MappingResolver mappings, String type) {
        JsonElement classNameJson = object.get("class");
        Type t = Type.getObjectType(classNameJson.getAsString());
        Type mappedType = ConfigLoader.remapType(t, mappings, false);

        int opcode = switch (type) {
            case "NEW" -> Opcodes.NEW;
            case "ANEWARRAY" -> Opcodes.ANEWARRAY;
            case "CHECKCAST" -> Opcodes.CHECKCAST;
            case "INSTANCEOF" -> Opcodes.INSTANCEOF;
            default -> {
                throw new IllegalArgumentException("Impossible to reach this point");
            }
        };

        return (insnList, __) -> insnList.add(new TypeInsnNode(opcode, mappedType.getInternalName()));
    }

    private BiConsumer<InsnList, int[]> createInstructionFactoryFromName(String insnName, Map<String, Integer> varNames) {
        for (int i = 0; i < VAR_INSNS.length; i++) {
            if (insnName.startsWith(VAR_INSNS[i])) {
                String varInsnName = VAR_INSNS[i];
                String varName = insnName.substring(varInsnName.length() + 2, insnName.length() - 1);
                int varIndex = varNames.get(varName);
                int opcode = VAR_OPCODES[i];

                return (insnList, indexes) -> insnList.add(new VarInsnNode(opcode, indexes[varIndex]));
            }
        }

        int opcode = opcodeFromName(insnName);

        return (insnList, indexes) -> insnList.add(new InsnNode(opcode));
    }

    private int opcodeFromName(String name) {
        return switch (name) {
            case "NOP" -> NOP;
            case "ACONST_NULL" -> ACONST_NULL;
            case "ICONST_M1" -> ICONST_M1;
            case "ICONST_0" -> ICONST_0;
            case "ICONST_1" -> ICONST_1;
            case "ICONST_2" -> ICONST_2;
            case "ICONST_3" -> ICONST_3;
            case "ICONST_4" -> ICONST_4;
            case "ICONST_5" -> ICONST_5;
            case "LCONST_0" -> LCONST_0;
            case "LCONST_1" -> LCONST_1;
            case "FCONST_0" -> FCONST_0;
            case "FCONST_1" -> FCONST_1;
            case "FCONST_2" -> FCONST_2;
            case "DCONST_0" -> DCONST_0;
            case "DCONST_1" -> DCONST_1;
            case "IALOAD" -> IALOAD;
            case "LALOAD" -> LALOAD;
            case "FALOAD" -> FALOAD;
            case "DALOAD" -> DALOAD;
            case "AALOAD" -> AALOAD;
            case "BALOAD" -> BALOAD;
            case "CALOAD" -> CALOAD;
            case "SALOAD" -> SALOAD;
            case "IASTORE" -> IASTORE;
            case "LASTORE" -> LASTORE;
            case "FASTORE" -> FASTORE;
            case "DASTORE" -> DASTORE;
            case "AASTORE" -> AASTORE;
            case "BASTORE" -> BASTORE;
            case "CASTORE" -> CASTORE;
            case "SASTORE" -> SASTORE;
            case "POP" -> POP;
            case "POP2" -> POP2;
            case "DUP" -> DUP;
            case "DUP_X1" -> DUP_X1;
            case "DUP_X2" -> DUP_X2;
            case "DUP2" -> DUP2;
            case "DUP2_X1" -> DUP2_X1;
            case "DUP2_X2" -> DUP2_X2;
            case "SWAP" -> SWAP;
            case "IADD" -> IADD;
            case "LADD" -> LADD;
            case "FADD" -> FADD;
            case "DADD" -> DADD;
            case "ISUB" -> ISUB;
            case "LSUB" -> LSUB;
            case "FSUB" -> FSUB;
            case "DSUB" -> DSUB;
            case "IMUL" -> IMUL;
            case "LMUL" -> LMUL;
            case "FMUL" -> FMUL;
            case "DMUL" -> DMUL;
            case "IDIV" -> IDIV;
            case "LDIV" -> LDIV;
            case "FDIV" -> FDIV;
            case "DDIV" -> DDIV;
            case "IREM" -> IREM;
            case "LREM" -> LREM;
            case "FREM" -> FREM;
            case "DREM" -> DREM;
            case "INEG" -> INEG;
            case "LNEG" -> LNEG;
            case "FNEG" -> FNEG;
            case "DNEG" -> DNEG;
            case "ISHL" -> ISHL;
            case "LSHL" -> LSHL;
            case "ISHR" -> ISHR;
            case "LSHR" -> LSHR;
            case "IUSHR" -> IUSHR;
            case "LUSHR" -> LUSHR;
            case "IAND" -> IAND;
            case "LAND" -> LAND;
            case "IOR" -> IOR;
            case "LOR" -> LOR;
            case "IXOR" -> IXOR;
            case "LXOR" -> LXOR;
            case "I2L" -> I2L;
            case "I2F" -> I2F;
            case "I2D" -> I2D;
            case "L2I" -> L2I;
            case "L2F" -> L2F;
            case "L2D" -> L2D;
            case "F2I" -> F2I;
            case "F2L" -> F2L;
            case "F2D" -> F2D;
            case "D2I" -> D2I;
            case "D2L" -> D2L;
            case "D2F" -> D2F;
            case "I2B" -> I2B;
            case "I2C" -> I2C;
            case "I2S" -> I2S;
            case "LCMP" -> LCMP;
            case "FCMPL" -> FCMPL;
            case "FCMPG" -> FCMPG;
            case "DCMPL" -> DCMPL;
            case "DCMPG" -> DCMPG;
            case "IRETURN" -> IRETURN;
            case "LRETURN" -> LRETURN;
            case "FRETURN" -> FRETURN;
            case "DRETURN" -> DRETURN;
            case "ARETURN" -> ARETURN;
            case "RETURN" -> RETURN;
            case "ARRAYLENGTH" -> ARRAYLENGTH;
            case "ATHROW" -> ATHROW;
            case "MONITORENTER" -> MONITORENTER;
            case "MONITOREXIT" -> MONITOREXIT;
            default -> throw new IllegalArgumentException("Error when reading JSON bytecode. Unknown instruction '" + name + "'");
        };
    }

    @Override
    public InsnList generate(Function<Type, Integer> varAllocator) {
        int[] vars = new int[this.varTypes.size()];

        for (int i = 0; i < this.varTypes.size(); i++) {
            vars[i] = varAllocator.apply(this.varTypes.get(i));
        }

        InsnList insnList = new InsnList();

        for (var generator : instructionGenerators) {
            generator.accept(insnList, vars);
        }

        return insnList;
    }
}
