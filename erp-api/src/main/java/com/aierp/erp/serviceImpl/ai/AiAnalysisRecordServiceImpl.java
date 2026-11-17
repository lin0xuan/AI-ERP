package com.aierp.erp.serviceImpl.ai;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.mapper.AiAnalysisRecordMapper;
import com.aierp.model.entity.AiAnalysisRecord;
import com.aierp.service.IAiAnalysisRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisRecordServiceImpl implements IAiAnalysisRecordService {

    private final AiAnalysisRecordMapper mapper;

    @Override
    public void save(AiAnalysisRecord record) {
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.updateById(record);
        }
    }

    @Override
    public AiAnalysisRecord getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public PageResult<AiAnalysisRecord> queryPageList(PageQuery query) {
        LambdaQueryWrapper<AiAnalysisRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AiAnalysisRecord::getCreatedTime);
        Page<AiAnalysisRecord> page = mapper.selectPage(query.build(), wrapper);
        return PageResult.build(page);
    }
}
