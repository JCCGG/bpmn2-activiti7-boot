package cn.jcc.entity.cmd;

import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ExecutionEntityManager;

import java.io.Serializable;

/**
 保存执行实例信息
 **/
public class SaveExecutionCmd implements Command<Void>, Serializable {

    private static final long serialVersionUID = 1L;
    protected ExecutionEntity entity;

    public SaveExecutionCmd(ExecutionEntity entity) {
        this.entity = entity;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        ExecutionEntityManager executionEntityManager = commandContext.getExecutionEntityManager();
        if (this.entity == null) {
            throw new RuntimeException("执行实例信息为空！");
        } else {
            System.out.println("执行 SaveExecutionCmd---" + entity.getId());
            executionEntityManager.insert(this.entity);
        }
        return null;
    }
}
