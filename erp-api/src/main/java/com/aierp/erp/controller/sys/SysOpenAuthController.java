package com.aierp.erp.controller.sys;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.SysOpenAuth;
import com.aierp.security.common.BaseController;
import com.aierp.service.SysOpenAuthService;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.time.LocalDateTime;

/**
 * 店铺Controller
 *
 * @author qihang
 * @date 2023-12-31
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/sys-api/openAuth")
public class SysOpenAuthController extends BaseController {
    private final SysOpenAuthService sysOpenAuthService;
    /**
     * 查询列表
     */
    @GetMapping("/list")
    public TableDataInfo list(SysOpenAuth bo, PageQuery pageQuery) {
        PageResult<SysOpenAuth> list = sysOpenAuthService.queryPageList(bo,pageQuery);
        return getDataTable(list);
    }


    /**
     * 获取详细信息
     */
    @GetMapping(value = "/detail/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(sysOpenAuthService.getById(id));
    }

    /**
     * 新增
     */
    @PostMapping("/add")
    public AjaxResult add(@RequestBody SysOpenAuth bo) {
        SysOpenAuth sysOpenAuth = sysOpenAuthService.queryByAppKey(bo.getAppKey());
        if(sysOpenAuth!=null){
            return AjaxResult.error("appkey已存在");
        }
        if(StringUtils.hasText(bo.getWhiteList())){
            bo.setWhiteList(bo.getWhiteList().replace("\r\n",",").replace("\n",","));
        }
        bo.setCreateTime(LocalDateTime.now());
        bo.setCreateBy(getUsername());
        return toAjax(sysOpenAuthService.save(bo));
    }

    /**
     * 修改
     */
    @PutMapping("/edit")
    public AjaxResult edit(@RequestBody SysOpenAuth bo) {
        if(bo.getId()==null) return AjaxResult.error("缺少参数：id");

        if(StringUtils.hasText(bo.getWhiteList())){
            bo.setWhiteList(bo.getWhiteList().replace("\r\n",",").replace("\n",","));
        }
        bo.setUpdateTime(LocalDateTime.now());
        bo.setUpdateBy(getUsername());
        sysOpenAuthService.updateById(bo);
        return toAjax(1);
    }

    /**
     * 删除
     */
    @DeleteMapping("/del/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        sysOpenAuthService.removeByIds(Arrays.asList(ids));
        return AjaxResult.success();
    }

}
