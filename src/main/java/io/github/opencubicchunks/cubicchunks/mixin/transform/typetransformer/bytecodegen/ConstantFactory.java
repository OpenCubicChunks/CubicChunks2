package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.bytecodegen;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

/**
 * An instruction factory which creates an instruction which creates a provided constant
 */
public class ConstantFactory implements InstructionFactory {
    private final Object value;

    /**
     * Creates a new constant factory
     * @param value The value of the constant that this factory will create
     */
    public ConstantFactory(Object value) {
        this.value = value;
    }

    @Override
    public AbstractInsnNode create() {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            Number number = (Number) value;
            int n = number.intValue();

            if (n >= -1 && n <= 5) {
                return new InsnNode(Opcodes.ICONST_0 + n);
            } else if (n < 256) {
                return new IntInsnNode(Opcodes.BIPUSH, n);
            } else if (n < 65536) {
                return new IntInsnNode(Opcodes.SIPUSH, n);
            }
        } else if (value instanceof Long l) {
            if (l == 0 || l == 1) {
                return new InsnNode((int) (Opcodes.LCONST_0 + l));
            }
        } else if (value instanceof Float f) {
            if (f == 0.0f || f == 1.0f || f == 2.0f) {
                return new InsnNode((int) (Opcodes.FCONST_0 + f));
            }
        } else if (value instanceof Double d) {
            if (d == 0.0d || d == 1.0d) {
                return new InsnNode((int) (Opcodes.DCONST_0 + d));
            }
        }

        return new LdcInsnNode(value);
    }
}
