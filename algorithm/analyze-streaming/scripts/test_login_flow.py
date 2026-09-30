# -*- coding: utf-8 -*-
"""验证 47.111.6.75:8091 平台登录流程能否用 Python 程序化复现。

流程: RSA 加密密码 -> POST /upc/user/login -> 拿 token -> 调受保护接口。
"""
import base64
import json
import time
import urllib.request

BASE = "http://47.111.6.75:8091"

# 从前端 app.js 提取的 JSEncrypt 公钥 (PKCS#1 v1.5, 2048位)
PUBKEY_B64 = (
    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArP9YDcoZOEm2sJixPWcL"
    "B9+vxhDHZIajajeBmtm8qrlQVNKp9OtNCdNpZTp4/AjUF6lkp1U8h0rn9CgOGIM3"
    "DFGU/Q1dSTnq1KSDgFv2tB6YX3XMTGTpm4sAFk5dF4GMmhMl3Nk/bF/9vf6sdEGA"
    "c/Ngr9cLeuRjK0N3GgXnSBrsp1rOVX7DdHXrkIHPf4539H58Uc+AlZLFmdCfQpuB"
    "eVT69dSeYbZePb84A3RGN4zZM3cM64A9KRHGgnssokrPwnz6QeVu+76zbQLKOe81"
    "S3Doy+9Q+fD/rk+c+8OsaVJ22kQyb9oW3AEnEF+a3zX5JX2W4Ab9N2ukEHcU8geU"
    "jQIDAQAB"
)

USERNAME = "admin"
PASSWORD = "123456"


def rsa_encrypt_login_password(password: str, pubkey_b64: str) -> str:
    """复现前端登录加密: RSA( PKCS1v15, password + "|" + 毫秒时间戳 ) -> base64。

    前端逻辑 (js/182.a77acff9.js):
        const s = new JSEncrypt(); s.setPublicKey(f.J1);
        const t = this.form.password + "|" + Date.now();
        const e = s.encrypt(t);
    """
    from cryptography.hazmat.primitives.serialization import load_der_public_key
    from cryptography.hazmat.primitives.asymmetric import padding

    plaintext = f"{password}|{int(time.time() * 1000)}"
    der = base64.b64decode(pubkey_b64)
    key = load_der_public_key(der)
    ct = key.encrypt(plaintext.encode("utf-8"), padding.PKCS1v15())
    return base64.b64encode(ct).decode("ascii")


def post_json(path, payload, headers=None):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json;charset=UTF-8", "tenantid": "1", **(headers or {})},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return resp.status, dict(resp.headers), json.loads(resp.read().decode("utf-8"))


def get_json(path, token):
    req = urllib.request.Request(
        BASE + path,
        headers={
            "authorization": "Bearer " + token,
            "tenantid": "1",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    # 1. RSA 加密密码 (密码|时间戳)
    enc_pwd = rsa_encrypt_login_password(PASSWORD, PUBKEY_B64)
    print("[1] RSA encrypted password (base64, len=%d): %s..." % (len(enc_pwd), enc_pwd[:60]))

    # 2. 登录
    status, headers, body = post_json("/upc/user/login", {"username": USERNAME, "password": enc_pwd})
    print("[2] login status=%s code=%s message=%s" % (status, body.get("code"), body.get("message")))
    token = (body.get("data") or {}).get("token") or headers.get("accesstoken") or ""
    print("    token: %s..." % token[:50] if token else "    token: <EMPTY>")

    if not token:
        print("!! 登录失败, 完整响应:", json.dumps(body, ensure_ascii=False)[:500])
        return

    # 3. 用 token 调智能体列表接口验证
    agents = get_json("/api/v1/aibody/list?keyword=&page=1&pageSize=20", token)
    items = (agents.get("data") or {}).get("list") or []
    print("[3] aibody/list code=%s, %d agents:" % (agents.get("code"), len(items)))
    for it in items:
        print("    - %s -> %s" % (it.get("name"), it.get("code")))


if __name__ == "__main__":
    main()
