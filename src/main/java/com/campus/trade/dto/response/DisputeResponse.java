package com.campus.trade.dto.response;
import com.campus.trade.domain.entity.Arbitration;
import com.campus.trade.domain.entity.DisputeEvidence;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class DisputeResponse { private Long id; private String disputeNo; private Long orderId; private String orderNo; private Long initiatorId; private Long respondentId; private String reason; private String evidenceUrls; private String status; private String statusDesc; private LocalDateTime createdAt; private List<DisputeEvidence> evidences; private Arbitration arbitration; }
