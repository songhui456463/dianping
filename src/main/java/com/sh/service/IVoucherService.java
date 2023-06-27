package com.sh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sh.entry.Voucher;

public interface IVoucherService extends IService<Voucher> {

    /**
     * 新增优惠券
     * @param voucher
     */
    void addSeckillVoucher(Voucher voucher);
}
