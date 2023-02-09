package io.jstach.svc;

import static java.lang.annotation.ElementType.MODULE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ServiceLoader;

/**
 * Generates {@link ServiceLoader} <code>META-INF/services/SERVICE_INTERFACE</code> files
 * and validates.
 *
 * @author agentgt
 */
@Documented
@Retention(SOURCE)
@Target({ TYPE, MODULE })
public @interface ServiceProvider {

	/**
	 * The specific interface to generate a service registration.
	 * @return if <code>void</code> is returned which is the default the interface will be
	 * inferred.
	 */
	Class<?>[] value() default void.class;

}
