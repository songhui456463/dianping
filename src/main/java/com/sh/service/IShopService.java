package com.sh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sh.dto.Result;
import com.sh.entry.Shop;

public interface IShopService extends IService<Shop> {

    /**
     * 根据店铺id查询
     * @param id 店铺id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商店信息
     * @param shop 商店
     * @return
     */
    Result update(Shop shop);


    /**
     * 查询附近商铺
     * @param typeId 商铺类型id
     * @param current 页数
     * @param x 经度
     * @param y 维度
     * @return
     */
    Result queryShopByType(Long typeId, Integer current, Double x, Double y);
}
