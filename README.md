# Activiti7实现跨网关跳转，并行网关外部跳转至并行网关内部
> 本项目基于https://gitee.com/banana6/bpm2-boot-starter.git 进行改进
> flowable提供了许多如：moveSingleActivityIdToActivityIds，moveActivityIdTo等接口可以快速实现流程节点之间跳转，但是activiti没有这样的接口，可以通过重写Command实现

实现功能：
- 同层级流程线间节点跳转
- 并行网关外部跳转并行网关内部节点

##### 只因需求太过于奇葩，activiti自带的接口无法实现跨并行网关间节点的跳转功能，例如以下流程，节点4->并行1，execution表只生成了并行1的执行，当并行1任务完成时会卡在并行网关汇聚节点，导致流程终止
思路: 当判断跳转的目标节点在并行网关中，则补充目标节点所在的并行网关节点其他兄弟节点执行完成了记录至并行网关，则以上当并行1完成时，并行网关检测3个分支均执行完成，流程会继续往下走
![流程图片](https://file.wwdab.cn/github/Snipaste_2023-02-13_16-38-14.png)
