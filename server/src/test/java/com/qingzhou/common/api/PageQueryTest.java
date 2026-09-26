package com.qingzhou.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageQueryTest {

    @Test
    void clampsPageSizeAndKeyword() {
        PageQuery query = new PageQuery();
        query.setCurrent(0);
        query.setSize(10_000);
        query.setKeyword("  " + "k".repeat(300));
        assertEquals(1, query.getCurrent());
        assertEquals(PageQuery.MAX_SIZE, query.getSize());
        assertEquals(PageQuery.MAX_KEYWORD, query.getKeyword().length());
    }

    @Test
    void blankKeywordIsNullAndInvalidSizeFallsBack() {
        PageQuery query = new PageQuery();
        query.setSize(0);
        query.setKeyword("   ");
        assertEquals(10, query.getSize());
        assertNull(query.getKeyword());
    }
}
