package com.intuit.fileUploadDemo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/")
public class DemoController {

    @GetMapping
    public ResponseEntity<Map<String,String>> initialMessage()
    {
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("msg","server is running"));
    }
}
