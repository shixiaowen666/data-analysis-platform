# 登录密码 RSA 加密方案

## 概览

前端使用 RSA 公钥加密密码后才传输到后端，后端用私钥解密还原明文，避免密码明文出现在网络传输、日志、中间件中。

```
前端：plainPassword + "|" + timestamp  →  RSA公钥加密  →  Base64密文  →  POST /login
后端：Base64密文  →  RSA私钥解密  →  拆分出密码+时间戳  →  校验时间戳±5min  →  密码校验
```

---

## 密钥对

> 由 `openssl` 生成，2048位 RSA。私钥注入后端环境变量（不可提交代码仓库），公钥写死在前端代码（公开信息）。

### 后端环境变量 `RSA_PRIVATE_KEY`

```
MIIEowIBAAKCAQEArP9YDcoZOEm2sJixPWcLB9+vxhDHZIajajeBmtm8qrlQVNKp9OtNCdNpZTp4/AjUF6lkp1U8h0rn9CgOGIM3DFGU/Q1dSTnq1KSDgFv2tB6YX3XMTGTpm4sAFk5dF4GMmhMl3Nk/bF/9vf6sdEGAc/Ngr9cLeuRjK0N3GgXnSBrsp1rOVX7DdHXrkIHPf4539H58Uc+AlZLFmdCfQpuBeVT69dSeYbZePb84A3RGN4zZM3cM64A9KRHGgnssokrPwnz6QeVu+76zbQLKOe81S3Doy+9Q+fD/rk+c+8OsaVJ22kQyb9oW3AEnEF+a3zX5JX2W4Ab9N2ukEHcU8geUjQIDAQABAoIBAATkSU8sgr2NeHNYzsXO3Kg8/XMP+H9oC9KXt4KGlSSztEKNwwOh33rY/3vLpTrYahAYOJHyh+r6DBu8KGsUUys2DfM0D7CS726FwpH2DibZaNNMMq09uw0fp1TTvVY9XszHIIgBgzjAI+q3Li7kYKqgSQCrFHZ4/LF774ms48JHARqxgpmWoSmehp+DOdFXGEaMX2g/Xsg6B4QUNIXtfVxTVEka3rcNVxzT3rM7Nkkg5dT0CP9Nw3/ABKIrqfpaevzjgitVNCwEDC44KSRmo6EVozL9GTmXJ0yC2m1VWl9b+3Tb3nQzVkJEA//4LS/wsyHBxvLCISS6qYqExA5MGYkCgYEA3sPjx0YMpRr6ohpz6xCVh5KPetdyZPuoOahWmMZPmLgukHYhv3EcF9sjSpwAc9wN9Db9W9MGkqwUQaEq4YWNFSbV3AOoCq+xh1IbAx14lk1ozMqYK2ZFWXigRFdKMKiZhq6ZdbRSbzsqR7jgRnUXXOHMzM8EAiBrBwwvNIMeq7UCgYEAxs6qXpE77ogNHmya/NBD0TtyedGQElbTTFWH5QXQ9qD94h6B4CjMbQAp7YOzdkBzFPFGyTP/r2d8Z+RxZIj9LCGlMJ/QZWuUdpp2CTfN8bA9rv4rm+z0lVqt7M2Ij5hrIj3l+KR9J5VArlgvODeaN8Vwt8HxCu1BNpRNC/liPHkCgYEAzkM/QyJfTvkeHL61mm/upGtWIv4eU9aYu1pdZHQq+1N++hAHy4Vl58jmcozj9mNJIFlSWpWYvnJLB0G9vLe2HGGrH17bV5m6nXuuu0GsiC8A3K6yG21ExUVl0CGq9kcGcKh2O4BN2+RCj8plD5gXm88PnIwKQBXYQ9xKUSsfWQkCgYACDPes6gqYN0cLXUtr+Cn66oYPw52c0tBzJR61ug2hvZ9gybfFPCZ/qVTFYmpjed62BJcaVDL6+DJMUArrYo5Z+i7eBYf8w9NQNd+p0K2LJKo+N9jzTspnD/xjSOtzr7rLK6BHpEq2Mc/s/HPgPJKWqK609ocp+bCGg5kX2oas4QKBgA8Lj75Mkdg4VdcCM8agh8GpRhnqLVnRsX9qtN8DrKD0VrAYPMgN7sl9/+gZqosqXcgZLoNLy5RX0DeN8HtqtymHx0MQ0Q6SY2aR+Fsn3y2We78/ZpTUrp7YdJrhbKMR6d75L14eik+qaiIfNJKvXAyhmdunJaVvqBKYoXyhG6cE
```

