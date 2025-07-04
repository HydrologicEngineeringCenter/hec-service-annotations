/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package mil.army.usace.hec.util.lookup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A general registry permitting clients to find instances of services
 * (implementation of a given interface). This class is inspired by the
 * <a href="http://www.jini.org/">Jini</a>
 * registration and lookup mechanism. The difference is that the methods do not
 * throw checked exceptions (as they usually work only locally and not over the
 * network) and that the Lookup API concentrates on the lookup, not on the
 * registration (although {@link Lookup#getDefault} is strongly encouraged to
 * support {@link Lookups#metaInfServices} for registration in addition to
 * whatever else it decides to support).
 * <p>
 * For a general talk about the idea behind the lookup pattern please see
 * <UL>
 * <LI><a href="doc-files/index.html">The Solution to Communication Between
 * Components</a>
 * page
 * <LI>the introduction to the <a href="doc-files/lookup-api.html">lookup API
 * via use cases</a>
 * <LI>the examples of <a href="doc-files/lookup-spi.html">how to write your own
 * lookup</a>
 * </UL>
 * <p>
 * @see mil.army.usace.hec.util.lookup.AbstractLookup
 * @see Lookups
 * @see LookupListener
 * @see LookupEvent
 * @author Jaroslav Tulach
 */
public abstract class Lookup implements Serializable
{

    private static final long serialVersionUID = -2186527574572087188L;
    /**
     * A dummy lookup that never returns any results.
     */
    public static final Lookup EMPTY = new Empty();

    /**
     * default instance
     */
    private static Lookup defaultLookup;

    /**
     * Empty constructor for use by subclasses.
     */
    public Lookup()
    {
    }

    /**
     * Static method to obtain the global lookup in the whole system. The actual
     * returned implementation can be different in different systems, but the
     * default one is based on {@link mil.army.usace.hec.util.lookup.Lookups#metaInfServices}
     * with the context classloader of the first caller. Each system is adviced
     * to honor this and include some form of <code>metaInfServices</code>
     * implementation in the returned lookup as usage of
     * <code>META-INF/services</code> is a JDK standard.
     * <p>
     * @return the global lookup in the system
     * <p>
     * @see mil.army.usace.hec.services.annotations.ServiceProvider
     */
    public static synchronized Lookup getDefault()
    {
        if (defaultLookup != null) {
            return defaultLookup;
        }

        // You can specify a Lookup impl using a system property if you like.
        String className = System.getProperty("mil.army.usace.hec.util.lookup.Lookup", // NOI18N
            System.getProperty("rma.util.lookup.Lookup")  // NOI18N
        );

        if ("-".equals(className)) { // NOI18N

            // Suppress even MetaInfServicesLookup.
            return EMPTY;
        }

        ClassLoader l = Thread.currentThread().getContextClassLoader();

        try {
            if (className != null) {
                defaultLookup = (Lookup) Class.forName(className, true, l).newInstance();

                return defaultLookup;
            }
        }
        catch (Exception e) {
            // do not use ErrorManager because we are in the startup code
            // and ErrorManager might not be ready
            e.printStackTrace();
        }

        // OK, none specified (successfully) in a system property.
        // Try MetaInfServicesLookup as a default, which may also
        // have a mil.army.usace.hec.util.lookup.Lookup line specifying the lookup.
        Lookup misl = Lookups.metaInfServices(l);
        defaultLookup = misl.lookup(Lookup.class);

        if (defaultLookup != null) {
            return defaultLookup;
        }

        // You may also specify a Lookup.Provider.
        Lookup.Provider prov = misl.lookup(Lookup.Provider.class);

        if (prov != null) {
            defaultLookup = Lookups.proxy(prov);

            return defaultLookup;
        }

        DefLookup def = new DefLookup();
        def.init(l, misl, false);
        defaultLookup = def;
        def.init(l, misl, true);
        return defaultLookup;
    }

    private static final class DefLookup extends ProxyLookup
    {
        private static final long serialVersionUID = 8292269042624460714L;

        public DefLookup()
        {
            super(new Lookup[0]);
        }

        public void init(ClassLoader loader, Lookup metaInfLookup, boolean addPath)
        {
            // Had no such line, use simple impl.
            // It does however need to have ClassLoader available or many things will break.
            // Use the thread context classloader in effect now.
            Lookup clLookup = Lookups.singleton(loader);
            List<Lookup> arr = new ArrayList<Lookup>();
            arr.add(metaInfLookup);
            arr.add(clLookup);
            String paths = System.getProperty("mil.army.usace.hec.util.lookup.Lookup.paths"); // NOI18N
            if (addPath && paths != null) {
                for (String p : paths.split(":")) { // NOI18N
                    arr.add(Lookups.forPath(p));
                }
            }
            setLookups(arr.toArray(new Lookup[0]));
        }
    }

    /**
     * Called from MockServices to reset default lookup in case services change
     */
    private static void resetDefaultLookup()
    {
        if (defaultLookup instanceof DefLookup) {
            DefLookup def = (DefLookup) defaultLookup;
            ClassLoader l = Thread.currentThread().getContextClassLoader();
            def.init(l, Lookups.metaInfServices(l), true);
        }
    }

    /**
     * Look up an object matching a given interface. This is the simplest method
     * to use. If more than one object matches, the first will be returned. The
     * template class may be a class or interface; the instance is guaranteed to
     * be assignable to it.
     * <p>
     * @param clazz class of the object we are searching for
     * <p>
     * @return an object implementing the given class or <code>null</code> if no
     *         such implementation is found
     */
    public abstract <T> T lookup(Class<T> clazz);

    /**
     * The general lookup method. Callers can get list of all instances and
     * classes that match the given <code>template</code>, request more info
     * about them in form of {@link Lookup.Item} and attach a listener to this
     * be notified about changes. The general interface does not specify whether
     * subsequent calls with the same template produce new instance of the
     * {@link Lookup.Result} or return shared instance. The prefered behaviour
     * however is to return shared one.
     * <p>
     * @param template a template describing the services to look for
     * <p>
     * @return an object containing the results
     */
    public abstract <T> Result<T> lookup(Template<T> template);

    /**
     * Look up the first item matching a given template. Includes not only the
     * instance but other associated information.
     * <p>
     * @param template the template to check
     * <p>
     * @return a matching item or <code>null</code>
     * <p>
     * @since 1.8
     */
    public <T> Item<T> lookupItem(Template<T> template)
    {
        Result<T> res = lookup(template);
        Iterator<? extends Item<T>> it = res.allItems().iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Find a result corresponding to a given class. Equivalent to calling
     * {@link #lookup(Lookup.Template)} but slightly more convenient. Subclasses
     * may override this method to produce the same semantics more efficiently.
     * <p>
     * @param clazz the supertype of the result
     * <p>
     * @return a live object representing instances of that type
     * <p>
     * @since mil.army.usace.hec.util.lookup 6.10
     */
    public <T> Lookup.Result<T> lookupResult(Class<T> clazz)
    {
        return lookup(new Lookup.Template<T>(clazz));
    }

    /**
     * Find all instances corresponding to a given class. Equivalent to calling
     * {@link #lookupResult} and asking for {@link Lookup.Result#allInstances}
     * but slightly more convenient. Subclasses may override this method to
     * produce the same semantics more efficiently.
     * <div class="nonnormative">
     * <p>
     * Example usage:</p>
     * <pre>
     * for (MyService svc : Lookup.getDefault().lookupAll(MyService.class)) {
     *     svc.useMe();
     * }
     * </pre>
     * </div>
     * <p>
     * @param clazz the supertype of the result
     * <p>
     * @return all currently available instances of that type
     * <p>
     * @since mil.army.usace.hec.util.lookup 6.10
     */
    public <T> Collection<? extends T> lookupAll(Class<T> clazz)
    {
        return lookupResult(clazz).allInstances();
    }

    /**
     * Objects implementing interface Lookup.Provider are capable of and willing
     * to provide a lookup (usually bound to the object).
     * <p>
     * @since 3.6
     */
    public interface Provider
    {

        /**
         * Returns lookup associated with the object.
         * <p>
         * @return fully initialized lookup instance provided by this object
         */
        Lookup getLookup();
    }

    /*
     * I expect this class to grow in the future, but for now, it is
     * enough to start with something simple.
     */
    /**
     * Template defining a pattern to filter instances by.
     */
    public static final class Template<T> extends Object
    {

        /**
         * cached hash code
         */
        private int hashCode;

        /**
         * type of the service
         */
        private Class<T> type;

        /**
         * identity to search for
         */
        private String id;

        /**
         * instance to search for
         */
        private T instance;

        /**
         * General template to find all possible instances.
         * <p>
         * @deprecated Use <code>new Template (Object.class)</code> which is
         * going to be better typed with JDK1.5 templates and should produce the
         * same result.
         */
        @Deprecated
        public Template()
        {
            this(null);
        }

        /**
         * Create a simple template matching by class.
         * <p>
         * @param type the class of service we are looking for (subclasses will
         *             match)
         */
        public Template(Class<T> type)
        {
            this(type, null, null);
        }

        /**
         * Constructor to create new template.
         * <p>
         * @param type     the class of service we are looking for or
         *                 <code>null</code> to leave unspecified
         * @param id       the ID of the item/service we are looking for or
         *                 <code>null</code> to leave unspecified
         * @param instance a specific known instance to look for or
         *                 <code>null</code> to leave unspecified
         */
        public Template(Class<T> type, String id, T instance)
        {
            this.type = extractType(type);
            this.id = id;
            this.instance = instance;
        }

        @SuppressWarnings("unchecked")
        private Class<T> extractType(Class<T> type)
        {
            return (type == null) ? (Class<T>) Object.class : type;
        }

        /**
         * Get the class (or superclass or interface) to search for. If it was
         * not specified in the constructor, <code>Object</code> is used as this
         * will match any instance.
         * <p>
         * @return the class to search for
         */
        public Class<T> getType()
        {
            return type;
        }

        /**
         * Get the persistent identifier being searched for, if any.
         * <p>
         * @return the ID or <code>null</code>
         * <p>
         * @see Lookup.Item#getId
         * <p>
         * @since 1.8
         */
        public String getId()
        {
            return id;
        }

        /**
         * Get the specific instance being searched for, if any. Most useful for
         * finding an <code>Item</code> when the instance is already known.
         * <p>
         * @return the object to find or <code>null</code>
         * <p>
         * @since 1.8
         */
        public T getInstance()
        {
            return instance;
        }

        /* Computes hashcode for this template. The hashcode is cached.
         * @return hashcode
         */
        @Override
        public int hashCode()
        {
            if (hashCode != 0) {
                return hashCode;
            }

            hashCode = ((type == null) ? 1 : type.hashCode()) + ((id == null) ? 2 : id.hashCode())
                    + ((instance == null) ? 3 : 0);

            return hashCode;
        }

        /* Checks whether two templates represent the same query.
         * @param obj another template to check
         * @return true if so, false otherwise
         */
        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof Template)) {
                return false;
            }

            Template t = (Template) obj;

            if (hashCode() != t.hashCode()) {
                // this is an optimalization - the hashCodes should have been
                // precomputed
                return false;
            }

            if (type != t.type) {
                return false;
            }

            if (id == null) {
                if (t.id != null) {
                    return false;
                }
            }
            else {
                if (!id.equals(t.id)) {
                    return false;
                }
            }

            if (instance == null) {
                return (t.instance == null);
            }
            else {
                return instance.equals(t.instance);
            }
        }

        /* for debugging */
        @Override
        public String toString()
        {
            return "Lookup.Template[type=" + type + ",id=" + id + ",instance=" + instance + "]"; // NOI18N
        }
    }

    /**
     * Result of a lookup request. Allows access to all matching instances at
     * once. Also permits listening to changes in the result. Result can contain
     * duplicate items.
     */
    public static abstract class Result<T> extends Object
    {

        /**
         * Registers a listener that is invoked when there is a possible change
         * in this result.
         * <p>
         * @param l the listener to add
         */
        public abstract void addLookupListener(LookupListener l);

        /**
         * Unregisters a listener previously added.
         * <p>
         * @param l the listener to remove
         */
        public abstract void removeLookupListener(LookupListener l);

        /**
         * Get all instances in the result. The return value type should be List
         * instead of Collection, but it is too late to change it.
         * <p>
         * @return unmodifiable collection of all instances that will never
         *         change its content
         */
        public abstract Collection<? extends T> allInstances();

        /**
         * Get all classes represented in the result. That is, the set of
         * concrete classes used by instances present in the result. All
         * duplicate classes will be omitted.
         * <p>
         * @return unmodifiable set of <code>Class</code> objects that will
         *         never change its content
         * <p>
         * @since 1.8
         */
        public Set<Class<? extends T>> allClasses()
        {
            return Collections.emptySet();
        }

        /**
         * Get all registered items. This should include all pairs of instances
         * together with their classes, IDs, and so on. The return value type
         * should be List instead of Collection, but it is too late to change
         * it.
         * <p>
         * @return unmodifiable collection of {@link Lookup.Item} that will
         *         never change its content
         * <p>
         * @since 1.8
         */
        public Collection<? extends Item<T>> allItems()
        {
            return Collections.emptyList();
        }
    }

    /**
     * A single item in a lookup result. This wrapper provides unified access to
     * not just the instance, but its class, a possible persistent identifier,
     * and so on.
     * <p>
     * @since 1.25
     */
    public static abstract class Item<T> extends Object
    {

        /**
         * Get the instance itself.
         * <p>
         * @return the instance or null if the instance cannot be created
         */
        public abstract T getInstance();

        /**
         * Get the implementing class of the instance.
         * <p>
         * @return the class of the item
         */
        public abstract Class<? extends T> getType();

        // XXX can it be null??
        /**
         * Get a persistent indentifier for the item. This identifier should
         * uniquely represent the item within its containing lookup (and if
         * possible within the global lookup as a whole). For example, it might
         * represent the source of the instance as a file name. The ID may be
         * persisted and in a later session used to find the same instance as
         * was encountered earlier, by means of passing it into a lookup
         * template.
         * <p>
         * @return a string ID of the item
         */
        public abstract String getId();

        /**
         * Get a human presentable name for the item. This might be used when
         * summarizing all the items found in a lookup result in some part of a
         * GUI.
         * <p>
         * @return the string suitable for presenting the object to a user
         */
        public abstract String getDisplayName();

        /* show ID for debugging */
        @Override
        public String toString()
        {
            return getId();
        }
    }

    //
    // Implementation of the default lookup
    //
    private static final class Empty extends Lookup
    {

        private static final Result NO_RESULT = new Result()
        {
            public void addLookupListener(LookupListener l)
            {
            }

            public void removeLookupListener(LookupListener l)
            {
            }

            public Collection allInstances()
            {
                return Collections.EMPTY_SET;
            }
        };
        private static final long serialVersionUID = -5899725504531325936L;

        Empty()
        {
        }

        public <T> T lookup(Class<T> clazz)
        {
            return null;
        }

        @SuppressWarnings("unchecked")
        public <T> Result<T> lookup(Template<T> template)
        {
            return NO_RESULT;
        }
    }

}
