package org.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Generate {
        /**
         * @return A class for the generated abstract class to extend. Does not support
         * providing type parameters to generic classes or extending classes which do not have a
         * zero arg constructor.
         */
        Class<?> extend() default Object.class;

}