package hec.services;

import mil.army.usace.hec.services.annotations.ServiceProvider;

@ServiceProvider(service = TestServiceProvider.class, position = 0)
public class TestServiceProviderImplForOne implements TestServiceProvider {

    @Override
    public int getTestValue() {
        return 1;
    }
    
}
