package com.aierp.service;

import com.aierp.model.entity.SysMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface ISysMessageService {
    void save(SysMessage msg);
    List<SysMessage> getUnread();
    long countUnread();
    void markRead(Long id);
    void markAllRead();
    List<SysMessage> getFailedNotify();

    /** title 为空时只按类型判断。用于同一条预警在窗口期内不重复写入。 */
    boolean existsRecent(String type, String title, LocalDateTime since);
}
