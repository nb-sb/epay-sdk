package com.nbsb.epaysdk.api.entity.reponse;

/**
 * Shared gateway envelope. YZF uses code=1 for success; MZF is normalized to the same.
 */
public class CommonResponse {

    private Integer code;

    private String msg;

    /**
     * @deprecated always compared Integer to "success"; use {@link #isSuccess()}.
     */
    @Deprecated
    public static <T extends CommonResponse> boolean verifyResponse(T param) {
        return param != null && param.isSuccess();
    }

    public boolean isSuccess() {
        return code != null && code == 1;
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
