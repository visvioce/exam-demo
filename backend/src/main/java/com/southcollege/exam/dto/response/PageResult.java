package com.southcollege.exam.dto.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页响应结果
 */
@Data
public class PageResult<T> {

    /**
     * 当前页码
     */
    private Integer current;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    /**
     * 从 MyBatis-Plus 的 IPage 转换
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setCurrent((int) page.getCurrent());
        result.setSize((int) page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords());
        result.setHasNext(page.getCurrent() < page.getPages());
        result.setHasPrevious(page.getCurrent() > 1);
        return result;
    }

    /**
     * 创建空分页结果
     */
    public static <T> PageResult<T> empty(Integer current, Integer size) {
        PageResult<T> result = new PageResult<>();
        result.setCurrent(current);
        result.setSize(size);
        result.setTotal(0L);
        result.setPages(0L);
        result.setRecords(List.of());
        result.setHasNext(false);
        result.setHasPrevious(current > 1);
        return result;
    }

    /**
     * 将 PageResult 的 records 列表通过 mapper 函数转换为另一种类型的分页结果，
     * 复用原有的分页元数据（total、pages、current、size、hasNext、hasPrevious）。
     *
     * @param source 源分页结果
     * @param mapper 将源类型列表映射为目标类型列表的函数
     * @param <T>    源类型
     * @param <R>    目标类型
     * @return 转换后的分页结果
     */
    public static <T, R> PageResult<R> map(PageResult<T> source, java.util.function.Function<List<T>, List<R>> mapper) {
        if (source == null) {
            return PageResult.empty(1, 10);
        }
        PageResult<R> result = new PageResult<>();
        result.setRecords(mapper.apply(source.getRecords()));
        result.setTotal(source.getTotal());
        result.setSize(source.getSize());
        result.setCurrent(source.getCurrent());
        result.setPages(source.getPages());
        result.setHasNext(source.getHasNext());
        result.setHasPrevious(source.getHasPrevious());
        return result;
    }
}
