package com.learn.aiintelligenttourism.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class BodyController {

    @GetMapping
    public String healthChheck(){
        return "OK";
    }

}