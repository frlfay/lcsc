package com.lcsc.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class SimpleController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, LCSC Crawler System!";
    }

    @GetMapping("/status")
    public String status() {
        return "System is running!";
    }
}
