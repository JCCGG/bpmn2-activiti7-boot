package cn.jcc.entity.query;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 查询流程定义中的用户任务
 **/

@Data
@ToString
public class BpmTaskModelQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 按照id查询
     */
    private String defineId;

    /**
     * 用户任务定义的key
     */
    private String taskDefKey;

    /**
     * 按照任务分类精确查询
     */
    private String category;

    /**
     * 任务的操作类型  提交还是审核 对应画图中的 任务类型 “COMMIT  AUDIT”
     */
    private String nodeType;
}
