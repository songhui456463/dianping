package com.sh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sh.dto.Result;
import com.sh.entry.Follow;

public interface IFollowService extends IService<Follow> {


    /**
     * 查询当前登录用户是否已经关注点评户主
     * @param followUserId 当前点评户主
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 关注笔记作者
     * @param followUserId 笔记作者id
     * @param isFollow 是否已经关注1
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询当前用户和作者的公共关注
     * @param followUserId 作者id
     * @return
     */
    Result followCommons(Long followUserId);
}
