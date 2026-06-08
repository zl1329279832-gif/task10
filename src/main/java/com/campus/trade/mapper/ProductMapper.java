package com.campus.trade.mapper;

import com.campus.trade.model.entity.Product;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ProductMapper {
    Product selectById(@Param("id") Long id);
    List<Product> selectByCondition(@Param("sellerId") Long sellerId, @Param("category") String category,
                                     @Param("keyword") String keyword, @Param("status") Integer status,
                                     @Param("offset") int offset, @Param("limit") int limit);
    long countByCondition(@Param("sellerId") Long sellerId, @Param("category") String category,
                          @Param("keyword") String keyword, @Param("status") Integer status);
    int insert(Product product);
    int updateById(Product product);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    int deductStock(@Param("id") Long id, @Param("quantity") int quantity, @Param("version") int version);
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);
}
