package com.sh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;

    /**
     * 成功请求时不返回数据(用于增加、删除、更新)
     * @return
     */
    public static Result ok() {
        return new Result(true, null, null, null);
    }

    /**
     * 成功请求时返回数据(用于查询指定数据)
     * @return
     */
    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }

    /**
     * 成功请求时返回数据(用于查询一组数据)
     * @return
     */
    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total);
    }


    /**
     * 请求失败时返回错误信息
     * @return
     */
    public static Result fail(String errorMsg) {
        return new Result(false, errorMsg, null, null);
    }

}
