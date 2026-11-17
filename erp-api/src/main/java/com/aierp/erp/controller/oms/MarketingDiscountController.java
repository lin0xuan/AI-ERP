package com.aierp.erp.controller.oms;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.OMarketingDiscountRule;
import com.aierp.security.common.BaseController;
import com.aierp.service.OMarketingDiscountRuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/oms-api/marketing/discount")
@RequiredArgsConstructor
public class MarketingDiscountController extends BaseController {

    private final OMarketingDiscountRuleService discountService;

    @GetMapping("/list")
    public TableDataInfo list(OMarketingDiscountRule query, PageQuery pageQuery) {
        LambdaQueryWrapper<OMarketingDiscountRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getStatus() != null, OMarketingDiscountRule::getStatus, query.getStatus());
        wrapper.orderByDesc(OMarketingDiscountRule::getId);
        Page<OMarketingDiscountRule> page = discountService.page(pageQuery.build(), wrapper);
        return getDataTable(PageResult.build(page));
    }

    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) {
        return success(discountService.getById(id));
    }

    @PostMapping("/add")
    public AjaxResult add(@RequestBody OMarketingDiscountRule rule) {
        if (!StringUtils.hasText(rule.getRuleName())) return error("折扣名称不能为空");
        if (rule.getDiscountType() == null || rule.getDiscountValue() == null) return error("请填写折扣类型和折扣值");
        rule.setStatus(0);
        rule.setUsedQuota(0);
        rule.setCreatedBy(getUsername());
        rule.setCreatedTime(LocalDateTime.now());
        rule.setUpdatedTime(LocalDateTime.now());
        discountService.save(rule);
        return success();
    }

    @DeleteMapping("/audit/{id}")
    public AjaxResult audit(@PathVariable Long id) {
        OMarketingDiscountRule rule = discountService.getById(id);
        if (rule == null) return error("折扣不存在");
        rule.setStatus(1);
        rule.setUpdatedTime(LocalDateTime.now());
        return toAjax(discountService.updateById(rule));
    }

    @DeleteMapping("/del/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        return toAjax(discountService.removeById(id));
    }
}
