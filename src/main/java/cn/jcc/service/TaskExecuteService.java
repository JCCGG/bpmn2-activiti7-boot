package cn.jcc.service;


import cn.jcc.entity.form.BpmJumpForm;


public interface TaskExecuteService {

    /**
     * 驳回任意节点，支持回流线，多节点并行网关
     * @return
     */
    Boolean jumpTask(BpmJumpForm form);

}



