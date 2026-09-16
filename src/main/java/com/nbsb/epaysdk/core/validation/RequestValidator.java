package com.nbsb.epaysdk.core.validation;

import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.api.entity.request.RefundCmd;
import com.nbsb.epaysdk.core.exception.EPayValidationException;

import java.util.regex.Pattern;

/**
 * Local checks so we fail before hitting the gateway.
 */
public final class RequestValidator {

    private static final Pattern AMOUNT = Pattern.compile("^(0|[1-9]\\d*)(\\.\\d{1,2})?$");
    private static final Pattern HTTP_URL = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    private RequestValidator() {
    }

    public static void validatePay(GetQRCmd cmd) {
        if (cmd == null) {
            throw new EPayValidationException("支付请求不能为空");
        }
        requireText(cmd.getName(), "商品名称");
        if (cmd.getName().length() > 127) {
            throw new EPayValidationException("商品名称不能超过 127 个字符");
        }
        requireText(cmd.getOrderNo(), "商户订单号");
        requireText(cmd.getAmount(), "金额");
        if (!AMOUNT.matcher(cmd.getAmount().trim()).matches()) {
            throw new EPayValidationException("金额格式非法，需为最多两位小数的数字: " + cmd.getAmount());
        }
        if ("0".equals(cmd.getAmount().trim()) || "0.0".equals(cmd.getAmount().trim()) || "0.00".equals(cmd.getAmount().trim())) {
            throw new EPayValidationException("金额必须大于 0");
        }
        if (cmd.getPayType() == null) {
            throw new EPayValidationException("支付方式不能为空");
        }
        requireUrl(cmd.getNotify_url(), "异步通知地址");
        requireUrl(cmd.getReturn_url(), "跳转地址");
    }

    public static void validateQuery(Query query) {
        if (query == null) {
            throw new EPayValidationException("查询请求不能为空");
        }
        if (query.getQuery_type() == null || (query.getQuery_type() != 1 && query.getQuery_type() != 2)) {
            throw new EPayValidationException("查询类型必须是 1(易支付订单号) 或 2(商户订单号)");
        }
        requireText(query.getOrder_no(), "订单号");
    }

    public static void validateRefund(RefundCmd cmd) {
        if (cmd == null) {
            throw new EPayValidationException("退款请求不能为空");
        }
        boolean hasTradeNo = notBlank(cmd.getTradeNo());
        boolean hasOutTradeNo = notBlank(cmd.getOutTradeNo());
        if (!hasTradeNo && !hasOutTradeNo) {
            throw new EPayValidationException("退款必须提供 trade_no 或 out_trade_no");
        }
        requireText(cmd.getMoney(), "退款金额");
        if (!AMOUNT.matcher(cmd.getMoney().trim()).matches()) {
            throw new EPayValidationException("退款金额格式非法: " + cmd.getMoney());
        }
    }

    private static void requireUrl(String value, String field) {
        requireText(value, field);
        if (!HTTP_URL.matcher(value.trim()).matches()) {
            throw new EPayValidationException(field + " 必须是 http/https 地址");
        }
    }

    private static void requireText(String value, String field) {
        if (!notBlank(value)) {
            throw new EPayValidationException(field + " 不能为空");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
