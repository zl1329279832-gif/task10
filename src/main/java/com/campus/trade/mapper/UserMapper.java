package com.campus.trade.mapper;

import com.campus.trade.model.entity.User;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface UserMapper {
    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    User selectByPhone(@Param("phone") String phone);
    int insert(User user);
    int updateById(User user);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    List<User> selectAll(@Param("offset") int offset, @Param("limit") int limit);
    long countAll();
}
