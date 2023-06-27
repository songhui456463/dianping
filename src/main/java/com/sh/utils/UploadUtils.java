package com.sh.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;

import java.io.File;

public class UploadUtils {

    /**
     * 生成文件名
     * @param originalFileName 原本文件名
     * @return
     */
    public static String createNewFile(String originalFileName) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFileName, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        // 一级目录
        int d1 = hash & 0xF;
        // 二级目录
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生产文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }


    public static void main(String[] args) {
        StringBuffer str = new StringBuffer("123");
        str.append("sh");
    }
}
