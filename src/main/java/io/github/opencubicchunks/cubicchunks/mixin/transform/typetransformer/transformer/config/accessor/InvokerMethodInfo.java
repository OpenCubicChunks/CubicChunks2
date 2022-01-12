package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.accessor;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.ConfigLoader;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TransformType;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class InvokerMethodInfo extends AccessorMethodInfo {
    private final String targetName;
    private final boolean isStatic;

    protected InvokerMethodInfo(String targetClassName, TransformSubtype[] argTypes,
                                TransformSubtype returnType,
                                MinimumPolicy policy, String originalName, String originalDesc, String targetName, boolean isStatic) {
        super(targetClassName, argTypes, returnType, policy, originalName, originalDesc);
        this.targetName = targetName;
        this.isStatic = isStatic;
    }


    @Override
    public MethodNode generateSafetyImplementation() {
        MethodNode implementation = generateAbstractSignature();
        implementation.access &= ~Opcodes.ACC_ABSTRACT;

        InsnList list = implementation.instructions;

        list.add(TypeTransformer.generateEmitWarningCall("Default implementation of invoke is being used", 2));

        //Invoke the method
        int varIndex = 0;
        if(!isStatic) {
            list.add(new VarInsnNode(Opcodes.ALOAD, varIndex++));
        }

        for(Type t: Type.getArgumentTypes(implementation.desc)) {
            list.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), varIndex));
            varIndex += t.getSize();
        }

        //Invoke the method
        int opcode = isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;

        list.add(new MethodInsnNode(opcode, targetClassName, targetName, implementation.desc + (implementation.desc.equals(originalDesc) ? TypeTransformer.MIX : ""), false));
        list.add(new InsnNode(Type.getReturnType(implementation.desc).getOpcode(Opcodes.IRETURN)));

        return implementation;
    }

    @Override
    public MethodNode generateAbstractSignature() {
        String newDesc = newDesc();
        String name = originalName;

        if(newDesc.equals(originalDesc)){
            name += TypeTransformer.MIX;
        }

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC + (isStatic ? Opcodes.ACC_STATIC : Opcodes.ACC_ABSTRACT), name, newDesc, null, null);
        return method;
    }

    public static AccessorMethodInfo load(JsonObject json, String className, MappingResolver mappingResolver, Map<String, TransformType> typeLookup) {
        String invokerName = json.get("name").getAsString();
        String[] targetMethod = json.get("target").getAsString().split(" ");

        boolean isStatic = json.get("static").getAsBoolean();

        String targetNameUnmapped = targetMethod[0];
        String targetDescUnmapped = targetMethod[1];

        MethodID unmappedTarget = new MethodID(className, targetNameUnmapped, targetDescUnmapped, isStatic ? MethodID.CallType.STATIC : MethodID.CallType.VIRTUAL);
        MethodID target = ConfigLoader.remapMethod(unmappedTarget, mappingResolver);

        TransformSubtype[] argTypes = new TransformSubtype[target.getDescriptor().getArgumentTypes().length];
        JsonArray transformedArgs = json.getAsJsonArray("transformed_args");

        int i;

        for(i = 0; i < transformedArgs.size(); i++) {
            JsonElement arg = transformedArgs.get(i);
            if(arg.isJsonNull()){
                argTypes[i] = TransformSubtype.of(null);
            }else {
                argTypes[i] = TransformSubtype.fromString(arg.getAsString(), typeLookup);
            }
        }

        for(; i < argTypes.length; i++) {
            argTypes[i] = TransformSubtype.of(null);
        }

        TransformSubtype returnType = TransformSubtype.of(null);
        JsonElement returnTypeElement = json.get("return_type");
        if(returnTypeElement != null && !returnTypeElement.isJsonNull()){
            returnType = TransformSubtype.fromString(returnTypeElement.getAsString(), typeLookup);
        }

        String minimumPolicy = json.get("minimum_policy").getAsString();
        MinimumPolicy policy = MinimumPolicy.fromString(minimumPolicy);

        return new InvokerMethodInfo(
            target.getOwner().getInternalName(),
            argTypes,
            returnType,
            policy,
            invokerName,
            target.getDescriptor().getDescriptor(),
            target.getName(),
            isStatic
        );
    }
}
