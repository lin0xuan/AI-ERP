package com.aierp.erp.controller.erp;

import com.aierp.common.AjaxResult;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.OGoodsCategory;
import com.aierp.model.entity.OGoodsCategoryAttribute;
import com.aierp.model.entity.OGoodsCategoryAttributeValue;
import com.aierp.security.common.BaseController;
import com.aierp.service.OGoodsCategoryAttributeService;
import com.aierp.service.OGoodsCategoryAttributeValueService;
import com.aierp.service.OGoodsCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.time.LocalDateTime;

@AllArgsConstructor
@RestController
@RequestMapping("/api/erp-api/goods_category")
public class GoodsCategoryController  extends BaseController {
    private final OGoodsCategoryService categoryService;
    private final OGoodsCategoryAttributeService categoryAttributeService;
    private final OGoodsCategoryAttributeValueService categoryAttributeValueService;

    @GetMapping("/list")
    public TableDataInfo categoryList()
    {
        var pageList = categoryService.list();
        return getDataTable(pageList);
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(categoryService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody OGoodsCategory category)
    {
        category.setCreateBy(getUsername());
        categoryService.addCategory(category);
        return toAjax(1);
    }

    @PutMapping
    public AjaxResult edit(@RequestBody OGoodsCategory category)
    {
        category.setUpdateBy(getUsername());
        category.setUpdateTime(LocalDateTime.now());
        return toAjax(categoryService.updateById(category));
    }
    /**
     * 删除分类
     * @param ids
     * @return
     */
    @DeleteMapping("/del/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(categoryService.removeByIds(Arrays.stream(ids).toList()));
    }

    /**
     * 分类属性列表
     * @param categoryId
     * @return
     */
    @GetMapping("/attribute_list")
    public TableDataInfo attributeList(Integer categoryId)
    {
        var pageList = categoryAttributeService.list(new LambdaQueryWrapper<OGoodsCategoryAttribute>().eq(OGoodsCategoryAttribute::getCategoryId,categoryId));
        return getDataTable(pageList);
    }

    /**
     * 分类属性添加
     * @param attribute
     * @return
     */
    @PostMapping("/attribute_add")
    public AjaxResult attributeAdd(@RequestBody OGoodsCategoryAttribute attribute)
    {
        return toAjax(categoryAttributeService.save(attribute));
    }

    @GetMapping(value = "/attribute/{id}")
    public AjaxResult getAttributeInfo(@PathVariable("id") Long id)
    {
        return success(categoryAttributeService.getById(id));
    }

    @PutMapping("/attribute")
    public AjaxResult attributeEdit(@RequestBody OGoodsCategoryAttribute attribute)
    {
        return toAjax(categoryAttributeService.updateById(attribute));
    }
    @DeleteMapping("/attribute/{ids}")
    public AjaxResult attributeRemove(@PathVariable Long[] ids)
    {
        return toAjax(categoryAttributeService.removeByIds(Arrays.stream(ids).toList()));
    }

    @GetMapping("/attribute_value_list")
    public TableDataInfo attributeValueList(Integer categoryAttributeId)
    {
        var pageList = categoryAttributeValueService.list(
                new LambdaQueryWrapper<OGoodsCategoryAttributeValue>().eq(OGoodsCategoryAttributeValue::getCategoryAttributeId,categoryAttributeId));
        return getDataTable(pageList);

    }

    @PostMapping("/attribute_value")
    public AjaxResult add(@RequestBody OGoodsCategoryAttributeValue attributeValue)
    {
        return toAjax(categoryAttributeValueService.save(attributeValue));
    }
    @GetMapping(value = "/attribute_value/{id}")
    public AjaxResult getAttributeValueInfo(@PathVariable("id") Long id)
    {
        return success(categoryAttributeValueService.getById(id));
    }

    @PutMapping("/attribute_value")
    public AjaxResult attributeValueEdit(@RequestBody OGoodsCategoryAttributeValue attributeValue)
    {
        return toAjax(categoryAttributeValueService.updateById(attributeValue));
    }

    /**
     * 删除商品分类属性值
     */
    @DeleteMapping("/attribute_value/{ids}")
    public AjaxResult removeAttributeValue(@PathVariable Long[] ids)
    {
        return toAjax(categoryAttributeValueService.removeByIds(Arrays.stream(ids).toList()));
    }
}
