package io.jstach.pistachio.svc;

import static java.lang.annotation.ElementType.MODULE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Retention(SOURCE)
@Target({ TYPE, MODULE })
public @interface ServiceProvider {

	Class<?>[] value() default void.class;

}
