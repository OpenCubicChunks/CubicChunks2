package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

public class JSONBytecodeFactory implements BytecodeFactory{
    private static final String NAMESPACE = "intermediary";

    private final List<InstructionFactory> instructionGenerators = new ArrayList<>();

    public JSONBytecodeFactory(JsonArray data, MappingResolver mappings, Map<String, MethodID> methodIDMap){
        for(JsonElement element: data){
            if(element.isJsonPrimitive()){
                instructionGenerators.add(Objects.requireNonNull(createInstructionFactoryFromName(element.getAsString())));
            }else{
                instructionGenerators.add(Objects.requireNonNull(createInstructionFactoryFromObject(element.getAsJsonObject(), mappings, methodIDMap)));
            }
        }
    }

    private InstructionFactory createInstructionFactoryFromObject(JsonObject object, MappingResolver mappings, Map<String, MethodID> methodIDMap) {
        String type = object.get("type").getAsString();

        if(type.equals("INVOKEVIRTUAL") || type.equals("INVOKESTATIC") || type.equals("INVOKESPECIAL") || type.equals("INVOKEINTERFACE")){
            JsonElement method = object.get("method");
            MethodID methodID = null;

            if(method.isJsonPrimitive()){
                methodID = methodIDMap.get(method.getAsString());
            }

            if(methodID == null){
                MethodID.CallType callType = switch (type) {
                    case "INVOKEVIRTUAL" -> MethodID.CallType.VIRTUAL;
                    case "INVOKESTATIC" -> MethodID.CallType.STATIC;
                    case "INVOKESPECIAL" -> MethodID.CallType.SPECIAL;
                    case "INVOKEINTERFACE" -> MethodID.CallType.INTERFACE;

                    default -> throw new IllegalArgumentException("Invalid call type: " + type); //This will never be reached but the compiler gets angry if it isn't here
                };
                methodID = ConfigLoader.loadMethodID(method, mappings, callType);
            }

            return methodID::callNode;
        }else if(type.equals("LDC")){
            String constantType = object.get("constant_type").getAsString();

            JsonElement element = object.get("value");

            if(constantType.equals("string")){
                return () -> new LdcInsnNode(element.getAsString());
            }else if(constantType.equals("long")){
                long value = element.getAsLong();
                if(value == 0) return () -> new InsnNode(Opcodes.LCONST_0);
                else if(value == 1) return () -> new InsnNode(Opcodes.LCONST_1);

                return () -> new LdcInsnNode(value);
            }else if(constantType.equals("int")){
                int value = element.getAsInt();

                if(value >= -1 && value <= 5){
                    return () -> new InsnNode(Opcodes.ICONST_0 + value);
                }

                return () -> new LdcInsnNode(value);
            }else if(constantType.equals("double")){
                double value = element.getAsDouble();

                if(value == 0) return () -> new InsnNode(Opcodes.DCONST_0);
                else if(value == 1) return () -> new InsnNode(Opcodes.DCONST_1);

                return () -> new LdcInsnNode(value);
            }else if(constantType.equals("float")){
                float value = element.getAsFloat();

                if(value == 0) return () -> new InsnNode(Opcodes.FCONST_0);
                else if(value == 1) return () -> new InsnNode(Opcodes.FCONST_1);
                else if(value == 2) return () -> new InsnNode(Opcodes.FCONST_2);

                return () -> new LdcInsnNode(value);
            }else{
                throw new IllegalStateException("Illegal entry for 'constant_type' (" + constantType + ")");
            }
        }

        return null;
    }

    private InstructionFactory createInstructionFactoryFromName(String insnName) {
        int opcode = opcodeFromName(insnName);

        return () -> new InsnNode(opcode);
    }

    private int opcodeFromName(String name){
        return switch (name){
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

    @Override public InsnList generate() {
        InsnList generated = new InsnList();
        for(InstructionFactory instructionFactory: instructionGenerators){
            generated.add(instructionFactory.create());
        }
        return generated;
    }
}
