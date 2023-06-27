package com.sh.controller;

import com.sh.dto.Result;
import com.sh.service.IVoucherOrderService;
import com.sh.utils.LimitType;
import com.sh.utils.RateLimiter;
import com.sh.utils.VisitCount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    //    @VisitCount(name = "seckillVoucher")
//    @RateLimiter(time = 3, count = 200)
//    @Retryable()
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

}
