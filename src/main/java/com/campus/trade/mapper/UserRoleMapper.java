package com.campus.trade.mapper;

import com.campus.trade.model.entity.UserRole;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface UserRoleMapper {
    List<UserRole> selectByUserId(@Param("userId") Long userId);
    int insert(UserRole userRole);
    int deleteByUserIdAndRole(@Param("userId") Long userId, @Param("role") String role);
    int countByUserIdAndRole(@Param("userId") Long userId, @Param("role") String role);
}
