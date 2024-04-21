package com.nbsb.epaysdk.api.entity.reponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: Wanghaonan @戏人看戏
 * @description: 码支付返回类型
 * @create: 2024/4/21 17:52
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MZFOrderInfoResponse extends CommonResponse {
    private Object data;
}
