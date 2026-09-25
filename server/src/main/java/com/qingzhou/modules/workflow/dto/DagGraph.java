package com.qingzhou.modules.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DagGraph {

    private List<DagNode> nodes = new ArrayList<>();
    private List<DagEdge> edges = new ArrayList<>();
}
