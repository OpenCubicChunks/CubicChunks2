package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.util.Objects;

public class BooleanReference {
    private boolean value;

    public BooleanReference(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BooleanReference that = (BooleanReference) o;
        return value == that.value;
    }

    @Override public int hashCode() {
        return Objects.hash(value);
    }
}
