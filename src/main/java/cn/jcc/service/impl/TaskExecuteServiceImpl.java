package cn.jcc.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.jcc.entity.BpmTaskModelEntity;
import cn.jcc.entity.cmd.JumpCurrNodeToTargetNodeCmd;
import cn.jcc.entity.dto.ParallelGatwayDTO;
import cn.jcc.entity.dto.UserTaskModelDTO;
import cn.jcc.entity.form.BpmJumpForm;
import cn.jcc.entity.query.BpmTaskModelQuery;
import cn.jcc.service.ProcessJumpService;
import cn.jcc.service.TaskExecuteService;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程任务处理类
 */
@Service
public class TaskExecuteServiceImpl implements TaskExecuteService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;


    @Autowired
    private ManagementService managementService;

    @Autowired
    private ProcessJumpService processJumpService;

    /**
     * 跳转任意节点，区别接口虚拟线的方法驳回(存在并发问题)，当驳回的目标节点出现多个流入线时，驳回会生成多个任务节点
     * 实现多节点并行网关任务驳回，可选择并行网关任意节点驳回
     * 目标节点有多个时，目标节点必须是在同一个并行网关内部
     * 如果并行网关中嵌入了排他网关驳回，则驳回线需要设置默认跳线
     * 当前节点A,目标节点B（多个）
     *
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean jumpTask(BpmJumpForm form) {
        try {
            // 1. 查询当前任务信息
            Task task = taskService.createTaskQuery()
                    .taskId(form.getTaskId())
                    .taskAssignee("当前用户")
                    .singleResult();
            if (ObjectUtil.isNull(task)) {
                throw new RuntimeException("任务不存在或者不是你处理的任务");
            }
            String procInstId = task.getProcessInstanceId();
            List<String> targetTaskDefineKes = form.getTargetTaskDefineKes();

            BpmTaskModelQuery bpmTaskModelQuery = new BpmTaskModelQuery();
            bpmTaskModelQuery.setDefineId(task.getProcessDefinitionId());
            UserTaskModelDTO userTaskModelsDTO = processJumpService.getUserTaskModelDto(bpmTaskModelQuery);

            List<BpmTaskModelEntity> taskModelEntities = userTaskModelsDTO.getAllUserTaskModels();
            Map<String, BpmTaskModelEntity> taskModelEntitiesMap = taskModelEntities.stream().collect(
                    Collectors.toMap(BpmTaskModelEntity::getTaskDefKey, a -> a, (k1, k2) -> k1));

            // 要跳转的节点 B
            List<BpmTaskModelEntity> targetNodes = new ArrayList<>(4);
            // 当前节点 A
            BpmTaskModelEntity currentNode = taskModelEntitiesMap.get(task.getTaskDefinitionKey());
            form.getTargetTaskDefineKes().forEach(item -> targetNodes.add(taskModelEntitiesMap.get(item)));
            if (currentNode == null || CollectionUtils.isEmpty(targetNodes)) {
                throw new RuntimeException("当前节点或者目标节点不存在, taskId=" + form.getTaskId());
            }
            //如果B有多个节点
            //必须为同一个并行网关内的任务节点（网关开始、合并节点必须一致）
            //必须不是同一条流程线上的任务节点
            processJumpService.checkjumpTargetNodes(targetNodes);

            if (targetNodes.size() == 1 && currentNode.getParallelGatewayForkRef().equals(targetNodes.get(0).getParallelGatewayForkRef())) {
                // 如果A和B为同一条顺序流程线上（其中包含了A/B都是非并行网关上的节点 或都为并行网关中同一条流程线上的节点），则可以直接跳转
                // 跳转到目标节点
                JumpCurrNodeToTargetNodeCmd jumpCurrNodeToTargetNodeCmd = new JumpCurrNodeToTargetNodeCmd(procInstId, task.getTaskDefinitionKey(), targetTaskDefineKes,form.getComment());
                managementService.executeCommand(jumpCurrNodeToTargetNodeCmd);
            }else if (!currentNode.getInParallelGateway()) {
                //如果A非并行分支上的任务节点
                //则根据以上判定，B一定是为并行网关上节点，需要创建其B所在并行网关内其他任务节点已完成日志
                String forkParallelGatwayId = targetNodes.get(0).getForkParallelGatewayId();
                ParallelGatwayDTO forkGatewayB = userTaskModelsDTO.getAllForkGatewayMap().get(forkParallelGatwayId);
                // B为并行网关上节点，需要创建其B所在并行网关内其他任务节点已完成日志
                processJumpService.dealParallelGatewayFinishLog(forkGatewayB, task, targetNodes.size());

                // 跳转到目标节点
                JumpCurrNodeToTargetNodeCmd jumpCurrNodeToTargetNodeCmd = new JumpCurrNodeToTargetNodeCmd(procInstId, task.getTaskDefinitionKey(),targetTaskDefineKes,form.getComment());
                managementService.executeCommand(jumpCurrNodeToTargetNodeCmd);

            } else {
                throw new RuntimeException("当前接口只实现了同顺序流程线跳转、从网关外部跳转网关内部功能");
            }

            // 12. 查询目标任务节点历史办理人
            List<Task> newTaskList = taskService.createTaskQuery().processInstanceId(procInstId).list();
            for (Task newTask : newTaskList) {
                // 取之前的历史办理人
                List<HistoricTaskInstance> oldTargetTasks = historyService.createHistoricTaskInstanceQuery()
                        // 节点id
                        .taskDefinitionKey(newTask.getTaskDefinitionKey())
                        .processInstanceId(procInstId)
                        // 已经完成才是历史
                        .finished()
                        // 最新办理的在最前面
                        .orderByTaskCreateTime().desc()
                        .list();
                if(CollUtil.isNotEmpty(oldTargetTasks)){
                    HistoricTaskInstance oldTargetTask = oldTargetTasks.get(0);
                    taskService.setAssignee(newTask.getId(), oldTargetTask.getAssignee());
                }
            }
            return Boolean.TRUE;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("驳回失败：" + e.getMessage());
        }
    }

}
