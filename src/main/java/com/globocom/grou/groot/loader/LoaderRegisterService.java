package com.globocom.grou.groot.loader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class LoaderRegisterService {

    private final LoaderService loaderService;

    @Autowired
    public LoaderRegisterService(LoaderService loaderService) {
        this.loaderService = loaderService;
    }

    @Scheduled(fixedRate = 10000)
    public void register() {
        loaderService.updateStatusKey();
        loaderService.checkAbortNow();
    }
}