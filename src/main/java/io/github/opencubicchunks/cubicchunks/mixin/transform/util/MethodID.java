package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import java.util.Objects;

import it.unimi.dsi.fastutil.Hash;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

public class MethodID implements Ancestralizable<MethodID> {
    public static final Hash.Strategy<MethodID> HASH_CALL_TYPE = new Hash.Strategy<>() {
        @Override public int hashCode(MethodID o) {
            return Objects.hash(o.callType, o.owner, o.name, o.descriptor);
        }

        @Override public boolean equals(MethodID a, MethodID b) {
            if (a == b) return true;
            if (a == null || b == null) return false;

            return a.callType == b.callType && a.owner.equals(b.owner) && a.name.equals(b.name) && a.descriptor.equals(b.descriptor);
        }
    };

    private final Type owner;
    private final String name;
    private final Type descriptor;

    private final CallType callType;

    public MethodID(Type owner, String name, Type descriptor, CallType callType) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.callType = callType;
    }

    public MethodID(String owner, String name, String desc, CallType callType) {
        this(Type.getObjectType(owner), name, Type.getMethodType(desc), callType);

    }

    public static MethodID from(MethodInsnNode methodCall) {
        Type owner = Type.getObjectType(methodCall.owner);
        Type descriptor = Type.getMethodType(methodCall.desc);
        String name = methodCall.name;
        CallType callType = CallType.fromOpcode(methodCall.getOpcode());

        return new MethodID(owner, name, descriptor, callType);
    }

    public Type getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public Type getDescriptor() {
        return descriptor;
    }

    public CallType getCallType() {
        return callType;
    }

    public MethodInsnNode callNode() {
        return new MethodInsnNode(callType.getOpcode(), owner.getInternalName(), name, descriptor.getDescriptor(), callType == CallType.INTERFACE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodID methodID = (MethodID) o;
        return Objects.equals(owner, methodID.owner) && Objects.equals(name, methodID.name) && Objects.equals(descriptor, methodID.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public Type getAssociatedType() {
        return owner;
    }

    @Override
    public MethodID withType(Type subType) {
        return new MethodID(subType, name, descriptor, callType);
    }

    @Override
    public String toString() {
        String ownerName = ASMUtil.onlyClassName(owner.getClassName());

        String returnTypeName = ASMUtil.onlyClassName(descriptor.getReturnType().getClassName());

        StringBuilder sb = new StringBuilder();
        sb.append(returnTypeName).append(" ");
        sb.append(ownerName).append(".").append(name).append("(");
        int i = 0;
        for (Type argType : descriptor.getArgumentTypes()) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ASMUtil.onlyClassName(argType.getClassName()));
            i++;
        }
        sb.append(")");
        return sb.toString();
    }

    public boolean isStatic() {
        return callType == CallType.STATIC;
    }

    public enum CallType {
        VIRTUAL(Opcodes.INVOKEVIRTUAL),
        STATIC(Opcodes.INVOKESTATIC),
        SPECIAL(Opcodes.INVOKESPECIAL),
        INTERFACE(Opcodes.INVOKEINTERFACE);

        private final int opcode;

        CallType(int opcode) {
            this.opcode = opcode;
        }

        public static CallType fromOpcode(int opcode) {
            return switch (opcode) {
                case Opcodes.INVOKEVIRTUAL -> VIRTUAL;
                case Opcodes.INVOKESTATIC -> STATIC;
                case Opcodes.INVOKESPECIAL -> SPECIAL;
                case Opcodes.INVOKEINTERFACE -> INTERFACE;
                default -> throw new IllegalArgumentException("Unknown opcode " + opcode);
            };
        }

        public int getOpcode() {
            return opcode;
        }

        public int getOffset() {
            if (this == STATIC) {
                return 0;
            }
            return 1;
        }
    }
}
