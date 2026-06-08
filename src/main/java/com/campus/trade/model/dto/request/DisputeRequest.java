package com.campus.trade.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisputeRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    private String refundNo;

    @NotBlank(message = "争议原因不能为空")
    private String reason;

    private String evidence;
}
