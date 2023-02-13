package cn.jcc.entity.cmd;

import cn.hutool.core.collection.CollUtil;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.history.HistoryManager;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.*;
import org.activiti.engine.runtime.Execution;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 当前节点跳转目标节点
 **/
public class JumpCurrNodeToTargetNodeCmd implements Command<Execution>, Serializable {
    private static final long serialVersionUID = 1L;

//   流程实例ID
    String processInstanceId;
//   当前节点
    private String nodeId;
//    目标节点列表
    private List<String> targetTaskDefineKes;
//    跳转意见
    private String comment;


    public JumpCurrNodeToTargetNodeCmd(String processInstanceId, String nodeId, List<String> targetTaskDefineKes, String comment) {
        this.processInstanceId = processInstanceId;
        this.nodeId = nodeId;
        this.targetTaskDefineKes = targetTaskDefineKes;
        this.comment=comment;
    }

    @Override
    public Execution execute(CommandContext commandContext) {
        ProcessEngineConfigurationImpl processEngineConfiguration = commandContext.getProcessEngineConfiguration();
        RepositoryService repositoryService = processEngineConfiguration.getRepositoryService();
        TaskEntityManager taskEntityManager = commandContext.getTaskEntityManager();
        HistoryManager historyManager = commandContext.getHistoryManager();
        ExecutionEntityManager executionEntityManager = commandContext.getExecutionEntityManager();
        VariableInstanceEntityManager variableManager = commandContext.getVariableInstanceEntityManager();


//        查询当前任务列表
        List<TaskEntity> taskEntityList = taskEntityManager.findTasksByProcessInstanceId(processInstanceId);
//        过滤出一个当前节点
        taskEntityList = taskEntityList.stream().filter(taskEntity -> nodeId.equals(taskEntity.getTaskDefinitionKey())).collect(Collectors.toList());
        TaskEntity currTaskEntity = null;
        if (taskEntityList != null && taskEntityList.size() > 0) {
            currTaskEntity = taskEntityList.get(0);
        } else {
            throw new ActivitiException("当前节点没有任务可以跳转");
        }
        ExecutionEntity execution = currTaskEntity.getExecution();
        ExecutionEntity processExecution = executionEntityManager.findById(processInstanceId);
        ArrayList<String> targetNames = new ArrayList<>(4);
        this.targetTaskDefineKes.stream().forEach(targetKey->{
            ExecutionEntity childExecution = executionEntityManager.createChildExecution(processExecution);
            //processInstanceId
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processExecution.getProcessDefinitionId());
            //目标节点信息
            FlowElement targetFlowElement = bpmnModel.getFlowElement(targetKey);

            //此时设置执行实例的当前活动节点为目标节点
            childExecution.setCurrentFlowElement(targetFlowElement);
            commandContext.getAgenda().planContinueProcessOperation(childExecution);
            targetNames.add(targetFlowElement.getName());
        });

        String targetName = CollUtil.join(targetNames, ",");
        String record="跳转->【"+targetName+"】，原因：【"+this.comment+"】";
        //通知当前活动结束(更新act_hi_actinst)
        historyManager.recordActivityEnd(execution, record );
        for (TaskEntity taskEntity : taskEntityList) {
            //通知任务节点结束(更新act_hi_taskinst)
            historyManager.recordTaskEnd(taskEntity.getId(), record);
            //删除正在执行的当前任务
            taskEntityManager.delete(taskEntity);
        }

        //因为外键约束,首先要删除variable表中的execution相关数据
        variableManager.deleteVariableInstanceByTask(currTaskEntity);
        //删除当前任务的执行以及相关联的数据
        executionEntityManager.deleteExecutionAndRelatedData(execution,record,false);
        return null;
    }

}
