package io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is generated through ASM and should not be added to methods manually. It tracks which methods are added through ASM and how they were made.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CCSynthetic {
    String type();
    String original();
}
