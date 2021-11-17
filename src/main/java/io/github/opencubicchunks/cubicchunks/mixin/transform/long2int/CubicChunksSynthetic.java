package io.github.opencubicchunks.cubicchunks.mixin.transform.long2int;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(java.lang.annotation.ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CubicChunksSynthetic {
    String original();
    String type();
}
