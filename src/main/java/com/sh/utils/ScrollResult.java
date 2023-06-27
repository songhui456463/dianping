package com.sh.utils;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {

    private List<?> list;
    // 下一次查询的最大值(时间戳)
    private Long minTime;
    // 下一次查询的偏移量
    private Integer offset;
}
