package cn.jcc.entity.dto;

import cn.jcc.entity.BpmTaskModelEntity;
import lombok.Data;
import lombok.ToString;
import org.activiti.bpmn.model.Process;

import java.util.List;
import java.util.Map;

/**
 流程图中的用户任务集合Po
 **/
@Data
@ToString
public class UserTaskModelDTO {

    /**
     * 所有的任务节点
     */
    private List<BpmTaskModelEntity> allUserTaskModels;

    /**
     * 所有的（不分父子网关）并行网关
     */
    private Map<String,ParallelGatwayDTO> allForkGatewayMap;

    /**
     * 本次Process对象
     */
    private Process process;


}
