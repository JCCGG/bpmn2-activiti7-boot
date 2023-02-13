package cn.jcc.service.impl;

import cn.jcc.entity.BpmTaskMinModelEntity;
import cn.jcc.entity.BpmTaskModelEntity;
import cn.jcc.entity.cmd.SaveExecutionCmd;
import cn.jcc.entity.dto.ParallelGatwayDTO;
import cn.jcc.entity.dto.UserTaskModelDTO;
import cn.jcc.entity.query.BpmTaskModelQuery;
import cn.jcc.service.ProcessJumpService;
import cn.jcc.utils.UserTaskAttrUtil;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.*;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.impl.cfg.IdGenerator;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.activiti.engine.impl.persistence.entity.ExecutionEntityManager;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 流程跳转操作类
 **/
@Service
public class ProcessJumpServiceImpl extends ActivitiService implements ProcessJumpService {
    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;
    @Autowired
    private ManagementService managementService;

    /**
     * （1）如果B有多个节点
     * 必须为同一个并行网关内的任务节点（网关开始、合并节点必须一致）
     * 必须不是同一条流程线上的任务节点
     *
     * @param targetNodes 要跳转到的目标节点集合
     */
    public void checkjumpTargetNodes(List<BpmTaskModelEntity> targetNodes) {
        int limitSize = 2;
        if (targetNodes.size() < limitSize) {
            return;
        }
        //判断节点
        String forkParallelGatwayId = "";
        String joinParallelGatwayId = "";
        String parallelGatewayForkRef = "";
        for (BpmTaskModelEntity item : targetNodes) {
            if (!item.getInParallelGateway()) {
                throw new RuntimeException("目标节点非并行网关中的节点");
            }
            if (StringUtils.isBlank(forkParallelGatwayId) || StringUtils.isBlank(joinParallelGatwayId)) {
                forkParallelGatwayId = item.getForkParallelGatewayId();
                joinParallelGatwayId = item.getJoinParallelGatewayId();
                continue;
            }
            if (!forkParallelGatwayId.equals(item.getForkParallelGatewayId()) ||
                    !joinParallelGatwayId.equals(item.getJoinParallelGatewayId())) {
                throw new RuntimeException("目标节点不是同一个并行网关");
            }
            if (StringUtils.isBlank(parallelGatewayForkRef)) {
                parallelGatewayForkRef = item.getParallelGatewayForkRef();
                continue;
            }
            if (parallelGatewayForkRef.equals(item.getParallelGatewayForkRef())) {
                throw new RuntimeException("目标节点不能在同一条业务线上");
            }
        }
    }

    /**
     * 获取所有用户任务节点信息
     *
     * @param query
     * @return
     */
    public UserTaskModelDTO getUserTaskModelDto(BpmTaskModelQuery query) {
        UserTaskModelDTO dto = new UserTaskModelDTO();
        if (StringUtils.isBlank(query.getDefineId())) {
            throw new RuntimeException("参数DefineID为空！");
        }
        List<BpmTaskModelEntity> resultUserTaskModels = new ArrayList<>(4);
        ProcessDefinition processDefinition = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(query.getDefineId()).singleResult();
        if (processDefinition == null) {
            return dto;
        }
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        dto.setProcess(process);
        // 先查询出所有符合条件的用户任务
        List<BpmTaskMinModelEntity> allUserTasks = queryMinUserTasks(query, process, processDefinition);
        if (CollectionUtils.isEmpty(allUserTasks)) {
            return dto;
        }

        // 查询出所有并行网关中的用户任务
        Map<String, ParallelGatwayDTO> forkGatewayMap = getAllParallelGatewayUserTask(query, processDefinition);

        // 并行网关的用户任务节点
        Map<String, BpmTaskModelEntity> pGatewayUserTaskModelsMap = new LinkedHashMap<>(4);
        forkGatewayMap.forEach((k, v) -> pGatewayUserTaskModelsMap.putAll(v.getUserTaskModels()));

        // 非并行网关的用户任务节点
        List<BpmTaskModelEntity> aloneUserTaskModels = new ArrayList<>(4);
        allUserTasks.stream()
                .filter(item -> !pGatewayUserTaskModelsMap.containsKey(item.getTaskDefKey()))
                .collect(Collectors.toList())
                .forEach(item -> aloneUserTaskModels.add((BpmTaskModelEntity) item));

        // 合并两个结果集
        resultUserTaskModels.addAll(aloneUserTaskModels);
        resultUserTaskModels.addAll(new ArrayList<>(pGatewayUserTaskModelsMap.values()));
        dto.setAllUserTaskModels(resultUserTaskModels);
        dto.setAllForkGatewayMap(forkGatewayMap);
        return dto;
    }

