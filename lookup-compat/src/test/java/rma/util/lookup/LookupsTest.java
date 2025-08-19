package rma.util.lookup;

import hec.services.MultipleServiceImpl;
import hec.services.TestServiceProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LookupsTest {

    @Test
    void testLookupsSingleton() {
        MultipleServiceImpl toLookup = new MultipleServiceImpl();
        Lookup singleton = Lookups.singleton(toLookup);
        assertEquals(toLookup, singleton.lookup(TestServiceProvider.class));
    }

    @Test
    void testLookupsFixed() {
        MultipleServiceImpl toLookup = new MultipleServiceImpl();
        Lookup fixed = Lookups.fixed(toLookup);
        assertEquals(toLookup, fixed.lookup(TestServiceProvider.class));
    }
}
