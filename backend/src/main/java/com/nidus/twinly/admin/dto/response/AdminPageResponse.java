package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminPageResult;

import java.util.List;
import java.util.function.Function;

public record AdminPageResponse<T>(
        List<T> items,
        Integer page,
        Integer size,
        Long totalCount
) {

    public static <R, T> AdminPageResponse<T> from(AdminPageResult<R> result, Function<R, T> mapper) {
        return new AdminPageResponse<>(result.items().stream().map(mapper).toList(), result.page(), result.size(), result.totalCount());
    }
}
