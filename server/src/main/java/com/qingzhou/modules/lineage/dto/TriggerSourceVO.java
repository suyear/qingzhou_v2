package com.qingzhou.modules.lineage.dto;

import lombok.Data;

@Data
public class TriggerSourceVO {

    private String type;
    private String label;
    private Long id;
    private String name;
    private String extra;
    private boolean inferred;
}
