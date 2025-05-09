/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2018 HEC, Inc. All rights reserved.
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
package mil.army.usace.hec.services.annotations.spi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * One entry in a {@code META-INF/services/*} file.
 * For purposes of collections, all lines with the same impl class are equal,
 * and lines with lower position sort first (else by class name).
 *
 * @author Shannon Newbold (sjnewbold@rmanet.com)
 */
final class ServiceLoaderLine implements Comparable<ServiceLoaderLine>
{

	private static final String POSITION = "#position="; // NOI18N
	private static final String SUPERSEDE = "#-"; // NOI18N

	private final String _impl;
	private final int _position;
	private final String[] _supersedes;

	public ServiceLoaderLine(String impl, int position, String[] supersedes)
	{
		this._impl = impl;
		this._position = position;
		this._supersedes = supersedes;
	}

	public static void parse(Reader r, SortedSet<ServiceLoaderLine> lines) throws IOException
	{
		BufferedReader br = new BufferedReader(r);
		String line;
		String impl = null;
		int position = Integer.MAX_VALUE;
		List<String> supersedes = new ArrayList<>();
		while((line = br.readLine()) != null)
		{
			if(line.startsWith(POSITION))
			{
				position = Integer.parseInt(line.substring(POSITION.length()));
			}
			else if(line.startsWith(SUPERSEDE))
			{
				supersedes.add(line.substring(SUPERSEDE.length()));
			}
			else
			{
				finalizeParse(lines, impl, position, supersedes);
				impl = line;
				position = Integer.MAX_VALUE;
				supersedes.clear();
			}
		}
		finalizeParse(lines, impl, position, supersedes);
	}

	private static void finalizeParse(Set<ServiceLoaderLine> lines, String impl, int position, List<String> supersedes)
	{
		if(impl != null)
		{
			lines.add(new ServiceLoaderLine(impl, position, supersedes.toArray(new String[0])));
		}
	}

	public @Override
	int compareTo(ServiceLoaderLine o)
	{
		if(_impl.equals(o._impl))
		{
			return 0;
		}
		int r = _position - o._position;
		return r != 0 ? r : _impl.compareTo(o._impl);
	}

	public @Override
	boolean equals(Object o)
	{
		return o instanceof ServiceLoaderLine && _impl.equals(((ServiceLoaderLine) o)._impl);
	}

	public @Override
	int hashCode()
	{
		return _impl.hashCode();
	}

	public void write(PrintWriter w)
	{
		w.println(_impl);
		if(_position != Integer.MAX_VALUE)
		{
			w.println(POSITION + _position);
		}
		for(String exclude : _supersedes)
		{
			w.println(SUPERSEDE + exclude);
		}
	}

}
