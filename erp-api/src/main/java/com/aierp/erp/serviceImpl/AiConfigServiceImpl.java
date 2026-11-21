package com.aierp.erp.serviceImpl;

import com.aierp.mapper.AiConfigMapper;
import com.aierp.model.entity.AiConfig;
import com.aierp.service.AiConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AiConfigServiceImpl extends ServiceImpl<AiConfigMapper, AiConfig> implements AiConfigService {
    private final com.aierp.erp.serviceImpl.ai.AiProviderProperties provider;

    @Override
    public AiConfig getDefaultConfig() {
        if (provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            AiConfig config = new AiConfig();
            config.setName("硅基流动（本地环境配置）");
            config.setApiEndpoint(provider.getEndpoint());
            config.setModelName(provider.getModel());
            config.setApiKey(provider.getApiKey().trim());
            config.setIsEnabled(1);
            config.setIsDefault(1);
            return config;
        }
        return lambdaQuery()
                .eq(AiConfig::getIsDefault, 1)
                .eq(AiConfig::getIsEnabled, 1)
                .one();
    }

    @Transactional
    @Override
    public boolean setDefault(Long id) {
        lambdaUpdate()
            .eq(AiConfig::getIsDefault, 1)
            .set(AiConfig::getIsDefault, 0)
            .update();
        // 设置新的默认
        AiConfig config = new AiConfig();
        config.setId(id);
        config.setIsDefault(1);
        return updateById(config);
    }
}
