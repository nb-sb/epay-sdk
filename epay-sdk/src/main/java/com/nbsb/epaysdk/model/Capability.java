package com.nbsb.epaysdk.model;

/** 能力只表达 SDK 与站点配置支持，不代表商户已经开通。 */
public enum Capability {
  CREATE_PAYMENT,
  QUERY_ORDER,
  VERIFY_NOTIFICATION,
  REFUND,
  QUERY_REFUND,
  QUERY_MERCHANT,
  LIST_ORDERS
}
