package com.aierp.erp.serviceImpl;

import com.aierp.mapper.SysAlertChannelMapper;
import com.aierp.model.entity.SysAlertChannel;
import com.aierp.service.ISysAlertChannelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SysAlertChannelServiceImpl extends ServiceImpl<SysAlertChannelMapper, SysAlertChannel>
        implements ISysAlertChannelService {
}
