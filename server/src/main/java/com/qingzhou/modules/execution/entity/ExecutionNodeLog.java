package com.qingzhou.modules.execution.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("qz_execution_node_log")
public class ExecutionNodeLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private String nodeId;
    private String nodeName;
    private Long componentId;
    private String componentCode;
    private String status;
    private String requestUrl;
    private String requestMethod;
    private String requestHeaders;
    private String requestBody;
    private Integer responseStatus;
    private String responseBody;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
