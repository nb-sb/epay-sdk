package com.nbsb.epaysdk.epaybase.enumeration;
/**
* author: Wanghaonan @戏人看戏
* description: 设备枚举类
* create: 2024/4/21 11:43
*/
public enum  DeviceType {
    /**
     * pc	    电脑浏览器
     * mobile	手机浏览器
     * qq	    手机QQ内浏览器
     * wechat	微信内浏览器
     * alipay	支付宝客户端
     * jump	    仅返回支付跳转url
     */
    PC("pc"),
    MOBILE("mobile"),
    QQ("qq"),
    WECHAT("wechat"),
    ALIPAY("alipay"),
    JUMP("jump");

    private String deviceTypeName;

    DeviceType(String deviceTypeName) {
        this.deviceTypeName = deviceTypeName;
    }

    public String getDeviceTypeName() {
        return deviceTypeName;
    }
}
