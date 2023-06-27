package com.sh.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sh.dto.Result;
import com.sh.entry.Shop;
import com.sh.service.IShopService;
import com.sh.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    private IShopService shopService;


    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Long typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("x") Double x,
            @RequestParam("y") Double y
    ) {
       return  shopService.queryShopByType(typeId, current, x, y);
    }

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

}
