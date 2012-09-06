/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rma.services.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative registration of a singleton service provider.
 * By marking an implementation class with this annotation,
 * you automatically register that implementation, normally in .
 * The class must be public and have a public no-argument constructor.
 * <p>Example of usage:
 * <pre>
 * package my.module;
 * import com.rma.spi.whatever.Thing;
 * import com.rma.annotations.service.ServiceProvider;
 * &#64;ServiceProvider(service=Thing.class)
 * public class MyThing implements Thing {...}
 * </pre>
 * <p>would result in a resource file <code>META-INF/services/com.rma.spi.whatever.Thing</code>
 * containing the single line of text: <code>my.module.MyThing</code>
 * 
 * @author Shannon Newbold (sjnewbold@rmanet.com)
 * 
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface ServiceProvider {

    /**
     * The interface (or abstract class) to register this implementation under.
     * It is an error if the implementation class is not in fact assignable to the interface.
     * <p>If you need to register one class under multiple interfaces, use {@link ServiceProviders}.
     * <p>Requests to look up the specified interface should result in this implementation.
     * Requests for any other types may or may not result in this implementation even if the
     * implementation is assignable to those types.
     */
    Class<?> service();

    /**
     * An optional position in which to register this service relative to others.
     * Lower-numbered services are returned in the lookup result first.
     * Services with no specified position are returned last.
     */
    int position() default Integer.MAX_VALUE;

    /**
     * An optional list of implementations (given as fully-qualified class names) which this implementation supersedes.
     * If specified, those implementations will not be loaded even if they were registered.
     * Useful on occasion to cancel a generic implementation and replace it with a more advanced one.
     */
    String[] supersedes() default {};

    /**
     * An optional path to register this implementation in.
     * For example, <code>Projects/sometype/Nodes</code> could be used.
     */
    String path() default "";

}
