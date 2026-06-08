package com.campus.trade.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class AlipayUtil {

    private AlipayUtil() {}

    /**
     * Simulate Alipay signature verification for sandbox.
     * In production, use AlipaySignature.rsaCheckV1 from alipay-sdk-java.
     */
    public static boolean verifySign(Map<String, String> params, String alipayPublicKey) {
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return false;
        }

        // In sandbox mode, accept "mock_sign_for_sandbox" for testing
        if ("mock_sign_for_sandbox".equals(sign)) {
            return true;
        }

        // Build sign content: sort params alphabetically, exclude sign and sign_type
        String content = params.entrySet().stream()
                .filter(e -> !"sign".equals(e.getKey()) && !"sign_type".equals(e.getKey()))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        // In real implementation, verify RSA2 signature using alipayPublicKey
        // For sandbox/testing, we use HMAC-SHA256 with the public key as secret
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(alipayPublicKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(sign);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse request parameters from HttpServletRequest into a Map
     */
    public static Map<String, String> parseParams(Map<String, String[]> requestParams) {
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String[] values = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(values[i]);
            }
            params.put(entry.getKey(), sb.toString());
        }
        return params;
    }
}
