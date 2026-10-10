package com.zhishuiyun.water.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 通用分页结果，对齐前端 PageResult<T>。
 */
@Getter
@AllArgsConstructor
public class PageResult<T> {
    private final List<T> items;
    private final long total;
    private final int page;
    private final int pageSize;

    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        return new PageResult<>(items, total, page, pageSize);
    }
}
