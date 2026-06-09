package com.campus.trade.service;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.domain.entity.Arbitration;
public interface ArbitrationService {
    Arbitration arbitrate(Long adminId, ArbitrationRequest request);
    Arbitration reverseArbitration(Long adminId, ArbitrationRequest request);
    Arbitration getByDisputeId(Long disputeId);
}
