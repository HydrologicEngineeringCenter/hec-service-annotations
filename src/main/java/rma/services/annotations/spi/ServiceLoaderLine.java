/*
 * Copyright (c) 2018.  Hydrologic Engineering Center (HEC).
 * United States Army Corps of Engineers
 * All Rights Reserved.  HEC PROPRIETARY/CONFIDENTIAL.
 * Source may not be released without written approval
 * from HEC
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package rma.services.annotations.spi;

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
