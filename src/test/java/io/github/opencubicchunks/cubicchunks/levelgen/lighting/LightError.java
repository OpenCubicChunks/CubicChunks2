package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

public record LightError(String message, String lightSliceInfo) {
    public void report() {
        System.err.println(this.lightSliceInfo);
        throw new AssertionError(this.message);
    }

    public void reportWithAdditional(String additionalMessage) {
        System.err.printf("%s\n%s\n", this.lightSliceInfo, additionalMessage);
        throw new AssertionError(this.message);
    }
}