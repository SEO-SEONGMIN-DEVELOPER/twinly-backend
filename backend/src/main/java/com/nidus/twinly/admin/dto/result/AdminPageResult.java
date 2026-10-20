package com.nidus.twinly.admin.dto.result;

import java.util.List;
import java.util.function.Function;

public record AdminPageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalCount
) {

    public <R> AdminPageResult<R> map(Function<T, R> mapper) {
        return new AdminPageResult<>(items.stream().map(mapper).toList(), page, size, totalCount);
    }
}
