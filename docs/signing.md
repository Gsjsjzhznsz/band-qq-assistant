# 签名与包名要求

互联（interconnect）通信的硬性前提：**手环快应用 manifest.json 的 package 与 Android 同步器
App 的 applicationId 完全一致，且 rpk 用与手机 App 相同的证书签名。**

本项目统一使用包名：`com.example.bandqq`。

## 1. 从 jks 提取签名

```bash
# 1. jks → p12
keytool -importkeystore -srckeystore keystore.jks -destkeystore keystore.p12 \
  -srcstoretype jks -deststoretype pkcs12

# 2. p12 → pem
openssl pkcs12 -nodes -in keystore.p12 -out keystore.pem

# 3. 从 keystore.pem 分离：
#    -----BEGIN PRIVATE KEY----- 内容 → sign/debug/private.pem
#    -----BEGIN CERTIFICATE----- 内容 → sign/debug/certificate.pem
#    （release 版同理放到 sign/release）
```

## 2. 手环 rpk 打包签名

- 在 AIoT-IDE 的"签名配置"中，将私钥与证书指向从第 1 步分离出的
  `sign/debug/private.pem` 与 `sign/debug/certificate.pem`。
- 打包出的 rpk 才能与同步器 App 正常互联。

## 3. 注意事项

- 调试通信时必须用配套证书打包手机 App 和手环 rpk，否则通信被拒绝。
- release 版 rpk 请始终使用同一证书。
- 真机重装建议先卸载旧包再安装，保证彻底替换。
