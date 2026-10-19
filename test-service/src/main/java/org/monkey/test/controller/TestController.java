package org.monkey.test.controller;

import org.monkey.common.core.dto.Result;
import org.monkey.test.model.Dict;
import org.monkey.test.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private DictService dictService;
    @RequestMapping("/all")
    public Result<List<Dict>> getAll() {
        return dictService.getAll();
    }
}
