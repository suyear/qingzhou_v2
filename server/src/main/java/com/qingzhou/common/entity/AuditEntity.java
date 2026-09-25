package com.qingzhou.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AuditEntity extends BaseEntity {

    private String createBy;

    private String updateBy;

    /** 仅标注在有 deleted 列的表上；不要配成 MP 全局字段，否则无该列的表会拼错误 SQL */
    @TableLogic
    private Integer deleted;
}
