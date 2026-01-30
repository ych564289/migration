package com.example.migration.controller;

import com.example.migration.dao.master.entity.SysUser;
import com.example.migration.dao.slave.entity.GenTable;
import com.example.migration.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController()
@RequestMapping("/userInfo")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    @GetMapping("/getUserInfoList")
    public List<SysUser> getUserInfoList() {
        return sysUserService.getUserInfoList();
    }

    @GetMapping("/getTableList")
    public List<GenTable> getTableList() {
        return sysUserService.getTableList();
    }

}
