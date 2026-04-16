package com.premierleague.server.dto;

import java.util.List;

/**
 * 分页结果
 */
public record PageResult<T>(
        List<T> list,
        int page,
        int pageSize,
        long total
) {
    public static <T> PageResult<T> of(List<T> list, int page, int pageSize, long total) {
        return new PageResult<>(list, page, pageSize, total);
    }
    
    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(List.of(), page, pageSize, 0);
    }
}