### 前端公钥（写死在代码/配置中）

```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArP9YDcoZOEm2sJixPWcL
B9+vxhDHZIajajeBmtm8qrlQVNKp9OtNCdNpZTp4/AjUF6lkp1U8h0rn9CgOGIM3
DFGU/Q1dSTnq1KSDgFv2tB6YX3XMTGTpm4sAFk5dF4GMmhMl3Nk/bF/9vf6sdEGA
c/Ngr9cLeuRjK0N3GgXnSBrsp1rOVX7DdHXrkIHPf4539H58Uc+AlZLFmdCfQpuB
eVT69dSeYbZePb84A3RGN4zZM3cM64A9KRHGgnssokrPwnz6QeVu+76zbQLKOe81
S3Doy+9Q+fD/rk+c+8OsaVJ22kQyb9oW3AEnEF+a3zX5JX2W4Ab9N2ukEHcU8geU
jQIDAQAB
-----END PUBLIC KEY-----
```

---

## 后端实现

### 1. 配置 (`application.yml` 新增)

```yaml
rsa:
  private-key: ${RSA_PRIVATE_KEY}   # 从环境变量读取
```

### 2. 密钥持有者 `KeyPairHolder.java`

```java
package com.senses.permission.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
public class KeyPairHolder {

    private final PrivateKey privateKey;

    public KeyPairHolder(@Value("${rsa.private-key}") String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.privateKey = kf.generatePrivate(spec);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
```

### 3. 解密工具 `CryptoUtils.java`

```java
package com.senses.permission.util;

import com.senses.permission.config.KeyPairHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoUtils {

    private final KeyPairHolder keyPairHolder;

    private static final long TIME_WINDOW_MS = 5 * 60 * 1000; // 5分钟

    /**
     * 解密前端传来的密文，还原明文密码并校验时间窗口
     *
     * @param ciphertext 前端 RSA 加密后的 Base64 密文
     * @return 明文密码
     * @throws Exception 解密失败或时间戳过期
     */
    public String decryptPassword(String ciphertext) throws Exception {
        PrivateKey privateKey = keyPairHolder.getPrivateKey();

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] plainBytes = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        String plaintext = new String(plainBytes, StandardCharsets.UTF_8);

        // 格式: password|timestamp
        int sepIdx = plaintext.lastIndexOf("|");
        if (sepIdx == -1) {
            throw new IllegalArgumentException("密文格式错误");
        }

        String password = plaintext.substring(0, sepIdx);
        long timestamp = Long.parseLong(plaintext.substring(sepIdx + 1));

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TIME_WINDOW_MS) {
            log.warn("登录请求时间戳过期，时间差: {}ms", Math.abs(now - timestamp));
            throw new IllegalArgumentException("请求已过期，请刷新页面重试");
        }

        return password;
    }
}
```

### 4. 改造 `UserServiceImpl.java` login 方法

```java
// 注入
@Autowired
private CryptoUtils cryptoUtils;

@Override
public User login(UserParam userParam) {
    String plainPassword;

    // 尝试 RSA 解密，失败则降级为明文（兼容旧前端）
    try {
        plainPassword = cryptoUtils.decryptPassword(userParam.getPassword());
    } catch (Exception e) {
        log.warn("RSA 解密失败，使用明文密码：{}", e.getMessage());
        plainPassword = userParam.getPassword();
    }

    QueryWrapper wrapper = new QueryWrapper();
    wrapper.eq("username", userParam.getUsername());
    wrapper.eq("password", DigestUtils.md5Hex(plainPassword + passwordKey));
    wrapper.ne("status", CommonStatusEnum.DELETE.getId());
    User user = baseMapper.selectOne(wrapper);
    // ... 后续逻辑不变
}
```

