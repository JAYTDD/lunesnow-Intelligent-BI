-- Chart 表初始化 SQL
-- 用于智能 BI 图表生成项目

-- 切换库
use `lunesnow-intelligent bi`;

-- 图表信息表
create table if not exists chart
(
    id          bigint auto_increment comment 'id' primary key,
    goal        varchar(128)                       not null comment '分析目标',
    name        varchar(128)                       not null comment '图表名称',
    chartData   varchar(2048)                      null comment '图表数据',
    chartType   varchar(128)                       null comment '图表类型',
    genChart    text                               null comment '生成的图表数据',
    genResult   text                               null comment '生成的分析结论',
    userId      bigint                             not null comment '创建用户 id',
    status      varchar(128) default 'waiting'     not null comment '任务状态：waiting/running/succeed/failed',
    execMessage text                               null comment '执行信息',
    waitTime    int                                null comment '等待时间',
    runningTime int                                null comment '执行时长',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    index idx_userId (userId)
) comment '图表信息' collate = utf8mb4_unicode_ci;

-- 旧表升级：补全缺失字段（如已存在则忽略）
alter table chart add column if not exists name        varchar(128)                       not null default '' comment '图表名称' after goal;
alter table chart add column if not exists status      varchar(128) default 'waiting'     not null comment '任务状态' after userId;
alter table chart add column if not exists execMessage text                               null comment '执行信息' after status;
alter table chart add column if not exists waitTime    int                                null comment '等待时间' after execMessage;
alter table chart add column if not exists runningTime int                                null comment '执行时长' after waitTime;
