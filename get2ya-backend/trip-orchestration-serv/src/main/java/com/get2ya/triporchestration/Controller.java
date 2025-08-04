// filepath: get2ya-backend/trip-orchestration-serv/src/main/java/com/get2ya/orchestration/HelloController.java
package com.get2ya.triporchestration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {
    @GetMapping("/")
    public String hello() {
        return "Hello from trip-orchestration-serv!";
    }
}