package hec.services;

import mil.army.usace.hec.services.annotations.ServiceProvider;

@ServiceProvider(service = OtherTestServiceProvider.class, position = 0)
public class OtherTestServiceProviderImplHello implements OtherTestServiceProvider {

    @Override
    public String getValue() {
        return "hello";
    }
    
}
