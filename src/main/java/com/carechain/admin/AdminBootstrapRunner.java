package com.carechain.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    @Autowired
    private AdminProvisioningService adminProvisioningService;

    @Override
    public void run(ApplicationArguments args) {
        adminProvisioningService.ensureBootstrapAdmin();
    }
}
