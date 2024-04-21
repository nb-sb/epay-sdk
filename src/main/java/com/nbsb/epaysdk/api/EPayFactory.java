package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.api.Impl.EPayMZF;
import com.nbsb.epaysdk.api.Impl.EPayYZF;
import com.nbsb.epaysdk.epaybase.enumeration.PayType;

/**
* @author: Wanghaonan @戏人看戏
* @description: 易支付创建工厂，用户创建易支付还是码支付
* @create: 2024/4/21 11:42
*/
public class EPayFactory {
    //判断创建的是易支付还是码支付
    public static EPay create(PayType payType) {
        if (payType == PayType.MZF) {
            return new EPayMZF();
        } else if (payType == PayType.YZF){
            return new EPayYZF();
        }
        throw new RuntimeException("未找到的支付方式（支持码支付/易支付）");
    }
}
