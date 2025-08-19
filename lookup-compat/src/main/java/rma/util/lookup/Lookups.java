package rma.util.lookup;

/**
 * This is a transitional class to provide temporary compatibility with HEC codebases until they can upgrade to the
 * upstream Netbeans Lookup library.
 * <br><br>
 * Originally, NetBeans did not ship the Lookup library as a standalone library. Since it provided useful functionality,
 * the Lookup code was duplicated into the package rma.util for use in some application development. Over time,
 * adoption of this library spread across many HEC libraries and applications.
 * <br><br>
 * Today, Netbeans ships Lookup as a standalone library, so this duplicated copy of the code is no longer necessary.
 * Unfortunately, updating the code across every relevant application and library takes time. The legacy copy
 * of the code in rma.util has bugs with recent versions of Java  which have since been solved in the upstream codebase.
 * Rather than continuing to maintain an unnecessary fork indefinitely, this class provides a minimal Proxy which allows
 * code compiled against the old library to call the upstream library until a time when it can be updated.
 * <br><br>
 * Applications that move to Java 21+ but still depend on libraries that use the old rma.util Lookup code
 * should use a dependency substitution to swap this library in to replace the old Lookup library. Codebases should
 * avoid compiling against this library, and it should be used purely for transitional purposes.
 *
 * @deprecated Use @{@link org.openide.util.lookup.Lookups} instead.
 * @author Stephen Ackerman, GEI Consultants
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public final class Lookups {

    private Lookups() {}

    public static Lookup singleton(Object objectToLookup) {
        return new DelegatingLookup(org.openide.util.lookup.Lookups.singleton(objectToLookup));
    }

    public static Lookup fixed(Object... objectsToLookup) {
        return new DelegatingLookup(org.openide.util.lookup.Lookups.fixed(objectsToLookup));
    }

    public static Lookup metaInfServices(ClassLoader classLoader) {
        return new DelegatingLookup(org.openide.util.lookup.Lookups.metaInfServices(classLoader));
    }

    public static Lookup metaInfServices(ClassLoader classLoader, String prefix) {
        return new DelegatingLookup(org.openide.util.lookup.Lookups.metaInfServices(classLoader, prefix));
    }

    public static Lookup forPath(String path) {
        return new DelegatingLookup(org.openide.util.lookup.Lookups.forPath(path));
    }
}
