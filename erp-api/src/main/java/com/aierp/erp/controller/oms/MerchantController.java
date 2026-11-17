package com.aierp.erp.controller.oms;

import com.aierp.common.*;
import com.aierp.model.bo.MerchantAddBo;
import com.aierp.model.entity.ErpMerchant;
import com.aierp.model.entity.SysUser;
import com.aierp.model.query.MerchantQuery;
import com.aierp.security.common.BaseController;
import com.aierp.security.common.SecurityUtils;
import com.aierp.service.ErpMerchantService;
import com.aierp.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.time.LocalDateTime;

@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/merchant")
public class MerchantController extends BaseController {
    private final ErpMerchantService merchantService;
    private final ISysUserService userService;


    @GetMapping("/list")
    public TableDataInfo list(MerchantQuery bo, PageQuery pageQuery) {
        var list = merchantService.list(new LambdaQueryWrapper<ErpMerchant>().eq(ErpMerchant::getStatus, 0));
        ErpMerchant self = new ErpMerchant();
        self.setId(0L);
        self.setName("总部自营");
        list.add(0,self);
        return getDataTable(list);
    }

    @GetMapping("/pageList")
    public TableDataInfo pageList(MerchantQuery bo, PageQuery pageQuery) {
        // 管理员
        PageResult<ErpMerchant> pageResult = merchantService.queryPageList(bo, pageQuery);
        ErpMerchant self = new ErpMerchant();
        self.setId(0L);
        self.setName("总部自营");
        if (pageResult.getRecords() != null) {
            if (pageResult.getRecords().size() == 0) {
                pageResult.setRecords(new ArrayList<ErpMerchant>());
            }
            pageResult.getRecords().add(0, self);

        } else {
            pageResult.setRecords(new ArrayList<ErpMerchant>());
            pageResult.getRecords().add(0, self);
        }
        return getDataTable(pageResult);
    }

    /**
     * 全部商户（仅总部查询）
     * @return
     */
    @GetMapping("/list_all")
    public AjaxResult listAll() {
        var list = merchantService.list(new LambdaQueryWrapper<ErpMerchant>().eq(ErpMerchant::getStatus, 0));
        return AjaxResult.success(list);
    }


    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(merchantService.getById(id));
    }


    @PostMapping("/add")
    public AjaxResult add(@RequestBody MerchantAddBo bo) {
        ResultVo<ErpMerchant> resultVo = merchantService.add(bo, getUsername());
        if (resultVo.getCode() == 0) return AjaxResult.success();
        else return AjaxResult.error(resultVo.getMsg());
    }

    @PutMapping("/edit")
    public AjaxResult edit(@RequestBody ErpMerchant bo)
    {
        if(bo.getId()==null) return AjaxResult.error("缺少参数：id");
        bo.setUpdateBy(getUsername());
        bo.setUpdateTime(LocalDateTime.now());
        merchantService.updateById(bo);
        return toAjax(1);
    }

    @PostMapping("/setLoginName")
    public AjaxResult setLoginName(@RequestBody ErpMerchant form) {
        if (form.getId() == null || !StringUtils.hasText(form.getLoginName()) || !StringUtils.hasText(form.getPassword())) {
            return AjaxResult.error("请填写商户、登录名和密码");
        }
        ErpMerchant merchant = merchantService.getById(form.getId());
        if (merchant == null) return AjaxResult.error("商户不存在");
        String loginName = form.getLoginName().trim();
        String encoded = SecurityUtils.encryptPassword(form.getPassword());
        SysUser existing = userService.selectUserByUserName(loginName);
        if (existing != null && !"20".equals(existing.getUserType())) {
            return AjaxResult.error("该登录名已被其他类型账号占用");
        }
        merchant.setLoginName(loginName);
        merchant.setPassword(encoded);
        merchant.setUpdateBy(getUsername());
        merchant.setUpdateTime(LocalDateTime.now());
        merchantService.updateById(merchant);
        if (existing == null) {
            SysUser user = new SysUser();
            user.setUserName(loginName);
            user.setNickName(merchant.getName());
            user.setPassword(encoded);
            user.setUserType("20");
            user.setStatus("0");
            user.setDelFlag("0");
            user.setCreateBy(getUsername());
            user.setCreateTime(LocalDateTime.now());
            userService.insertUser(user);
        } else {
            existing.setPassword(encoded);
            existing.setNickName(merchant.getName());
            userService.updateUser(existing);
        }
        return AjaxResult.success();
    }

    @DeleteMapping("/del/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        if(ids == null) return AjaxResult.error("缺少参数：id");
        merchantService.removeBatchByIds(Arrays.stream(ids).toList());
        return AjaxResult.success();
    }
}
