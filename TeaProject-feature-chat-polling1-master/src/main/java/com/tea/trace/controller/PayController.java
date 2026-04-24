package com.tea.trace.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tea.trace.entity.TeaOrder;
import com.tea.trace.entity.TeaUser;
import com.tea.trace.mapper.OrderMapper;
import com.tea.trace.mapper.UserMapper;
import com.tea.trace.service.impl.TeaServiceImpl;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;
import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/pay")
public class PayController {

    @Autowired private UserMapper userMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private TeaServiceImpl teaService;

    @PostMapping("/balance")
    @Transactional
    public String balancePay(@RequestParam String orderNo, HttpSession session) {
        TeaUser user = (TeaUser) session.getAttribute("user");
        if (user == null) {
            return "请先登录";
        }

        // 核心改造：支持单订单或多订单(逗号分隔)的合并支付
        List<String> orderNoList = Arrays.asList(orderNo.split(","));
        List<TeaOrder> orders = orderMapper.selectList(new QueryWrapper<TeaOrder>().in("order_no", orderNoList));
        if (orders == null || orders.isEmpty()) {
            return "订单不存在";
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (TeaOrder order : orders) {
            if (order.getStatus() != 0) {
                return "包含无效或已支付订单";
            }
            totalAmount = totalAmount.add(order.getAmount()); // 累加总金额
        }

        TeaUser dbUser = userMapper.selectById(user.getId());
        BigDecimal balance = dbUser.getBalance() == null ? BigDecimal.ZERO : dbUser.getBalance();

        if (balance.compareTo(totalAmount) < 0) {
            return "余额不足，请先前往个人中心充值";
        }

        // 一次性扣除总款项
        UpdateWrapper<TeaUser> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", user.getId()).setSql("balance = balance - " + totalAmount);
        userMapper.update(null, updateWrapper);

        // 批量更新子订单状态
        for (TeaOrder order : orders) {
            order.setStatus(1);
            order.setPayType("BALANCE");
            order.setExpressInfo(buildInitialExpressInfo(order));
            orderMapper.updateById(order);
        }

        TeaUser latestUser = userMapper.selectById(user.getId());
        user.setBalance(latestUser.getBalance());
        session.setAttribute("user", user);

        return "SUCCESS";
    }

    @PostMapping("/sandbox")
    @Transactional
    public String sandboxPay(@RequestParam String orderNo, HttpSession session) {
        TeaUser user = (TeaUser) session.getAttribute("user");
        if (user == null || !"TEST_USER".equals(user.getRole())) {
            return "无权使用沙箱支付";
        }

        List<String> orderNoList = Arrays.asList(orderNo.split(","));
        List<TeaOrder> orders = orderMapper.selectList(new QueryWrapper<TeaOrder>().in("order_no", orderNoList));
        if (orders == null || orders.isEmpty()) {
            return "订单不存在";
        }

        for (TeaOrder order : orders) {
            if (order.getStatus() != 0) {
                return "包含无效订单";
            }
            order.setStatus(1);
            order.setIsSandbox(1);
            order.setPayType("SANDBOX");
            order.setExpressInfo(buildInitialExpressInfo(order));
            orderMapper.updateById(order);
            teaService.releaseStock(order.getTeaId(), order.getCount());
        }
        return "SUCCESS";
    }

    private String buildInitialExpressInfo(TeaOrder order) {
        String logisticsCompany = "顺丰速运";
        String trackingNum = "SF" + System.currentTimeMillis();
        String origin = "福建省武夷山市原产地发货仓";
        String destination = order.getAddressSnapshot();
        if (destination == null || destination.isBlank()) {
            destination = "默认收货地址";
        }
        String estTime = LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String receiver = order.getReceiverName() == null ? "收货人" : order.getReceiverName();
        return String.format("【%s】单号：%s%n[发件] %s%n[收件] %s %s%n[预计送达] %s%n[当前状态] 待发货",
                logisticsCompany, trackingNum, origin, receiver, destination + " " + (order.getReceiverPhone() == null ? "" : order.getReceiverPhone()), estTime);
    }
}
