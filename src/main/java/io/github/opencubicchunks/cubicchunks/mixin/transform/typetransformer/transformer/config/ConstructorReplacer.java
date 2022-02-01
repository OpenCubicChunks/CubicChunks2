package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config;

import java.util.HashMap;
import java.util.Map;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.TypeTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public record ConstructorReplacer(String originalDesc, Map<String, String> replacements) {
    public InsnList make(TypeTransformer transformer) {
        //Get original
        MethodNode original = transformer.getClassNode().methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals(originalDesc)).findAny().orElseThrow();
        InsnList originalCode = original.instructions;

        InsnList newCode = new InsnList();

        //Make label copies
        Map<LabelNode, LabelNode> labelCopies = new HashMap<>();
        for (AbstractInsnNode insn : originalCode) {
            if (insn instanceof LabelNode labelNode) {
                labelCopies.put(labelNode, new LabelNode());
            }
        }

        //Copy original code and modify required types
        for (AbstractInsnNode insn : originalCode) {
            if (insn instanceof LabelNode labelNode) {
                newCode.add(labelCopies.get(labelNode));
            } else if (insn instanceof TypeInsnNode typeInsnNode) {
                String desc = typeInsnNode.desc;
                if (replacements.containsKey(desc)) {
                    desc = replacements.get(desc);
                }

                newCode.add(new TypeInsnNode(typeInsnNode.getOpcode(), desc));
            } else if (insn instanceof MethodInsnNode methodInsnNode) {
                Type owner = Type.getObjectType(methodInsnNode.owner);
                Type[] args = Type.getArgumentTypes(methodInsnNode.desc);

                if (replacements.containsKey(owner.getInternalName())) {
                    owner = Type.getObjectType(replacements.get(owner.getInternalName()));
                }

                for (int i = 0; i < args.length; i++) {
                    if (replacements.containsKey(args[i].getInternalName())) {
                        args[i] = Type.getObjectType(replacements.get(args[i].getInternalName()));
                    }
                }

                //Check for itf
                int opcode = methodInsnNode.getOpcode();
                boolean itf = opcode == Opcodes.INVOKEINTERFACE;
                if (itf || opcode == Opcodes.INVOKEVIRTUAL) {
                    itf = transformer.getConfig().getHierarchy().recognisesInterface(owner);

                    opcode = itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
                }

                newCode.add(new MethodInsnNode(opcode, owner.getInternalName(), methodInsnNode.name, Type.getMethodDescriptor(Type.getReturnType(methodInsnNode.desc), args), itf));
            } else {
                newCode.add(insn.clone(labelCopies));
            }
        }

        return newCode;
    }
}
