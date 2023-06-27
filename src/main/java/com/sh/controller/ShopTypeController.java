package com.sh.controller;

import com.sh.dto.Result;
import com.sh.entry.ShopType;
import com.sh.exception.BaseErrorInfoInterface;
import com.sh.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    @Resource
    private IShopTypeService shopTypeService;

    @GetMapping("list")
    public Result queryShopType() {
        List<ShopType> typeList = shopTypeService.query()
                .orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
