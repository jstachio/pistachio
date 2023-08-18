import javax.annotation.processing.Processor;

/**
 *
 * Prism processor you almost never want to require this module.
 * @author agentgt
 *
 * @provides Processor
 */
module io.jstach.prism.apt {
	requires java.compiler;
	provides Processor with io.jstach.prism.apt.PrismGenerator;
}