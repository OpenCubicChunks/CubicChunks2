package io.github.opencubicchunks.cubicchunks.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface TransformFrom {

    String value();

    Signature signature() default @Signature(fromString = true);

    boolean makeSyntheticAccessor() default false;

    @interface Signature {
        Class<?>[] args() default {};
        Class<?> ret() default void.class;
        boolean fromString() default false;
    }
}