    /**
     * 查询所有符合条件的任务节点
     *
     * @param query
     * @param process
     * @param processDefinition
     * @return
     */
    private List<BpmTaskMinModelEntity> queryMinUserTasks(
            BpmTaskModelQuery query, Process process, ProcessDefinition processDefinition) {
        List<BpmTaskMinModelEntity> list = new ArrayList<>(8);
        List<UserTask> userTasks = process.findFlowElementsOfType(UserTask.class);
        for (UserTask task : userTasks) {
            if (StringUtils.isNotBlank(query.getCategory()) && !query.getCategory().equals(task.getCategory())) {
                continue;
            }
            if (StringUtils.isNotBlank(query.getTaskDefKey()) && !query.getTaskDefKey().equals(task.getId())) {
                continue;
            }
            if (StringUtils.isNotBlank(query.getNodeType()) && !query.getNodeType().equals(UserTaskAttrUtil.getAttr(task, UserTaskAttrUtil.NODE_TYPE_KEY))) {
                continue;
            }
            BpmTaskMinModelEntity userTaskModelEntity = new BpmTaskModelEntity();
            userTaskModelEntity.setTenantId(processDefinition.getTenantId());
            userTaskModelEntity.setProcDefId(processDefinition.getId());
            userTaskModelEntity.setTaskDefKey(task.getId());
            userTaskModelEntity.setTaskName(task.getName());
            userTaskModelEntity.setAssignee(task.getAssignee());
            userTaskModelEntity.setCategory(task.getCategory());
            userTaskModelEntity.setFormKey(task.getFormKey());
            userTaskModelEntity.setSkipExpression(task.getSkipExpression());
            userTaskModelEntity.setHasMultiInstance(task.hasMultiInstanceLoopCharacteristics());
            if (task.hasMultiInstanceLoopCharacteristics()) {
                userTaskModelEntity.setSequential(task.getLoopCharacteristics().isSequential());
                userTaskModelEntity.setAssignee("${" + task.getLoopCharacteristics().getInputDataItem() + "}");
            } else {
                userTaskModelEntity.setSequential(false);
            }
            UserTaskAttrUtil.setAttr(userTaskModelEntity, task);
            list.add(userTaskModelEntity);
        }
        return list;
    }

    /**
     * 获取所有并行网关内的节点 和 并行网关之间的关系
     */
    protected Map<String, ParallelGatwayDTO> getAllParallelGatewayUserTask(BpmTaskModelQuery limitQuery, ProcessDefinition processDefinition) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        Process process = bpmnModel.getProcesses().get(0);
        Map<String, FlowElement> flowElementMap = process.getFlowElementMap();

        List<ParallelGateway> parallelGateways = process.findFlowElementsOfType(ParallelGateway.class);
        List<InclusiveGateway> inclusiveGateways = process.findFlowElementsOfType(InclusiveGateway.class);

        List<Gateway> allParallelGateways = new ArrayList<>(4);
        allParallelGateways.addAll(parallelGateways);
        allParallelGateways.addAll(inclusiveGateways);


        Map<String, ParallelGatwayDTO> forkGatewayMap = new HashMap<>(4);

