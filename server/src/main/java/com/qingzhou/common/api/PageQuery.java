package com.qingzhou.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQuery {

    public static final long MAX_SIZE = 200;
    public static final int MAX_KEYWORD = 128;

    @Min(1)
    private long current = 1;

    @Min(1)
    @Max(MAX_SIZE)
    private long size = 10;

    private String keyword;

    /** 绑定后收口，避免绕过校验注解把分页打到很大。前端最大请求为 200。 */
    public long getCurrent() {
        return current < 1 ? 1 : current;
    }

    public long getSize() {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, MAX_SIZE);
    }

    public String getKeyword() {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_KEYWORD ? trimmed : trimmed.substring(0, MAX_KEYWORD);
    }
}
