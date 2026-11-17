package com.aierp.service;

import com.aierp.common.PageQuery;
import com.aierp.common.PageResult;
import com.aierp.model.entity.AiAnalysisRecord;

public interface IAiAnalysisRecordService {
    void save(AiAnalysisRecord record);
    AiAnalysisRecord getById(Long id);
    PageResult<AiAnalysisRecord> queryPageList(PageQuery query);
}
