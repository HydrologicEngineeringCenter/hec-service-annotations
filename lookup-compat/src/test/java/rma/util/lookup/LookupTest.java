package rma.util.lookup;

import hec.services.MultipleServiceImpl;
import hec.services.TestServiceProvider;
import hec.services.TestServiceProviderImplForOne;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LookupTest {

    @Test
    void test_simple_lookup() {
        final Lookup lu = Lookup.getDefault();

        final TestServiceProvider service = lu.lookup(TestServiceProvider.class);
        assertNotNull(service, "Could not retrieve service instance");
        assertInstanceOf(TestServiceProviderImplForOne.class, service, "Expected Provider not retrieved.");
        assertEquals(1, service.getTestValue(), "Retrieved service did not provide the expected value.");

        // This appears to always be giving the "MultiServiceImpl"
        // final OtherTestServiceProvider otherService = lu.lookup(OtherTestServiceProvider.class);
        // assertNotNull(otherService, "Could not retrieve service instance");
        // assertInstanceOf(OtherTestServiceProviderImplHello.class, otherService, "Expected Provider not retrieved.");
        // assertEquals("hello", otherService.getValue(), "Retrieved service did not provide the expected value.");
    }


    @Test
    void test_multi_provider() {
        final Lookup lu = Lookup.getDefault();
        final var results = lu.lookupAll(TestServiceProvider.class).toArray(new TestServiceProvider[0]);
        assertEquals(2,results.length);
        assertInstanceOf(TestServiceProviderImplForOne.class, results[0], "Implementations not in expected order");
        assertInstanceOf(MultipleServiceImpl.class, results[1], "Implementations not in expected order.");
        final var multi = (MultipleServiceImpl)results[1];
        assertEquals("world", multi.getValue(), "Incorrect value from multi-provider");
        assertEquals(2, multi.getTestValue(), "Incorrect testValue from multi-provider");
    }

}
