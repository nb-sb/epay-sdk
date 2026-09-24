package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;

/** 使用从零开始的偏移量分页；协议可进一步限制单页大小。 */
public record OrderListRequest(int offset, int limit) {
  public OrderListRequest {
    if (offset < 0 || limit <= 0 || limit > 1000) throw EPayException.validation();
  }
}
