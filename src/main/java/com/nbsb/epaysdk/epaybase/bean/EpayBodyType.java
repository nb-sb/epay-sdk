package com.nbsb.epaysdk.epaybase.bean;

import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;

/**
* author: Wanghaonan @戏人看戏
* description: 支付类型
* create: 2024/4/21 15:12
*/
public class EpayBodyType {
    private String is_mzf = "false";
    private DeviceType type = DeviceType.JUMP;

    public String getIs_mzf() {
        return is_mzf;
    }

    public void setIs_mzf(String is_mzf) {
        this.is_mzf = is_mzf;
    }

    public DeviceType getDeviceType() {
        return type;
    }

    public void setType(DeviceType type) {
        this.type = type;
    }
}
