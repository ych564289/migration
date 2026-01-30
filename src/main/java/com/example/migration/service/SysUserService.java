package com.example.migration.service;

import com.example.migration.dao.master.entity.SysUser;
import com.example.migration.dao.slave.entity.GenTable;

import java.util.List;

public interface SysUserService {

    List<SysUser> getUserInfoList();

    List<GenTable> getTableList();

}
