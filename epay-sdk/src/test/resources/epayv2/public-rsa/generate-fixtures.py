#!/usr/bin/env python3
"""公开测试材料生成器；仅离线运行，不访问 SDK/JCA，不写文件、不联网。

来源/取证日期：2026-09-24，文档派生合成样本，非真实联调。
https://mazhifupay.com/28/6/568.html ：签名步骤、验签步骤、页面跳转支付、
统一下单接口、订单查询、支付结果通知、订单退款、订单退款查询。
https://docs.xarr.cn/merchant/api/epayn ：密钥与签名、三、订单查询、四、异步回调。
query-b 的 status=1 在参考 V2 为已支付，在 XArr 为待支付；不混用状态表。

方法（工作目录任意，Python 3 标准库 + OpenSSL；首次生成使用 OpenSSL 3.6.2）：
  python3 generate-fixtures.py --new-public-test-keys  # 输出四组独立测试密钥的 JSON
  python3 generate-fixtures.py --emit                # 用已固定的测试密钥输出完整 fixture
  python3 generate-fixtures.py --check               # 只读比对固定原文及签名字节
输出由维护者审核后保存为同目录 fixtures.json。普通 Java 测试不运行此脚本。
--emit 可逐字复现已固定密钥的向量；重新生成随机密钥当然会改变向量，禁止自动轮换。

这些私钥故意公开、无密码、仅测试用；从未注册到任何支付平台，绝非商户凭据。
OpenSSL genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048；pkey -pubout 导出 X509。
签名：dgst -sha256 -sign KEY -sigopt rsa_padding_mode:pkcs1，标准 Base64。
原文 UTF-8，无 BOM、无结尾换行；未 URL 编码、不附加密钥；0 保留、null/空串排除。
PEM 通过匿名管道传递，不创建临时密钥文件；只消费本目录已公开的测试密钥。
"""

import argparse
import base64
import json
import os
from pathlib import Path
import subprocess

FIXTURE = Path(__file__).with_name("fixtures.json")
SOURCE = "https://mazhifupay.com/28/6/568.html"


def openssl(*args, data=b"", pass_fds=()):
    return subprocess.run(["openssl", *args], input=data, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True, pass_fds=pass_fds).stdout


def with_key(pem, args, data):
    read_fd, write_fd = os.pipe()
    try:
        os.write(write_fd, pem.encode("ascii"))
        os.close(write_fd)
        write_fd = None
        return openssl(*args, f"/dev/fd/{read_fd}", data=data, pass_fds=(read_fd,))
    finally:
        os.close(read_fd)
        if write_fd is not None:
            os.close(write_fd)


def sign(pem, text):
    return with_key(pem, ("dgst", "-sha256", "-sigopt", "rsa_padding_mode:pkcs1", "-sign"),
                    text.encode("utf-8"))


def canonical(data):
    return "&".join(f"{name}={value}" for name, value in sorted(data.items())
                    if name not in ("sign", "sign_type") and value is not None and value != "")