        for (Gateway gateway : allParallelGateways) {
            int outgoingFlowsSize = gateway.getOutgoingFlows().size();
            // 从 fork网关节点开始查找
            if (outgoingFlowsSize > 1 && !forkGatewayMap.containsKey(gateway.getId())) {
                ParallelGatwayDTO dto = new ParallelGatwayDTO();
                dto.setForkSize(outgoingFlowsSize);
                dto.setForkId(gateway.getId());
                dto.setTenantId(processDefinition.getTenantId());
                dto.setProcDefId(processDefinition.getId());
                dto.setUserTaskModels(new LinkedHashMap<>());
                forkGatewayMap.put(gateway.getId(), dto);

                loopForkParallelGateway(limitQuery, dto, gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
            }
        }

        // 设置一些并行网关附加信息，用于跳转业务逻辑的判定
        forkGatewayMap.forEach((k, v) -> {
            Set<String> childForkParallelGatwayIds = new HashSet<>(2);
            getChildForkParallelGatwayIds(v.getChildParallelGatways(), childForkParallelGatwayIds);
            v.getUserTaskModels().forEach((k1, v1) -> v1.setChildForkParallelGatewayIds(childForkParallelGatwayIds));
        });
        return forkGatewayMap;
    }

    private void getChildForkParallelGatwayIds(List<ParallelGatwayDTO> childGws, Set<String> childForkParallelGatwayIds) {
        if (!CollectionUtils.isEmpty(childGws)) {
            for (ParallelGatwayDTO item : childGws) {
                childForkParallelGatwayIds.add(item.getForkId());

                getChildForkParallelGatwayIds(item.getChildParallelGatways(), childForkParallelGatwayIds);
            }
        }
    }

