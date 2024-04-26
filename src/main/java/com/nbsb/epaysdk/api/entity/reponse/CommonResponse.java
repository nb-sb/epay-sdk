package com.nbsb.epaysdk.api.entity.reponse;


import java.util.Objects;

/**
* author: Wanghaonan @戏人看戏
* description: 通用响应实体
* create: 2024/4/21 15:52
*/
public class CommonResponse {

    private Integer code;

    private String msg;

    public static <T extends CommonResponse> boolean verifyResponse(T param) {
        if (Objects.equals(param.getCode(), "success")) {
            return true;
        }
        return false;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
