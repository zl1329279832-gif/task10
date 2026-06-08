package com.campus.trade.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArbitrateRequest {
    @NotBlank(message = "仲裁结果不能为空")
    private String result; // BUYER_WIN or SELLER_WIN

    private String remark;
}
