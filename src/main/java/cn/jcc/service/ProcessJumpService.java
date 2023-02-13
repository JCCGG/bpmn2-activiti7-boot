package cn.jcc.service;


import cn.jcc.entity.BpmTaskModelEntity;
import cn.jcc.entity.dto.ParallelGatwayDTO;
import cn.jcc.entity.dto.UserTaskModelDTO;
import cn.jcc.entity.query.BpmTaskModelQuery;

import java.util.List;

public interface ProcessJumpService {
    /**
     * 获取所有用户任务节点信息
     *
     * @param query
     * @return
     */
    public UserTaskModelDTO getUserTaskModelDto(BpmTaskModelQuery query);
    /**
     * （1）如果B有多个节点
     * 必须为同一个并行网关内的任务节点（网关开始、合并节点必须一致）
     * 必须不是同一条流程线上的任务节点
     *
     * @param targetNodes 要跳转到的目标节点集合
     */
    public void checkjumpTargetNodes(List<BpmTaskModelEntity> targetNodes);
    /**
     * B为并行网关上节点，需要创建其B所在并行网关内其他任务节点已完成日志
     *
     * @param forkGatewayB    B所在的并行网关
     * @param task            当然任务
     * @param targetNodesSize B的数量
     */
    public void dealParallelGatewayFinishLog(
            ParallelGatwayDTO forkGatewayB, org.activiti.engine.task.Task task, int targetNodesSize);
}
