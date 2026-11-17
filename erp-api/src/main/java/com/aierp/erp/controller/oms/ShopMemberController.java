package com.aierp.erp.controller.oms;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.ShopMember;
import com.aierp.security.common.BaseController;
import com.aierp.service.ShopMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/oms-api/shop/member")
@RequiredArgsConstructor
public class ShopMemberController extends BaseController {

    private final ShopMemberService memberService;

    @GetMapping("/list")
    public TableDataInfo list(ShopMember query, PageQuery pageQuery) {
        return getDataTable(memberService.queryPageList(query, pageQuery));
    }

    @GetMapping("/{id}")
    public AjaxResult get(@PathVariable Long id) {
        return success(memberService.getById(id));
    }

    @GetMapping("/search")
    public AjaxResult search(String mobile, String name) {
        return success(memberService.querySingleBy(mobile, name));
    }

    @PostMapping("/add")
    public AjaxResult add(@RequestBody ShopMember member) {
        if (!StringUtils.hasText(member.getName())) return error("姓名不能为空");
        member.setCreateOn(LocalDateTime.now());
        var result = memberService.addShopMember(member);
        if (result.getCode() != 0) return error(result.getMsg());
        return success(result.getData());
    }

    @PutMapping("/edit")
    public AjaxResult edit(@RequestBody ShopMember member) {
        if (member.getId() == null) return error("缺少会员 id");
        member.setUpdateOn(LocalDateTime.now());
        return toAjax(memberService.updateById(member));
    }
}
