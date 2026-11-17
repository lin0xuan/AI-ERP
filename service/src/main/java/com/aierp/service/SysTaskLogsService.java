package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.model.entity.SysTaskLogs;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author TW
* @description 针对表【sys_task_logs】的数据库操作Service
* @createDate 2024-03-22 20:07:56
*/
public interface SysTaskLogsService extends IService<SysTaskLogs> {
    PageResult<SysTaskLogs> queryPageList(Integer taskId,PageQuery pageQuery);
}
