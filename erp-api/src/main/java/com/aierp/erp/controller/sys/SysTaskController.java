package com.aierp.erp.controller.sys;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.SysTask;
import com.aierp.security.common.BaseController;
import com.aierp.service.SysTaskLogsService;
import com.aierp.service.SysTaskService;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 *
 * 
 * @author qihang
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/sys-api/system/task")
public class SysTaskController extends BaseController
{
    private final SysTaskService taskService;
    private final SysTaskLogsService taskLogsService;
    @GetMapping("/list")
    public TableDataInfo list(PageQuery pageQuery)
    {
        PageResult<SysTask> pageResult = taskService.queryPageList(pageQuery);
        return getDataTable(pageResult);
    }

    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable Long taskId)
    {
        return success(taskService.getById(taskId));
    }

    @GetMapping(value = "/logs/{taskId}")
    public AjaxResult getLogs(@PathVariable Integer taskId)
    {
        return success(taskLogsService.queryPageList(taskId,new PageQuery()));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody SysTask task)
    {
        if(StringUtils.hasText(task.getCron())&&!task.getCron().equals("-")){
            task.setStatus(1);
        }else{
            task.setStatus(0);
        }
        return toAjax(taskService.updateById(task));
    }
}
