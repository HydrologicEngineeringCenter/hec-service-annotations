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
 * Software is Sun Microsystems, Inc.
 *
 * Portions Copyrighted 2006 Sun Microsystems, Inc.
 */

package mil.army.usace.hec.util.lookup.implspi;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import mil.army.usace.hec.util.lookup.Lookup;
import mil.army.usace.hec.util.lookup.Lookups;

/** Infrastructure provider interface for those who control the overall
 * registration of services in the system. The first instance of this interface
 * found in {@link Lookup#getDefault()} is consulted when providing answers
 * to {@link Lookups#forPath(java.lang.String)} queries. Current implementation
 * is not ready for multiple instances of this interface (the first one wins)
 * and also changing the instances during runtime.
 *
 * <div class="nonnormative">
 * The basic implementation of this interface is provided in
 * <a href="@org-openide-filesystems@/overview-summary.html">Filesystem API</a>
 * and recognizes the 
 * <a href="@org-openide-util@/org/openide/util/doc-files/api.html#instance-folders">.instance files</a>
 * registered in XML layers. As such one can rely on
 * <a href="@org-openide-util@/org/openide/util/doc-files/api.html#instance-folders">.instance files</a>
 * being recognized in unit tests, if the
 * <a href="@org-openide-filesystems@/overview-summary.html">Filesystem API</a>
 * is included.
 * The implementation
 * is then refined in
 * <a href="@org-netbeans-modules-settings@/overview-summary.html">Settings API</a>
 * to handle also <a href="@org-openide-util@/org/openide/util/doc-files/api.html#settings">.settings files</a>.
 * Again, including this module in unit tests
 * ensures 
 * <a href="@org-openide-util@/org/openide/util/doc-files/api.html#settings">.settings files</a>
 * files are recognized.
 * </div>
 *
 * @author Jaroslav Tulach
 * @since 8.1
 */
public abstract class NamedServicesProvider {
    private static final Map<String,Reference<Lookup>> namedServicesProviders = Collections.synchronizedMap(new HashMap<String,Reference<Lookup>>());

    public static Lookup forPath(String path, ClassLoader classLoader) {

        Reference<Lookup> ref = namedServicesProviders.get(path);
        Lookup lkp = ref == null ? null : ref.get();
        if (lkp != null) {
            return lkp;
        }
        NamedServicesProvider prov = Lookup.getDefault().lookup(NamedServicesProvider.class);
        if (prov != null &&
            /* avoid stack overflow during initialization */
            !path.startsWith(
                "URLStreamHandler/"
                /*URLStreamHandlerRegistrationProcessor.REGISTRATION_PREFIX*/
            )
        ) {
            lkp = prov.create(path);
        } else {
            ClassLoader l = classLoader;
            if (l == null) {
                l = Thread.currentThread().getContextClassLoader();
                if (l == null) {
                    l = NamedServicesProvider.class.getClassLoader();
                }
            }
            lkp = Lookups.metaInfServices(l, "META-INF/namedservices/" + path);
        }

        namedServicesProviders.put(path, new WeakReference<Lookup>(lkp));
        return lkp;
    }
    
    public static Lookup forPath(String path)
    {
	ClassLoader classLoader = Lookup.getDefault().lookup(ClassLoader.class);
	return forPath(path, classLoader);
    }

    /** Throws an exception. Prevents unwanted instantiation of this class
     * by unknown subclasses.
     */
    protected NamedServicesProvider() {
        if (getClass().getName().equals("org.openide.util.lookup.PathInLookupTest$P")) { // NOI18N
            // OK for tests
            return;
        }
        if (getClass().getName().equals("org.openide.util.UtilitiesTest$NamedServicesProviderImpl")) { // NOI18N
            // OK for tests
            return;
        }
        if (getClass().getName().equals("org.netbeans.modules.openide.filesystems.RecognizeInstanceFiles")) { // NOI18N
            // OK for openide.filesystems
            return;
        }
        if (getClass().getName().equals("org.netbeans.modules.settings.RecognizeInstanceObjects")) { // NOI18N
            // OK for settings
            return;
        }
        throw new IllegalStateException();
    }

    /** Create the lookup for given path. Called as a result of query to
     * {@link Lookups#forPath(java.lang.String)}.
     *
     * @param path the identification of the path
     * @return the lookup representing objects in this path.
     */
    protected abstract Lookup create(String path);
    
}
