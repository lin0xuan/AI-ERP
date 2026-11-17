package com.aierp.erp.controller.sys;

import com.aierp.common.AjaxResult;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.common.TableDataInfo;
import com.aierp.erp.notify.DingTalkNotifier;
import com.aierp.erp.notify.FeishuNotifier;
import com.aierp.erp.notify.WeChatNotifier;
import com.aierp.model.entity.SysAlertChannel;
import com.aierp.security.common.BaseController;
import com.aierp.service.ISysAlertChannelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/api/sys-api/alert/channel")
public class SysAlertChannelController extends BaseController {
    private final ISysAlertChannelService channelService;
    private final FeishuNotifier feishuNotifier;
    private final DingTalkNotifier dingTalkNotifier;
    private final WeChatNotifier weChatNotifier;

    @GetMapping("/list")
    public TableDataInfo list(SysAlertChannel bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SysAlertChannel> w = new LambdaQueryWrapper<>();
        w.orderByDesc(SysAlertChannel::getCreateTime);
        Page<SysAlertChannel> page = channelService.page(new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize()), w);
        return getDataTable(PageResult.build(page));
    }

    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(channelService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody SysAlertChannel entity) {
        entity.setCreateTime(LocalDateTime.now());
        return toAjax(channelService.save(entity));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody SysAlertChannel entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return toAjax(channelService.updateById(entity));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/test")
    public AjaxResult test(@RequestBody Map<String, Object> body) {
        String channelType = (String) body.get("channelType");
        String webhookUrl = (String) body.get("webhookUrl");
        String secret = (String) body.get("secret");

        if (channelType == null || webhookUrl == null) {
            return error("参数不完整");
        }

        boolean ok;
        try {
            ok = switch (channelType) {
                case "FEISHU" -> feishuNotifier.notify(webhookUrl, "【测试消息】AI ERP 通知渠道", "这是一条测试消息，如果您收到此消息，说明通知渠道配置正确。");
                case "DINGTALK" -> dingTalkNotifier.notify(webhookUrl, secret != null ? secret : "", "【测试消息】AI ERP 通知渠道", "这是一条测试消息，如果您收到此消息，说明通知渠道配置正确。");
                case "WECHAT" -> weChatNotifier.notify(webhookUrl, "【测试消息】AI ERP 通知渠道", "这是一条测试消息，如果您收到此消息，说明通知渠道配置正确。");
                default -> false;
            };
        } catch (Exception e) {
            return error("发送失败: " + e.getMessage());
        }

        if (ok) {
            return success("消息发送成功");
        } else {
            return error("消息发送失败，请检查Webhook地址和密钥是否正确");
        }
    }

    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(channelService.removeByIds(Arrays.asList(ids)));
    }
}
