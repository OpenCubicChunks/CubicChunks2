package io.github.opencubicchunks.cubicchunks.test.util;

public class IndentingStringBuilder {
    private final int indentation;
    private int indentationLevel = 0;
    private final StringBuilder wrapped = new StringBuilder();

    public IndentingStringBuilder(int indentation) {
        this.indentation = indentation;
    }

    public IndentingStringBuilder append(String string) {
        String indent = " ".repeat(this.indentation * this.indentationLevel);
        for (String line : string.split("\n")) {
            this.wrapped.append(indent);
            this.wrapped.append(line).append('\n');
        }
        // remove last \n
        this.wrapped.deleteCharAt(this.wrapped.length() - 1);
        return this;
    }

    public IndentingStringBuilder append(Object obj) {
        return this.append(String.valueOf(obj));
    }

    public IndentingStringBuilder appendNewLine() {
        this.wrapped.append('\n');
        return this;
    }

    public IndentingStringBuilder indent() {
        this.indentationLevel++;
        return this;
    }

    public IndentingStringBuilder unIndent() {
        this.indentationLevel--;
        return this;
    }

    @Override public String toString() {
        return this.wrapped.toString();
    }
}
