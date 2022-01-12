package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.accessor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen.BytecodeFactory;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.analysis.TransformSubtype;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodParameterInfo;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodReplacement;
import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.MethodTransformChecker;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.AncestorHashMap;
import io.github.opencubicchunks.cubicchunks.mixin.transform.util.MethodID;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public abstract class AccessorMethodInfo {
    protected final String targetClassName;
    protected final TransformSubtype[] argTypes;
    protected final TransformSubtype returnType;
    protected final MinimumPolicy policy;
    protected AccessorClassInfo accessorClassInfo = null;

    protected final String originalName, originalDesc;

    protected AccessorMethodInfo(String targetClassName, TransformSubtype[] argTypes, TransformSubtype returnType,
                                 MinimumPolicy policy, String originalName, String originalDesc) {
        this.argTypes = argTypes;
        this.policy = policy;
        this.targetClassName = targetClassName;
        this.returnType = returnType;
        this.originalName = originalName;
        this.originalDesc = originalDesc;
    }

    public abstract MethodNode generateSafetyImplementation();
    public abstract MethodNode generateAbstractSignature();

    public String newDesc(){
        return MethodParameterInfo.getNewDesc(TransformSubtype.of(null), argTypes, originalDesc);
    }

    public void addParameterInfoTo(AncestorHashMap<MethodID, List<MethodParameterInfo>> parameterInfo) {
        final String desc = newDesc();
        final String name = originalName + (originalDesc.equals(desc) ? TypeTransformer.MIX : "");

        Type[] argTypes = Type.getArgumentTypes(desc);

        BytecodeFactory replacementCode = (Function<Type, Integer> variableAllocator) -> {
            InsnList list = new InsnList();

            //Save all the arguments into variables to load them later
            List<Integer> varIndices = new ArrayList<>();
            for(Type t: argTypes){
                int varIndex = variableAllocator.apply(t);
                varIndices.add(varIndex);
            }

            //Save the arguments into their corresponding variables
            for (int i = varIndices.size() - 1; i >= 0; i--) {
                Type t = argTypes[i];
                int varIndex = varIndices.get(i);

                list.add(new VarInsnNode(t.getOpcode(Opcodes.ISTORE), varIndex));
            }

            //First cast to target class
            list.add(new TypeInsnNode(Opcodes.CHECKCAST, targetClassName));

            //Then cast to the new interface
            list.add(new TypeInsnNode(Opcodes.CHECKCAST, accessorClassInfo.getNewClassName()));

            //Load the arguments back
            for (int i = 0; i < varIndices.size(); i++) {
                Type t = argTypes[i];
                int varIndex = varIndices.get(i);

                list.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), varIndex));
            }

            //Then call the method
            list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, accessorClassInfo.getNewClassName(), name, desc));

            return list;
        };

        MethodReplacement replacement = new MethodReplacement(replacementCode);

        MethodID methodID = new MethodID(accessorClassInfo.getMixinClass().getInternalName(), originalName, originalDesc, MethodID.CallType.INTERFACE);

        MethodTransformChecker.Minimum[] minimums;

        if(policy == MinimumPolicy.ALL){
            MethodTransformChecker.Minimum minimum = new MethodTransformChecker.Minimum(returnType, this.argTypes);
            minimums = new MethodTransformChecker.Minimum[]{minimum};
        }else{
            List<MethodTransformChecker.Minimum> minimumsList = new ArrayList<>();

            if(returnType.getTransformType() != null){
                TransformSubtype[] newArgTypes = new TransformSubtype[argTypes.length];
                for(int i = 0; i < argTypes.length; i++){
                    newArgTypes[i] = TransformSubtype.of(null);
                }

                minimumsList.add(new MethodTransformChecker.Minimum(returnType, newArgTypes));
            }

            for(int i = 0; i < this.argTypes.length; i++){
                if(this.argTypes[i].getTransformType() != null){
                    TransformSubtype[] newArgTypes = new TransformSubtype[this.argTypes.length];

                    for(int j = 0; j < this.argTypes.length; j++){
                        newArgTypes[j] = TransformSubtype.of(null);
                    }

                    newArgTypes[i] = this.argTypes[i];

                    minimumsList.add(new MethodTransformChecker.Minimum(TransformSubtype.of(null), newArgTypes));
                }
            }

            minimums = minimumsList.toArray(new MethodTransformChecker.Minimum[0]);
        }

        TransformSubtype[] actualArgTypes = new TransformSubtype[this.argTypes.length + 1];
        actualArgTypes[0] = TransformSubtype.of(null);
        System.arraycopy(this.argTypes, 0, actualArgTypes, 1, this.argTypes.length);

        MethodParameterInfo info = new MethodParameterInfo(
            methodID,
            returnType,
            actualArgTypes,
            minimums,
            replacement
        );

        parameterInfo.computeIfAbsent(methodID, k -> new ArrayList<>()).add(info);
    }

    public void setAccessorClassInfo(AccessorClassInfo accessorClassInfo){
        if(this.accessorClassInfo != null){
            throw new IllegalStateException("Accessor class info already set");
        }

        this.accessorClassInfo = accessorClassInfo;
    }

    public enum MinimumPolicy{
        ANY,
        ALL;

        public static MinimumPolicy fromString(String minimumPolicy) {
            return switch (minimumPolicy.toUpperCase()) {
                case "ANY" -> ANY;
                case "ALL" -> ALL;
                default -> throw new IllegalArgumentException("Unknown minimum policy: " + minimumPolicy);
            };
        }
    }
}
