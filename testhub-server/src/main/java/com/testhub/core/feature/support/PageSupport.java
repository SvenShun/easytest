package com.testhub.core.feature.support;

import com.testhub.common.web.PageResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Maps Spring Data pages to the contract envelope. */
public final class PageSupport {

    private PageSupport() {
    }

    public static <T, R> PageResponse<R> of(Page<T> page, Function<T, R> mapper) {
        List<R> items = page.getContent().stream().map(mapper).toList();
        return PageResponse.of(items, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public static int pageIndex(Integer page) {
        return page == null || page < 1 ? 0 : page - 1;
    }

    public static int pageSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 200);
    }
}
