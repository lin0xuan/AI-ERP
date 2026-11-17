package com.aierp.service;

import com.aierp.model.entity.AiConfig;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AiConfigService extends IService<AiConfig> {
    boolean setDefault(Long id);
    AiConfig getDefaultConfig();
}