def samples():
    common = {"pid": "1001", "timestamp": "1790208000", "sign_type": "RSA"}
    payment = dict(common, money="1.00", name='商品 &+"测试"=/%',
                   notify_url="https://shop.example/notify?a=1&b=%2B", out_trade_no="订单 &+=",
                   return_url="https://shop.example/return", param="")
    create = dict(payment, clientip="192.0.2.1", is_applet="0", method="jsapi",
                  sub_appid="wx-test", sub_openid="用户 &+=", type="wxpay")
    base = {"code": 0, "timestamp": "1790208000", "sign_type": "RSA"}
    order = dict(base, pid="1001", trade_no="T1", out_trade_no="订单 &+=", money="1.00",
                 status=1, type="wxpay", name='商品 &+"测试"=/%', param="原样参数 &+=/%",
                 future_zero=0, future_empty="", future_null=None)
    refund = dict(base, refund_no="R1", out_refund_no="退款 &+=", trade_no="T1",
                  money="0.50", reducemoney="0.50", msg="受理 &+=")
    # 原文逐项手写，以便复核排序、零值及空值规则，不由 SDK 或同一排序器计算预期。
    entries = [
        ("submit-request", "merchant-a", "页面跳转支付", payment,
         'money=1.00&name=商品 &+"测试"=/%&notify_url=https://shop.example/notify?a=1&b=%2B&out_trade_no=订单 &+=&pid=1001&return_url=https://shop.example/return&timestamp=1790208000'),
        ("create-request", "merchant-a", "统一下单接口", create,
         'clientip=192.0.2.1&is_applet=0&method=jsapi&money=1.00&name=商品 &+"测试"=/%&notify_url=https://shop.example/notify?a=1&b=%2B&out_trade_no=订单 &+=&pid=1001&return_url=https://shop.example/return&sub_appid=wx-test&sub_openid=用户 &+=&timestamp=1790208000&type=wxpay'),
        ("query-request", "merchant-a", "订单查询", dict(common, out_trade_no="订单 &+="),
         'out_trade_no=订单 &+=&pid=1001&timestamp=1790208000'),
        ("refund-request", "merchant-a", "订单退款", dict(common, trade_no="T1", money="0.50", out_refund_no="退款 &+="),
         'money=0.50&out_refund_no=退款 &+=&pid=1001&timestamp=1790208000&trade_no=T1'),
        ("refundquery-request", "merchant-a", "订单退款查询", dict(common, out_refund_no="退款 &+="),
         'out_refund_no=退款 &+=&pid=1001&timestamp=1790208000'),
        ("create-response", "platform-a", "统一下单接口 / 返回参数说明", dict(base, trade_no="T1", pay_type="jsapi",
             pay_info='{"appId":"wx-test","zero":0,"note":"中文 &+="}', msg="", future_null=None),
         'code=0&pay_info={"appId":"wx-test","zero":0,"note":"中文 &+="}&pay_type=jsapi&timestamp=1790208000&trade_no=T1'),
        ("query-response", "platform-a", "订单查询 / 返回参数说明 / 支付状态列表", order,
         'code=0&future_zero=0&money=1.00&name=商品 &+"测试"=/%&out_trade_no=订单 &+=&param=原样参数 &+=/%&pid=1001&status=1&timestamp=1790208000&trade_no=T1&type=wxpay'),
        ("refund-response", "platform-a", "订单退款 / 返回参数说明", refund,
         'code=0&money=0.50&msg=受理 &+=&out_refund_no=退款 &+=&reducemoney=0.50&refund_no=R1&timestamp=1790208000&trade_no=T1'),
        ("refundquery-response", "platform-a", "订单退款查询 / 返回参数说明", dict(refund, out_trade_no="订单 &+=", status=1),
         'code=0&money=0.50&msg=受理 &+=&out_refund_no=退款 &+=&out_trade_no=订单 &+=&reducemoney=0.50&refund_no=R1&status=1&timestamp=1790208000&trade_no=T1'),
        ("notify-a", "platform-a", "支付结果通知", {
            "pid": "1001", "trade_no": "T1", "out_trade_no": "订单 &+=", "money": "1.00",
            "name": '商品 &+"测试"=/%', "param": "原样参数 &+=/%", "trade_status": "TRADE_SUCCESS",
            "type": "wxpay", "timestamp": "1790208000", "sign_type": "RSA", "future_zero": "0", "future_empty": ""},
         'future_zero=0&money=1.00&name=商品 &+"测试"=/%&out_trade_no=订单 &+=&param=原样参数 &+=/%&pid=1001&timestamp=1790208000&trade_no=T1&trade_status=TRADE_SUCCESS&type=wxpay'),
        ("query-b-request", "merchant-b", "订单查询", {
            "pid": "1002", "timestamp": "1790208060", "sign_type": "RSA", "out_trade_no": "B-订单 &+="},
         'out_trade_no=B-订单 &+=&pid=1002&timestamp=1790208060'),
        ("query-b-response", "platform-b", "订单查询 / 支付状态列表", {
            "code": 0, "pid": "1002", "timestamp": "1790208060", "sign_type": "RSA", "out_trade_no": "B-订单 &+=",
            "trade_no": "T2", "money": "2.00", "status": 1, "type": "alipay"},
         'code=0&money=2.00&out_trade_no=B-订单 &+=&pid=1002&status=1&timestamp=1790208060&trade_no=T2&type=alipay'),
        ("notify-b", "platform-b", "支付结果通知", {
            "pid": "1002", "timestamp": "1790208060", "sign_type": "RSA", "out_trade_no": "B-订单 &+=",
            "trade_no": "T2", "money": "2.00", "trade_status": "TRADE_SUCCESS", "type": "alipay"},
         'money=2.00&out_trade_no=B-订单 &+=&pid=1002&timestamp=1790208060&trade_no=T2&trade_status=TRADE_SUCCESS&type=alipay'),
    ]
    return entries


def emit(keys):
    vectors = {}
    for name, role, chapter, data, text in samples():
        assert canonical(data) == text, f"手写原文不符：{name}"
        signature = sign(keys[role]["privatePem"], text)
        assert len(signature) == 256, "只能使用 2048 位公开测试密钥"
        encoded = base64.b64encode(signature).decode("ascii")
        signed = dict(data, sign=encoded)
        vectors[name] = {"source": SOURCE, "chapter": chapter, "key": role,
                         "canonical": text, "signature": encoded}
        if name.endswith("-response"):
            vectors[name]["body"] = json.dumps(signed, ensure_ascii=False, separators=(",", ":"))
        else:
            vectors[name]["parameters"] = signed
    return {"notice": "公开测试密钥，绝非商户凭据；文档派生合成样本，非真实联调，禁止用于生产",
            "evidenceDate": "2026-09-24", "generator": "OpenSSL 3.6.2; RSA-2048; SHA256; PKCS1-v1_5; UTF-8",
            "keys": keys, "vectors": vectors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--new-public-test-keys", action="store_true")
    modes.add_argument("--emit", action="store_true")
    modes.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.new_public_test_keys:
        keys = {}
        for role in ("merchant-a", "platform-a", "merchant-b", "platform-b"):
            private = openssl("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048")
            public = openssl("pkey", "-pubout", data=private)
            keys[role] = {"privatePem": private.decode("ascii"), "publicPem": public.decode("ascii")}
        print(json.dumps({"keys": keys}, ensure_ascii=False, indent=2))
        return
    if not FIXTURE.is_file():
        raise SystemExit("RED：缺少 fixtures.json，禁止用运行时签名伪装固定向量")
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    expected = emit(fixture["keys"])
    if args.emit:
        print(json.dumps(expected, ensure_ascii=False, indent=2))
        return
    assert fixture == expected, "固定样本与离线 OpenSSL 重现结果不一致"
    public_keys = set()
    for role, key in fixture["keys"].items():
        public = openssl("pkey", "-pubout", data=key["privatePem"].encode("ascii")).decode("ascii")
        assert public == key["publicPem"], role
        public_keys.add(public)
    assert len(public_keys) == 4, "商户/平台及 A/B 必须四组独立密钥"
    print(f"PASS：{len(fixture['vectors'])} 个固定向量与 OpenSSL 字节一致；四组独立公开测试密钥")


if __name__ == "__main__":
    main()
