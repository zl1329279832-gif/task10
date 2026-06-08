package com.campus.trade.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface UserRoleMapper {
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);
    int deleteByUserId(@Param("userId") Long userId);
}
