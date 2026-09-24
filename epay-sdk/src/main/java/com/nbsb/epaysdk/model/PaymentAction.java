package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;
import java.net.URI;

/** 支付动作只携带数据，SDK 不执行 HTML、脚本、应用参数或远端抓取。 */
public sealed interface PaymentAction {
  record Redirect(URI url) implements PaymentAction {
    public Redirect {
      url = Checks.httpUri(url);
    }

    @Override
    public String toString() {
      return "Redirect[已遮蔽]";
    }
  }

  record QrCode(String content) implements PaymentAction {
    public QrCode {
      content = Checks.text(content);
    }

    @Override
    public String toString() {
      return "QrCode[已遮蔽]";
    }
  }

  record QrImage(URI url) implements PaymentAction {
    public QrImage {
      url = Checks.httpUri(url);
    }

    @Override
    public String toString() {
      return "QrImage[已遮蔽]";
    }
  }

  record UrlScheme(String url) implements PaymentAction {
    public UrlScheme {
      url = Checks.text(url);
    }

    @Override
    public String toString() {
      return "UrlScheme[已遮蔽]";
    }
  }

  record Form(String html) implements PaymentAction {
    public Form {
      html = Checks.text(html);
    }

    @Override
    public String toString() {
      return "Form[已遮蔽]";
    }
  }

  record Html(String html) implements PaymentAction {
    public Html {
      html = Checks.text(html);
    }

    @Override
    public String toString() {
      return "Html[已遮蔽]";
    }
  }

  /** 保留网关签名覆盖的原始载荷，调用方应按支付渠道安全消费。 */
  record JsApi(String payload) implements PaymentAction {
    public JsApi {
      payload = Checks.text(payload);
    }

    @Override
    public String toString() {
      return "JsApi[已遮蔽]";
    }
  }

  record App(String payload) implements PaymentAction {
    public App {
      payload = Checks.text(payload);
    }

    @Override
    public String toString() {
      return "App[已遮蔽]";
    }
  }

  /** V2 wxplugin：保留已签名的插件参数字符串，不解析重建或执行。 */
  record WechatPlugin(String payload) implements PaymentAction {
    public WechatPlugin {
      payload = Checks.text(payload);
    }

    @Override
    public String toString() {
      return "WechatPlugin[已遮蔽]";
    }
  }

  /** V2 wxapp：保留已签名的小程序参数字符串，包括字符串 extraData。 */
  record MiniProgram(String payload) implements PaymentAction {
    public MiniProgram {
      payload = Checks.text(payload);
    }

    @Override
    public String toString() {
      return "MiniProgram[已遮蔽]";
    }
  }

  /** V2 scan：仅保留已签名的订单信息，不从未知字段推断状态或代替业务记账。 */
  record ScanResult(String payload) implements PaymentAction {
    public ScanResult {
      payload = Checks.text(payload);
    }

    @Override
    public String toString() {
      return "ScanResult[已遮蔽]";
    }
  }
}
