package com.yonerp.po.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应体
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records = new ArrayList<>();

    private long total;

    private long pageNo;

    private long pageSize;

    private long pages;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNo(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    public static <S, T> PageResult<T> of(IPage<S> page, Function<S, T> converter) {
        PageResult<T> result = new PageResult<>();
        List<T> records = new ArrayList<>();
        for (S source : page.getRecords()) {
            records.add(converter.apply(source));
        }
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNo(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    public static <T> PageResult<T> empty(long pageNo, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }
}
