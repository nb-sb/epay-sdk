package com.nbsb.epaysdk.model;

/** 未识别的网关状态必须映射到 UNKNOWN，退款状态另行表示。 */
public enum OrderStatus {
  PENDING,
  PAID,
  CLOSED,
  FAILED,
  UNKNOWN
}