---

## 前端实现

### 1. 安装依赖

```bash
npm install jsencrypt
```

### 2. 公钥常量（放在配置文件或常量文件中）

```js
// config/rsa.js 或直接放在登录模块中
export const RSA_PUBLIC_KEY = `-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArP9YDcoZOEm2sJixPWcL
B9+vxhDHZIajajeBmtm8qrlQVNKp9OtNCdNpZTp4/AjUF6lkp1U8h0rn9CgOGIM3
DFGU/Q1dSTnq1KSDgFv2tB6YX3XMTGTpm4sAFk5dF4GMmhMl3Nk/bF/9vf6sdEGA
c/Ngr9cLeuRjK0N3GgXnSBrsp1rOVX7DdHXrkIHPf4539H58Uc+AlZLFmdCfQpuB
eVT69dSeYbZePb84A3RGN4zZM3cM64A9KRHGgnssokrPwnz6QeVu+76zbQLKOe81
S3Doy+9Q+fD/rk+c+8OsaVJ22kQyb9oW3AEnEF+a3zX5JX2W4Ab9N2ukEHcU8geU
jQIDAQAB
-----END PUBLIC KEY-----`
```

### 3. 登录方法

```js
import JSEncrypt from 'jsencrypt'
import { RSA_PUBLIC_KEY } from '@/config/rsa'
import axios from 'axios'

function login(username, password) {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(RSA_PUBLIC_KEY)

  // 密码 + "|" + 当前毫秒时间戳
  const rawText = password + '|' + Date.now()
  const encryptedPassword = encryptor.encrypt(rawText)

  if (!encryptedPassword) {
    // 加密失败（极少情况，通常是因为公钥格式错误）
    console.error('RSA 加密失败')
    return Promise.reject(new Error('加密失败'))
  }

  return axios.post('/login', {
    username: username,
    password: encryptedPassword
  })
}
```

---

## 安全性

| 威胁 | 防御情况 |
|------|:------:|
| 传输中间人窃听 | HTTPS 保障（应用层 RSA 额外防日志/app内泄漏） |
| 密文重放 | 5 分钟内失效 |
| 5 分钟内重放 | 未防（无 Redis 无法实现一次性 token，但攻击门槛已很高） |
| 日志泄漏密码明文 | 日志中是 RSA 密文，无法还原 |
| 数据库泄漏 | 密码为 MD5 哈希存储（**建议后续升级为 BCrypt**） |

---

## 兼容过渡

后端 login 方法中，RSA 解密失败时自动降级为明文密码：

```java
try {
    plainPassword = cryptoUtils.decryptPassword(userParam.getPassword());
} catch (Exception e) {
    plainPassword = userParam.getPassword(); // 兼容旧前端
}
```

因此前后端可以独立上线，不会因为一方先发版导致登录不可用。

---

## 多实例部署

所有实例共享同一个环境变量 `RSA_PRIVATE_KEY`，密钥对一致，负载均衡下任意实例均可解密。

K8s 配置示例：

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: permission-secrets
data:
  RSA_PRIVATE_KEY: <上面那串 Base64>
```

```yaml
# Deployment 中引用
env:
  - name: RSA_PRIVATE_KEY
    valueFrom:
      secretKeyRef:
        name: permission-secrets
        key: RSA_PRIVATE_KEY
```

---

## 密钥轮换

如果未来需要更换密钥对：

1. 重新执行 `openssl genpkey` 生成新密钥对
2. 更新 K8s Secret 中的 `RSA_PRIVATE_KEY`（新私钥）
3. 更新前端代码中的 `RSA_PUBLIC_KEY`（新公钥）
4. 前后端同时发版

建议频率：1 年一次，或怀疑私钥泄露时立即更换。
