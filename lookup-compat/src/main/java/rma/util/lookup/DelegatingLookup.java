package rma.util.lookup;

import java.util.Collection;

@Deprecated
class DelegatingLookup extends Lookup {

    private org.openide.util.Lookup delegate;

    @Deprecated(forRemoval = true, since = "3.0.0")
    DelegatingLookup(org.openide.util.Lookup delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T lookup(Class<T> clazz) {
        return delegate.lookup(clazz);
    }

    @Override
    public <T> Collection<? extends T> lookupAll(Class<T> clazz) {
        return delegate.lookupAll(clazz);
    }

}
