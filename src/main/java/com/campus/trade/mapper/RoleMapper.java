package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface RoleMapper {
    @Select("SELECT * FROM t_role WHERE role_code = #{roleCode}")
    Role findByRoleCode(@Param("roleCode") String roleCode);
    @Select("SELECT * FROM t_role WHERE id = #{id}")
    Role findById(@Param("id") Long id);
}
