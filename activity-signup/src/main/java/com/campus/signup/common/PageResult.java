package com.campus.signup.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页返回体。
 *
 * <p>对外页码统一 <b>从 1 开始</b>，和前端习惯一致；Spring Data 的 {@code Page#getNumber()} 是从 0 开始的，
 * 转换在 {@link #from(Page)} 里做掉，别再让每个 controller 各自 ±1。
 */
public record PageResult<T>(List<T> list, long total, int page, int size, int totalPages) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResult<>(list == null ? List.of() : list, total, page, size, totalPages);
    }

    public static <T> PageResult<T> from(Page<T> page) {
        return of(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    /** 空结果，用于「查了但没有」，避免前端拿到 null 还要判空。 */
    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), 0, page, size);
    }
}