    /**
     * 递归遍历所有并行分支上的
     *
     * @param limitQuery     查询限制
     * @param dto            ParallelGatwayDTO
     * @param outgoingFlows  List<SequenceFlow>
     * @param forkGatewayMap Map<String, ParallelGatwayDTO>
     * @param flowElementMap Map<String, FlowElement>
     */
    private void loopForkParallelGateway(
            BpmTaskModelQuery limitQuery, ParallelGatwayDTO dto, List<SequenceFlow> outgoingFlows,
            Map<String, ParallelGatwayDTO> forkGatewayMap, Map<String, FlowElement> flowElementMap) {
        if (CollectionUtils.isEmpty(outgoingFlows)) {
            return;
        }
        for (SequenceFlow item : outgoingFlows) {
            FlowElement refFlowElement = flowElementMap.get(item.getSourceRef());
            FlowElement targetFlowElement = flowElementMap.get(item.getTargetRef());
            // 设置当前查询的哪一个分支线
            if (refFlowElement instanceof ParallelGateway || refFlowElement instanceof InclusiveGateway) {
                dto.setTmpForkRef(item.getTargetRef());
            }
            if (targetFlowElement instanceof UserTask) {
                UserTask task = (UserTask) targetFlowElement;
                boolean eligible = true;
                if (dto.getUserTaskModels().containsKey(task.getId())) {
                    eligible = false;
                }
                if (StringUtils.isNotBlank(limitQuery.getCategory()) && !limitQuery.getCategory().equals(task.getCategory())) {
                    eligible = false;
                }
                if (StringUtils.isNotBlank(limitQuery.getTaskDefKey()) && !limitQuery.getTaskDefKey().equals(task.getId())) {
                    eligible = false;
                }
                if (StringUtils.isNotBlank(limitQuery.getNodeType()) && !limitQuery.getNodeType().equals(UserTaskAttrUtil.getAttr(task, UserTaskAttrUtil.NODE_TYPE_KEY))) {
                    eligible = false;
                }
                if (eligible) {
                    BpmTaskModelEntity userTaskModelEntity = new BpmTaskModelEntity();
                    // 设置 BpmTaskModelEntity 值
                    convorBpmTaskModelEntity(userTaskModelEntity, dto, task);
                    dto.getUserTaskModels().put(task.getId(), userTaskModelEntity);
                }
                // 递归取下面的节点
                loopForkParallelGateway(limitQuery, dto, ((FlowNode) targetFlowElement).getOutgoingFlows(), forkGatewayMap, flowElementMap);
            }else if(targetFlowElement instanceof org.activiti.bpmn.model.Task){
//                其他任务节点跳过往后走
                loopForkParallelGateway(limitQuery, dto, ((FlowNode) targetFlowElement).getOutgoingFlows(), forkGatewayMap, flowElementMap);
            }
//            排他网关的时候特殊处理
            if(targetFlowElement instanceof ExclusiveGateway){
//                Gateway gateway = (Gateway) targetFlowElement;
////                获取默认线（驳回线）
//                String defaultFlow = gateway.getDefaultFlow();
////                筛选排他网关的目标节点
//                List<SequenceFlow> targetSequnces = gateway.getOutgoingFlows()
//                        .stream()
//                        .filter(s -> !s.getId().equals(defaultFlow)&&"${result=='Y'}".equals(s.getConditionExpression()))
//                        .collect(Collectors.toList());
//                if(targetSequnces.size()>=1){
//                    // 递归取下面的节点
//                    loopForkParallelGateway(limitQuery, dto,targetSequnces, forkGatewayMap, flowElementMap);
//                }
            }
//            并行网关和包容网关
            if (targetFlowElement instanceof ParallelGateway || targetFlowElement instanceof InclusiveGateway) {
                Gateway gateway = (Gateway) targetFlowElement;
                // 遇到新的并行网关节点
                // 从 fork网关节点开始查找
                if (gateway.getOutgoingFlows().size() > 1) {
                    ParallelGatwayDTO childDto = forkGatewayMap.get(gateway.getId());
                    if (childDto == null) {
                        childDto = new ParallelGatwayDTO();
                        childDto.setTenantId(dto.getTenantId());
                        childDto.setProcDefId(dto.getProcDefId());
                    }
                    childDto.setForkSize(gateway.getOutgoingFlows().size());
                    childDto.setForkId(targetFlowElement.getId());
                    dto.getChildParallelGatways().add(childDto);
                    childDto.setParentParallelGatwayDTO(dto);
                    forkGatewayMap.put(targetFlowElement.getId(), childDto);
                    // 递归取下面的节点
                    loopForkParallelGateway(limitQuery, childDto, gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
                } else if (gateway.getIncomingFlows().size() > 1 && gateway.getOutgoingFlows().size() == 1) {
                    // 遇到新的join类型的并行网关节点，此时dto为前面与之对应的fork并行网关节点
                    dto.setJoinId(gateway.getId());
                    dto.getUserTaskModels().forEach((k, v) -> v.setJoinParallelGatewayId(gateway.getId()));

                    if (dto.getParentParallelGatwayDTO() == null) {
                        // 本并行网关里面的用户任务递归完毕
                        break;
                    }
                    // 继续父并行网关的递归取
                    loopForkParallelGateway(limitQuery, dto.getParentParallelGatwayDTO(), gateway.getOutgoingFlows(), forkGatewayMap, flowElementMap);
                }
            }
        }
    }

    private void convorBpmTaskModelEntity(BpmTaskModelEntity userTaskModelEntity,
                                          ParallelGatwayDTO dto,
                                          UserTask task) {
        userTaskModelEntity.setTenantId(dto.getTenantId());
        userTaskModelEntity.setProcDefId(dto.getProcDefId());
        userTaskModelEntity.setTaskDefKey(task.getId());
        userTaskModelEntity.setTaskName(task.getName());
        userTaskModelEntity.setAssignee(task.getAssignee());
        userTaskModelEntity.setCategory(task.getCategory());
        userTaskModelEntity.setFormKey(task.getFormKey());
        userTaskModelEntity.setInParallelGateway(true);
        userTaskModelEntity.setParallelGatewayForkRef(dto.getTmpForkRef());
        userTaskModelEntity.setForkParallelGatewayId(dto.getForkId());
        userTaskModelEntity.setHasMultiInstance(task.hasMultiInstanceLoopCharacteristics());
        userTaskModelEntity.setSkipExpression(task.getSkipExpression());
        if (task.hasMultiInstanceLoopCharacteristics()) {
            userTaskModelEntity.setSequential(task.getLoopCharacteristics().isSequential());
            userTaskModelEntity.setAssignee("${" + task.getLoopCharacteristics().getInputDataItem() + "}");
        } else {
            userTaskModelEntity.setSequential(false);
        }
        UserTaskAttrUtil.setAttr(userTaskModelEntity, task);
    }

    /**
     * B为并行网关上节点，需要创建其B所在并行网关内其他任务节点已完成日志
     *
     * @param forkGatewayB    B所在的并行网关
     * @param task            当然任务
     * @param targetNodesSize B的数量
     */
    public void dealParallelGatewayFinishLog(
            ParallelGatwayDTO forkGatewayB, Task task, int targetNodesSize) {
        dealParallelGatewayFinishLog(forkGatewayB, task, targetNodesSize, null);
    }

    /**
     * B为并行网关上节点，需要创建其B所在并行网关内其他任务节点已完成日志
     *
     * @param forkGatewayB             B所在的并行网关
     * @param task                     当然任务
     * @param targetNodesSize          B的数量
     * @param untilParentForkGatewayId b的最上层父并行网关
     */
    public void dealParallelGatewayFinishLog(
            ParallelGatwayDTO forkGatewayB, Task task, int targetNodesSize, String untilParentForkGatewayId) {
        int reduceForkSize = forkGatewayB.getForkSize() - targetNodesSize;
        if (reduceForkSize < 0) {
            throw new RuntimeException("目标节点数量不能大于并行网关的总分支数量");
        }
        if (reduceForkSize > 0) {
            for (int i = 0; i < reduceForkSize; i++) {
//                logger.debug("插入目标节点的合并网关 {} 一条分支线上的完成记录", forkGatewayB.getJoinId());
                insertExecutionTest(forkGatewayB.getJoinId(), task.getProcessInstanceId(), task.getProcessDefinitionId(), task.getTenantId());
            }
        }

        // 如果该网关是子网关则，还需要处理父网关信息
        ParallelGatwayDTO parentParallelGatwayDTO = forkGatewayB.getParentParallelGatwayDTO();
        while (parentParallelGatwayDTO != null) {
            if (StringUtils.isNotBlank(untilParentForkGatewayId)
                    && parentParallelGatwayDTO.getForkId().equals(untilParentForkGatewayId)) {
                break;
            }

            for (int i = 0; i < parentParallelGatwayDTO.getForkSize() - 1; i++) {
//                logger.debug("插入目标节点的父合并网关 {} 一条分支线上的完成记录 ", parentParallelGatwayDTO.getJoinId());
                insertExecutionTest(parentParallelGatwayDTO.getJoinId(), task.getProcessInstanceId(), task.getProcessDefinitionId(), task.getTenantId());
            }
            parentParallelGatwayDTO = parentParallelGatwayDTO.getParentParallelGatwayDTO();
        }
    }

    /**
     * 添加并行网关执行实例
     *
     * @param gatewayId
     * @param processInstanceId
     * @param processDefinitionId
     * @param tenantId
     */
    protected void insertExecutionTest(String gatewayId, String processInstanceId, String processDefinitionId, String tenantId) {
        ExecutionEntityManager executionEntityManager = ((ProcessEngineConfigurationImpl) processEngineConfiguration).getExecutionEntityManager();
        ExecutionEntity executionEntity = executionEntityManager.create();
        IdGenerator idGenerator = ((ProcessEngineConfigurationImpl) processEngineConfiguration).getIdGenerator();
        executionEntity.setId(idGenerator.getNextId());
        executionEntity.setRevision(0);
        executionEntity.setProcessInstanceId(processInstanceId);

        executionEntity.setParentId(processInstanceId);
        executionEntity.setProcessDefinitionId(processDefinitionId);

        executionEntity.setRootProcessInstanceId(processInstanceId);
//        添加结束网关的ID到act_ru_execution的ACT_ID_字段
        ParallelGateway parallelGateway = new ParallelGateway();
        parallelGateway.setId(gatewayId);
        executionEntity.setCurrentFlowElement(parallelGateway);

        executionEntity.setActive(false);
        executionEntity.setSuspensionState(1);
        executionEntity.setTenantId(tenantId);
        executionEntity.setScope(false);
        executionEntity.setStartTime(new Date());
        ((ExecutionEntityImpl) executionEntity).setCountEnabled(false);

        managementService.executeCommand(new SaveExecutionCmd(executionEntity));
    }
}
