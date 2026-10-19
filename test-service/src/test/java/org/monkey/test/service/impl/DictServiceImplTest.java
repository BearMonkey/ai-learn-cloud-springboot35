package org.monkey.test.service.impl;

import org.junit.jupiter.api.Test;
import org.monkey.test.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DictServiceImplTest {

    @Autowired
    private DictService dictService;

    @Test
    void insert100W() {
        dictService.insert100W();
    }
}