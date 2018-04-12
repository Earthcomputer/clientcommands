package net.earthcomputer.clientcommands.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * When a field is labeled with this annotation, delegate methods are generated
 * in the surrounding class
 */
public @interface Proxy {
}
