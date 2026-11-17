package com.aierp.erp.controller.oms;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.TableDataInfo;
import com.aierp.model.entity.OShop;
import com.aierp.model.entity.OShopPlatform;
import com.aierp.request.ShopBo;
import com.aierp.security.common.BaseController;
import com.aierp.model.entity.OShopPullLasttime;
import com.aierp.service.OShopPlatformService;
import com.aierp.service.OShopPullLasttimeService;
import com.aierp.service.OShopService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 店铺Controller
 * 
 * @author qihang
 * @date 2023-12-31
 */
@AllArgsConstructor
@RestController
@RequestMapping("/api/oms-api/shop")
public class ShopController extends BaseController {

    private final OShopService shopService;
    private final OShopPlatformService platformService;
    private final OShopPullLasttimeService pullLasttimeService;


    /**
     * 查询店铺列表logistics
     */
    @PreAuthorize("@ss.hasPermi('shop:shop:list')")
    @GetMapping("/list")
    public TableDataInfo list(ShopBo shop)
    {
        LambdaQueryWrapper<OShop> qw = new LambdaQueryWrapper<OShop>()
                .eq(shop.getType()!=null,OShop::getType,shop.getType())
                .eq(shop.getMerchantId()!=null,OShop::getMerchantId,shop.getMerchantId())
//                .ne(shop.getType()==null,OShop::getType,999)
                ;
        List<OShop> list = shopService.list(qw);
        return getDataTable(list);
    }

    @GetMapping("/pageList")
    public TableDataInfo pageList(OShop shop, PageQuery pageQuery) {
        PageResult<OShop> pageList = shopService.queryPageList(shop, pageQuery);
        return getDataTable(pageList);
    }

    @GetMapping("/platformList")
    public TableDataInfo platformList(Integer status) {
        LambdaQueryWrapper<OShopPlatform> qw = new LambdaQueryWrapper<OShopPlatform>()
                .eq(status!=null,OShopPlatform::getStatus,status)
                ;
        qw.orderByAsc(OShopPlatform::getSort);
        List<OShopPlatform> list = platformService.list(qw);
        return getDataTable(list);
    }

    /**
     * 获取店铺详细信息
     */
    @PreAuthorize("@ss.hasPermi('shop:shop:query')")
    @GetMapping(value = "/shop/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(shopService.getById(id));
    }

    @GetMapping(value = "/platform/{id}")
    public AjaxResult getPlatform(@PathVariable("id") Long id)
    {
        return success(platformService.getById(id));
    }

    /**
     * 新增店铺
     */
    @PreAuthorize("@ss.hasPermi('shop:shop:add')")
    @PostMapping("/shop")
    public AjaxResult add(@RequestBody OShop shop)
    {
        if(shop.getType()==null) return AjaxResult.error("请选择店铺平台");
//        shop.setModifyOn(System.currentTimeMillis()/1000);
//        shop.setCreateOn(System.currentTimeMillis()/1000);
//        shop.setCreateTime(LocalDateTime.now());
        return toAjax(shopService.save(shop));
    }

    /**
     * 修改店铺
     */
    @PreAuthorize("@ss.hasPermi('shop:shop:edit')")
    @PutMapping("/shop")
    public AjaxResult edit(@RequestBody OShop shop)
    {
//        shop.setUpdateTime(LocalDateTime.now());
        return toAjax(shopService.updateById(shop));
    }

    /**
     * 修改平台
     * @param
     * @return
     */
    @PutMapping("/platform")
    public AjaxResult edit(@RequestBody OShopPlatform platform)
    {
        return toAjax(platformService.updateById(platform));
    }

    /**
     * 删除店铺
     */
    @GetMapping("/pull_lasttime/{shopId}")
    public AjaxResult pullLasttime(@PathVariable Long shopId) {
        return success(pullLasttimeService.getLasttimeByShop(shopId));
    }

    @PostMapping("/pull_lasttime")
    public AjaxResult savePullLasttime(@RequestBody OShopPullLasttime form) {
        if (form.getShopId() == null || form.getPullType() == null || form.getLasttime() == null) {
            return error("请填写店铺、拉取类型和最后时间");
        }
        String pullType = String.valueOf(form.getPullType());
        OShopPullLasttime existing = pullLasttimeService.getLasttimeByShop(form.getShopId(), pullType);
        if (existing == null) {
            form.setPullType(pullType);
            form.setCreateTime(LocalDateTime.now());
            form.setUpdateTime(LocalDateTime.now());
            pullLasttimeService.save(form);
        } else {
            existing.setLasttime(form.getLasttime());
            existing.setUpdateTime(LocalDateTime.now());
            pullLasttimeService.updateById(existing);
        }
        return success();
    }

    @PreAuthorize("@ss.hasPermi('shop:shop:remove')")
	@DeleteMapping("/shop/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(shopService.removeBatchByIds(Arrays.stream(ids).toList()));
    }

//    @GetMapping("/logistics_status")
//    public TableDataInfo logisticsStatusList(Integer status, Integer shopType, Integer shopId)
//    {
//        return getDataTable(logisticsCompanyService.queryListByStatus(status,shopType, shopId));
//    }
//    /**
//     * 查询店铺列表logistics
//     */
//    @GetMapping("/logistics")
//    public TableDataInfo logisticsList(Integer type, Integer shopId, PageQuery pageQuery)
//    {
//        PageResult<OLogisticsCompany> result = logisticsCompanyService.queryPageList(type, shopId, pageQuery);
//        return getDataTable(result);
//    }
}
