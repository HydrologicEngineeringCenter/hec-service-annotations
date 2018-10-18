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
 * @author Shannon Newbold (sjnewbold@rmanet.com)
 */
final class ServiceLoaderLine implements Comparable<ServiceLoaderLine> {

    private static final String POSITION = "#position="; // NOI18N
    private static final String SUPERSEDE = "#-"; // NOI18N

    private final String impl;
    private final int position;
    private final String[] supersedes;

    public ServiceLoaderLine(String impl, int position, String[] supersedes) {
        this.impl = impl;
        this.position = position;
        this.supersedes = supersedes;
    }

    public @Override int compareTo(ServiceLoaderLine o) {
        if (impl.equals(o.impl)) {
            return 0;
        }
        int r = position - o.position;
        return r != 0 ? r : impl.compareTo(o.impl);
    }

    public @Override boolean equals(Object o) {
        return o instanceof ServiceLoaderLine && impl.equals(((ServiceLoaderLine) o).impl);
    }

    public @Override int hashCode() {
        return impl.hashCode();
    }

    public void write(PrintWriter w) {
        w.println(impl);
        if (position != Integer.MAX_VALUE) {
            w.println(POSITION + position);
        }
        for (String exclude : supersedes) {
            w.println(SUPERSEDE + exclude);
        }
    }

    public static void parse(Reader r, SortedSet<ServiceLoaderLine> lines) throws IOException {
        BufferedReader br = new BufferedReader(r);
        String line;
        String impl = null;
        int position = Integer.MAX_VALUE;
        List<String> supersedes = new ArrayList<String>();
        while ((line = br.readLine()) != null) {
            if (line.startsWith(POSITION)) {
                position = Integer.parseInt(line.substring(POSITION.length()));
            } else if (line.startsWith(SUPERSEDE)) {
                supersedes.add(line.substring(SUPERSEDE.length()));
            } else {
                finalize(lines, impl, position, supersedes);
                impl = line;
                position = Integer.MAX_VALUE;
                supersedes.clear();
            }
        }
        finalize(lines, impl, position, supersedes);
    }
    private static void finalize(Set<ServiceLoaderLine> lines, String impl, int position, List<String> supersedes) {
        if (impl != null) {
            lines.add(new ServiceLoaderLine(impl, position, supersedes.toArray(new String[supersedes.size()])));
        }
    }

}
