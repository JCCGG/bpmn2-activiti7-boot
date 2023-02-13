package cn.jcc.entity;

import lombok.Data;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 流程中某个用户任务节点
 **/
@Data
@ToString
public class BpmTaskModelEntity extends BpmTaskMinModelEntity{
    private static final long serialVersionUID = 1L;

    /**
     * 是否为并行网关中的任务节点
     */
    protected Boolean inParallelGateway = false;

    /**
     * 如果是并行网关上的节点，该值表示是属于哪一个分支线上的节点
     */
    protected String parallelGatewayForkRef = "";

    /**
     * 如果是并行网关上的节点，该值表示并行网关的分支网关id
     */
    protected String forkParallelGatewayId = "";

    /**
     * 如果是并行网关上的节点，该值表示并行网关的合并网关id
     */
    protected String joinParallelGatewayId = "";

    /**
     * 如果是并行网关上的节点，该值表示所有子网关的分支网关id集合，用于判断两个节点是否为 父子网关中的任务节点关系
     */
    protected Set<String> childForkParallelGatewayIds = new HashSet<>(2);
}
