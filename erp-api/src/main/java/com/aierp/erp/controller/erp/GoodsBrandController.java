package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.OGoodsBrand;
import com.aierp.security.common.BaseController;
import com.aierp.service.OGoodsBrandService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.time.LocalDateTime;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/goods_brand")
public class GoodsBrandController extends BaseController {
    private final OGoodsBrandService brandService;
    @GetMapping("/list")
    public TableDataInfo skuList(OGoodsBrand bo, PageQuery pageQuery)
    {
        var pageList = brandService.queryPageList(bo,pageQuery);
        return getDataTable(pageList);
    }

    /**
     * 获取商品品牌详细信息
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(brandService.getById(id));
    }

    /**
     * 新增商品品牌
     */
    @PostMapping
    public AjaxResult add(@RequestBody OGoodsBrand erpGoodsBrand)
    {
        erpGoodsBrand.setStatus(1);
        erpGoodsBrand.setCreateBy(getUsername());
        erpGoodsBrand.setCreateTime(LocalDateTime.now());
        return toAjax(brandService.save(erpGoodsBrand));
    }

    /**
     * 修改商品品牌
     */
    @PutMapping
    public AjaxResult edit(@RequestBody OGoodsBrand erpGoodsBrand)
    {
        erpGoodsBrand.setUpdateBy(getUsername());
        erpGoodsBrand.setUpdateTime(LocalDateTime.now());
        return toAjax(brandService.updateById(erpGoodsBrand));
    }

    /**
     * 删除商品品牌
     */
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(brandService.removeByIds(Arrays.stream(ids).toList()));
    }
}
