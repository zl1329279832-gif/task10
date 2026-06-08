package com.campus.trade.mapper;
import com.campus.trade.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface UserMapper {
    User findById(@Param("id") Long id);
    User findByUsername(@Param("username") String username);
    int insert(User user);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
