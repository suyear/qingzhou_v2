package com.qingzhou.modules.dashboard.dto;

import lombok.Data;

@Data
public class NamedCountVO {

    private String name;
    private String label;
    private Long count;
}
