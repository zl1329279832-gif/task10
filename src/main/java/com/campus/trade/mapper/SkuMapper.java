package com.campus.trade.mapper;
import com.campus.trade.domain.entity.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface SkuMapper {
    Sku findById(@Param("id") Long id);
    List<Sku> findByProductId(@Param("productId") Long productId);
    int insert(Sku sku);
    int updateStock(@Param("id") Long id, @Param("stock") Integer stock);
    int lockStock(@Param("id") Long id, @Param("quantity") int quantity);
    int releaseLockStock(@Param("id") Long id, @Param("quantity") int quantity);
    int deductLockStock(@Param("id") Long id, @Param("quantity") int quantity);
}
