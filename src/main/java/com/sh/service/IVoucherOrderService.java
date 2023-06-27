package com.sh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sh.dto.Result;
import com.sh.entry.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 在redis中预减库存
     * @param voucherId 优惠券id
     * @return
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 从数据库中创建订单(异步)
     * @param voucherOrder 优惠券信息
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
