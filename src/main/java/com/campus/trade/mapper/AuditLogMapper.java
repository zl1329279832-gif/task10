package com.campus.trade.mapper;
import com.campus.trade.domain.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface AuditLogMapper {
    int insert(AuditLog log);
    List<AuditLog> findByModule(@Param("module") String module, @Param("offset") int offset, @Param("limit") int limit);
    long countByModule(@Param("module") String module);
    List<AuditLog> findByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);
    List<AuditLog> findByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);
}
