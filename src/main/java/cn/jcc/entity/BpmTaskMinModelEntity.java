package cn.jcc.entity;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 流程图中某个用户任务节点
 **/
@Data
@ToString
public class BpmTaskMinModelEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前任务对应的流程定义id
     */
    protected String procDefId;

    /**
     * The tenant identifier of this process definition
     */
    protected String tenantId;

    /**
     * 节点id
     */
    protected String taskDefKey;

    /**
     * 节点名称
     */
    protected String taskName;

    /**
     * 受理人，可能为表达式
     */
    protected String assignee;

    /**
     * 关联的表单key
     */
    protected String formKey;

    /**
     * 任务分类
     */
    protected String category;

    /**
     * 是否多实例任务
     */
    protected Boolean hasMultiInstance;

    /**
     * 是否多实例串行执行
     */
    protected Boolean sequential;

    /**
     * 跳过该节点表达式
     */
    protected String skipExpression;

    /**
     * 扩展属性 动作类型，提交还是审批
     */
    protected String attrNodetype;

    /**
     * 扩展属性 是否可以撤销该步骤
     */
    protected Boolean attrRevokeflag;

    /**
     * 扩展属性 是否可以终止流程
     */
    protected Boolean attrEndflag;

    /**
     * 到期时间表达式
     */
    protected String duedatedefinition;
}
