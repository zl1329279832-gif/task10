package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProductMapper {
    Product findById(@Param("id") Long id);
    List<Product> findBySellerId(@Param("sellerId") Long sellerId, @Param("status") Integer status);
    List<Product> findAll(@Param("status") Integer status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("status") Integer status, @Param("keyword") String keyword);
    int insert(Product product);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
