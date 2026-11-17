package com.aierp.erp.controller.oms;

import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.OShopRegion;
import com.aierp.security.common.BaseController;
import com.aierp.service.OShopRegionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/oms-api/shop/region")
@RequiredArgsConstructor
public class ShopRegionController extends BaseController {

    private final OShopRegionService regionService;

    @GetMapping("/list")
    public TableDataInfo list() {
        List<OShopRegion> list = regionService.list(new LambdaQueryWrapper<OShopRegion>()
                .orderByAsc(OShopRegion::getId));
        return getDataTable(list);
    }
}
