package com.deepoove.dubbo.provider.springboot.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author dengshangyu
 * @Description
 * @date 2021年01月14日
 */
@RestController
@Api(tags = "用户接口")
public class UserController {


    @GetMapping("/save")
    @ApiOperation("保存")
    public void save(){
        System.out.println("save.....");
    }
}
