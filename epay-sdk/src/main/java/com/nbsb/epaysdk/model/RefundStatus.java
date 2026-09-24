package com.nbsb.epaysdk.model;

/** 退款受理不等于退款完成；未知状态不推断为成功。 */
public enum RefundStatus {
  ACCEPTED,
  PROCESSING,
  SUCCEEDED,
  FAILED,
  CLOSED,
  UNKNOWN
}
