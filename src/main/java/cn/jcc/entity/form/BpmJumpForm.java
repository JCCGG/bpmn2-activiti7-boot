package cn.jcc.entity.form;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 跳转参数表单
 **/
@Data
public class BpmJumpForm implements Serializable {
    private static final long serialVersionUID=1l;
    /**
     * 当前任务节点
     */
    private String taskId;

    /**
     * 目标任务节点key,为多个的时候必须是同一个并行网关内的节点
     */
    private List<String> targetTaskDefineKes;

    /**
     * 原因
     */
    private String comment;
}
