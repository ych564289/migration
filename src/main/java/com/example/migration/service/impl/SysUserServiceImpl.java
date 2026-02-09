package com.example.migration.service.impl;

import com.example.migration.dao.master.entity.SpCashBalanceClosingAsAt;
import com.example.migration.dao.master.entity.SysUser;
import com.example.migration.dao.master.mapper.SpCashBalanceClosingAsAtMapper;
import com.example.migration.dao.master.mapper.SysUserMapper;
import com.example.migration.dao.slave.entity.GenTable;
import com.example.migration.dao.slave.mapper.GenTableMapper;
import com.example.migration.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private GenTableMapper genTableMapper;

    @Autowired
    private SpCashBalanceClosingAsAtMapper spCashBalanceClosingAsAtMapper;

    @Override
    public List<SysUser> getUserInfoList() {
        return sysUserMapper.selectByExample( null);
    }

    @Override
    public List<GenTable> getTableList() {
        return genTableMapper.selectByExample( null);
    }

    @Override
    public List<SpCashBalanceClosingAsAt> querySpCashBalanceClosingAsAt() {
        return spCashBalanceClosingAsAtMapper.querySpCashBalanceClosingAsAt();
    }
}
