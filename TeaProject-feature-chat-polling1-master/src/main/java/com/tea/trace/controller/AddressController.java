package com.tea.trace.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tea.trace.entity.TeaAddress;
import com.tea.trace.entity.TeaUser;
import com.tea.trace.mapper.AddressMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    @Autowired
    private AddressMapper addressMapper;

    @GetMapping("/list")
    public List<TeaAddress> list(HttpSession session) {
        TeaUser user = getCurrentUser(session);
        if (user == null) {
            return List.of();
        }
        return addressMapper.selectList(new LambdaQueryWrapper<TeaAddress>()
                .eq(TeaAddress::getUserId, user.getId())
                .orderByDesc(TeaAddress::getIsDefault)
                .orderByDesc(TeaAddress::getCreateTime));
    }

    @GetMapping("/default")
    public TeaAddress getDefault(HttpSession session) {
        TeaUser user = getCurrentUser(session);
        if (user == null) {
            return null;
        }
        return addressMapper.selectOne(new LambdaQueryWrapper<TeaAddress>()
                .eq(TeaAddress::getUserId, user.getId())
                .eq(TeaAddress::getIsDefault, 1)
                .last("limit 1"));
    }

    @PostMapping("/save")
    public String save(@RequestBody TeaAddress address, HttpSession session) {
        TeaUser user = getCurrentUser(session);
        if (user == null) {
            return "请先登录";
        }
        if (!StringUtils.hasText(address.getReceiverName())
                || !StringUtils.hasText(address.getReceiverPhone())
                || !StringUtils.hasText(address.getProvince())
                || !StringUtils.hasText(address.getCity())
                || !StringUtils.hasText(address.getDistrict())
                || !StringUtils.hasText(address.getDetail())) {
            return "地址信息不完整";
        }

        if (address.getId() == null) {
            address.setUserId(user.getId());
            address.setCreateTime(LocalDateTime.now());
        } else {
            TeaAddress dbAddress = addressMapper.selectById(address.getId());
            if (dbAddress == null || !dbAddress.getUserId().equals(user.getId())) {
                return "地址不存在";
            }
            address.setUserId(user.getId());
            address.setCreateTime(dbAddress.getCreateTime());
        }

        if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }

        if (address.getIsDefault() == 1) {
            clearDefaultAddress(user.getId());
        } else {
            Long count = addressMapper.selectCount(new LambdaQueryWrapper<TeaAddress>()
                    .eq(TeaAddress::getUserId, user.getId()));
            if (count == 0) {
                address.setIsDefault(1);
            }
        }

        if (address.getId() == null) {
            addressMapper.insert(address);
        } else {
            addressMapper.updateById(address);
        }
        return "SUCCESS";
    }

    @PostMapping("/setDefault")
    public String setDefault(@RequestParam Long id, HttpSession session) {
        TeaUser user = getCurrentUser(session);
        if (user == null) {
            return "请先登录";
        }
        TeaAddress address = addressMapper.selectById(id);
        if (address == null || !address.getUserId().equals(user.getId())) {
            return "地址不存在";
        }
        clearDefaultAddress(user.getId());
        address.setIsDefault(1);
        addressMapper.updateById(address);
        return "SUCCESS";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, HttpSession session) {
        TeaUser user = getCurrentUser(session);
        if (user == null) {
            return "请先登录";
        }
        TeaAddress address = addressMapper.selectById(id);
        if (address == null || !address.getUserId().equals(user.getId())) {
            return "地址不存在";
        }
        addressMapper.deleteById(id);

        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            TeaAddress latest = addressMapper.selectOne(new LambdaQueryWrapper<TeaAddress>()
                    .eq(TeaAddress::getUserId, user.getId())
                    .orderByDesc(TeaAddress::getCreateTime)
                    .last("limit 1"));
            if (latest != null) {
                latest.setIsDefault(1);
                addressMapper.updateById(latest);
            }
        }
        return "SUCCESS";
    }

    private void clearDefaultAddress(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<TeaAddress>()
                .eq(TeaAddress::getUserId, userId)
                .set(TeaAddress::getIsDefault, 0));
    }

    private TeaUser getCurrentUser(HttpSession session) {
        return (TeaUser) session.getAttribute("user");
    }
}
