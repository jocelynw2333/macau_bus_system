package com.albez.bus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "bus.refresh-on-startup.enabled=false")
class BusApplicationTests {

    @Test
    void contextLoads() {
    }

}
