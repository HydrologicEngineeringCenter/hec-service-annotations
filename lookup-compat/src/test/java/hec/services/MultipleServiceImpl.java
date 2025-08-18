package hec.services;

import mil.army.usace.hec.services.annotations.ServiceProvider;
import mil.army.usace.hec.services.annotations.ServiceProviders;

@ServiceProviders(
    {
        @ServiceProvider(service = TestServiceProvider.class, position = 200),
        @ServiceProvider(service = OtherTestServiceProvider.class, position = 200, path = "OTHER")
    }
)
public class MultipleServiceImpl implements TestServiceProvider, OtherTestServiceProvider{

    @Override
    public String getValue() {
        return "world";
    }

    @Override
    public int getTestValue() {
        return 2;
    }
    
}
