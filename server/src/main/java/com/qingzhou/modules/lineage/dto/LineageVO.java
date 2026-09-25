package com.qingzhou.modules.lineage.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LineageVO {

    private String type;
    private LineageRefVO self;
    private List<LineageRefVO> components = new ArrayList<>();
    private List<LineageRefVO> workflows = new ArrayList<>();
    private List<LineageRefVO> schedules = new ArrayList<>();
    private List<LineageRefVO> openapiApps = new ArrayList<>();
}
