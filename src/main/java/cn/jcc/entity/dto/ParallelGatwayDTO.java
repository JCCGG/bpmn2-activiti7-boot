package cn.jcc.entity.dto;

import cn.jcc.entity.BpmTaskModelEntity;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

/**
 并行网关DTO
 **/
@Data
@ToString
public class ParallelGatwayDTO implements Serializable {

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
     * 该并行网关多少个分支数量
     */
    private Integer forkSize;

    /**
     * 并行网关起始网关id
     */
    private String forkId;

    /**
     * 并行网关结束id
     */
    private String joinId;

    /**
     * 该值表示当前查询的哪一个分支线上
     */
    protected String tmpForkRef;

    /**
     * 并行网关节点上的用户任务
     * key = taskDefKey  value = UserTaskModelEntity
     */
    private LinkedHashMap<String, BpmTaskModelEntity> userTaskModels;

    /**
     * 子 并行网关节点
     * 例如：嵌套并行网关
     */
    private List<ParallelGatwayDTO> childParallelGatways;

    /**
     * 父 并行网关节点
     */
    private ParallelGatwayDTO parentParallelGatwayDTO;
}
