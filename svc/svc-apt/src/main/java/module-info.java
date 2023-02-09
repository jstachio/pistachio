/**
 * 
 * You should never requires this module otherwise you will have problems.
 * 
 * @author agentgt
 *
 * @provides javax.annotation.processing.Processor
 */
module io.jstach.svc.apt {
	requires java.compiler;
	provides javax.annotation.processing.Processor with io.jstach.svc.apt.ServiceProcessor;
}