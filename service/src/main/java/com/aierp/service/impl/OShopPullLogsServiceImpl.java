package com.aierp.service.impl;

import com.aierp.mapper.OShopPullLogsMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.model.entity.OShopPullLogs;

import com.aierp.service.OShopPullLogsService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
* @author TW
* @description 针对表【sys_shop_pull_logs(更新日志表)】的数据库操作Service实现
* @createDate 2024-03-22 17:43:38
*/
@AllArgsConstructor
@Service
public class OShopPullLogsServiceImpl extends ServiceImpl<OShopPullLogsMapper, OShopPullLogs>
    implements OShopPullLogsService {
    private final OShopPullLogsMapper mapper;

    @Override
    public PageResult<OShopPullLogs> queryPageList(OShopPullLogs bo, PageQuery pageQuery) {
        LambdaQueryWrapper<OShopPullLogs> queryWrapper = new LambdaQueryWrapper<OShopPullLogs>();
        queryWrapper.eq(bo.getShopId()!=null, OShopPullLogs::getShopId,bo.getShopId());
        queryWrapper.eq(bo.getShopType()!=null, OShopPullLogs::getShopType,bo.getShopType());
        queryWrapper.eq(StringUtils.hasText(bo.getPullType()), OShopPullLogs::getPullType,bo.getPullType());
        Page<OShopPullLogs> pages = mapper.selectPage(pageQuery.build(), queryWrapper);

        return PageResult.build(pages);
    }
}




