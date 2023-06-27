package com.sh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sh.dto.Result;
import com.sh.entry.Blog;

public interface IBlogService extends IService<Blog> {

    /**
     * 查询热评
     * @param current 当前页
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 保存点评
     * @param blog 点评
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 根据博客id查询点评
     * @param id 博客id
     * @return
     */
    Result queryById(Long id);

    /**
     * 点赞点评
     * @param id 点评id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询点评的用户
     * @param id 点评id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     *
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
