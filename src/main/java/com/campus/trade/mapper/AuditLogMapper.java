package com.campus.trade.mapper;

import com.campus.trade.model.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface AuditLogMapper {
    int insert(AuditLogEntity log);
    List<AuditLogEntity> selectByCondition(@Param("module") String module, @Param("action") String action,
                                            @Param("targetType") String targetType, @Param("targetId") String targetId,
                                            @Param("userId") Long userId,
                                            @Param("offset") int offset, @Param("limit") int limit);
    long countByCondition(@Param("module") String module, @Param("action") String action,
                          @Param("targetType") String targetType, @Param("targetId") String targetId,
                          @Param("userId") Long userId);
}
