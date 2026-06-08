package com.campus.trade.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNoGenerator() {}

    public static String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    public static String generateRefundNo() {
        return "REF" + LocalDateTime.now().format(FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    public static String generateDisputeNo() {
        return "DIS" + LocalDateTime.now().format(FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    public static String generateSettlementNo() {
        return "SET" + LocalDateTime.now().format(FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
