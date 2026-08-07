# FISCO BCOS Wallet Proxy

`fisco-bcos-wallet-proxy` 是一个面向浏览器插件钱包和 DApp 调试场景的 FISCO BCOS JSON-RPC 白名单代理服务。服务通过 FISCO BCOS Java SDK 与链节点建立 TLS 连接，对外提供统一的 HTTP JSON-RPC 入口，并按路由隔离不同链、群组和密码体系配置。

这个项目的定位是钱包测试与接入辅助服务：钱包本体负责账户、授权、签名和资产展示；proxy 负责把浏览器侧请求安全地转发到 FISCO BCOS 节点，并限制可调用的 RPC 方法范围。

## 功能特性

- 统一 HTTP 入口：`POST /rpc/{route}`；
- 支持一个服务进程连接多条链或多个群组；
- 支持标准链和国密链配置；
- 支持全局或按路由配置 RPC 方法白名单；
- 自动为 FISCO BCOS 原生 RPC 注入真实 `group-id` 和空节点参数；
- 支持 JSON-RPC batch，并可限制 batch 请求数量；
- 使用 FISCO BCOS Java SDK 查询账户余额；
- SDK 按路由懒加载，单条链配置异常不会影响服务启动；
- 提供 Spring Boot Actuator 健康检查和滚动日志。

## 技术栈

- Java 17
- Maven
- Spring Boot 3.4.7
- FISCO BCOS Java SDK 3.8.0

## 项目结构

```text
fisco-bcos-wallet-proxy/
├── pom.xml                                  # Maven 构建配置
├── README.md                                # 项目说明
├── http/
│   └── example.http                         # HTTP 调试样例
├── config/
│   └── fisco/                               # SDK TOML、证书和账户材料，部署时按环境维护
├── src/
│   ├── main/
│   │   ├── java/com/fiscobcos/wallet/proxy/
│   │   │   ├── FiscoBcosProxyApplication.java
│   │   │   ├── config/                      # Spring Boot 配置绑定
│   │   │   ├── model/                       # JSON-RPC 错误模型
│   │   │   ├── service/                     # SDK 客户端注册与代理逻辑
│   │   │   └── web/                         # HTTP Controller 和请求限制 Filter
│   │   └── resources/
│   │       ├── application.yml              # Spring Boot 基础配置
│   │       └── application-chain.yml        # 默认链路由和 RPC 白名单配置
│   └── test/java/com/fiscobcos/wallet/proxy/
└── target/                                  # Maven 构建产物，忽略提交
```

## 配置说明

### 应用配置

`src/main/resources/application.yml` 保存服务端口、日志、健康检查和配置导入规则。默认会加载 classpath 中的 `application-chain.yml`，同时支持通过项目根目录的 `config/application-chain.yml` 进行环境覆盖：

```yaml
spring:
  config:
    import:
      - classpath:application-chain.yml
      - optional:file:./config/application-chain.yml
```

因此，开发阶段可以直接使用源码中的默认路由配置启动服务；部署阶段如需改路由、白名单或 SDK 配置路径，可以复制一份 `application-chain.yml` 到根目录 `config/` 下覆盖默认值。

默认配置中的 `example` 路由只是模板。它可以用于验证 Spring Boot 服务是否能启动，但在真实调用链 RPC 前，需要把 SDK TOML、节点地址和证书替换为实际链环境。

### 链路由配置

链路由、真实 group ID、国密开关、SDK TOML 路径和 RPC 白名单配置在 `application-chain.yml` 中：

```yaml
proxy:
  max-batch-size: 20
  max-request-bytes: 2097152
  allowed-methods:
    - getBlockNumber
    - getTransaction
    - getTransactionReceipt
    - call
    - getBalance
    - sendTransaction
  passthrough-methods:
    - getGroupList
    - getGroupInfoList
    - getPeers
  groups:
    example:
      enabled: true
      gm: false
      group-id: group0
      sdk-config: config/fisco/example/config.toml
```

`{route}` 是对外暴露的路由名称，例如 `/rpc/example`；`group-id` 是链上的真实群组 ID。两者可以相同，也可以不同。

### SDK 配置和证书

SDK TOML、证书和账户材料放在根目录 `config/fisco/<route>/` 下，例如：

```text
config/fisco/example/
├── config.toml
└── certs/
    ├── ca.crt
    ├── sdk.crt
    └── sdk.key
```

标准链通常需要：

```text
ca.crt
sdk.crt
sdk.key
```

国密链通常需要：

```text
sm_ca.crt
sm_sdk.crt
sm_sdk.key
sm_ensdk.crt
sm_ensdk.key
```

`application-chain.yml` 中的 `gm` 必须与 SDK TOML 中的 `useSMCrypto` 保持一致，否则首次访问该路由时会拒绝创建 SDK 客户端。

证书、账户文件和本地环境覆盖配置不建议提交到代码仓库，已经在 `.gitignore` 中忽略。

默认仓库只保留 `config/fisco/example/certs/.gitkeep` 作为目录占位，不包含真实证书。连接真实链前，请将链生成的 SDK 证书放入 `certPath` 指向的目录，并把 `config/fisco/example/config.toml` 中的 `peers` 改成实际节点的 SDK channel 地址。

## 构建与启动

### 1. 准备环境

安装 JDK 17 和 Maven：

```bash
java -version
mvn -version
```

### 2. 运行测试

```bash
mvn clean test
```

测试不依赖真实链节点和证书，主要验证配置加载、JSON-RPC 白名单、batch 限制、请求校验和错误映射等基础逻辑。

### 3. 本地启动

```bash
mvn spring-boot:run
```

默认端口为 `28081`，可以通过环境变量覆盖：

```bash
SERVER_PORT=28082 mvn spring-boot:run
```

Windows PowerShell：

```powershell
$env:SERVER_PORT = "28082"
mvn spring-boot:run
```

如果本机同时安装了多个 JDK，请确认 `mvn -version` 输出的 Java version 是 17。Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

### 4. 打包运行

```bash
mvn clean package
java -jar target/fisco-bcos-wallet-proxy-1.0.0.jar
```

启动 JAR 时建议从项目根目录或包含 `config/` 的部署目录执行，确保 SDK TOML 和证书路径能够被正确解析。

## 健康检查

```bash
curl http://127.0.0.1:28081/actuator/health
```

返回 `UP` 代表 Spring Boot 服务已启动。SDK 客户端采用懒加载，健康检查不会主动连接所有链；需要再调用一次链 RPC 才能验证节点、证书和 group 配置是否正确。

## 调用示例

获取最新块高：

```bash
curl -X POST http://127.0.0.1:28081/rpc/example \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"getBlockNumber","params":[]}'
```

成功响应示例：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": 42
}
```

该示例只有在 `config/fisco/example/config.toml` 已改为真实节点地址、证书已放入 `certPath`，并且 `group-id` 与链上群组一致时才会成功。默认模板未内置真实节点和证书。

接口调试样例可参考 `http/example.http`。

## 与钱包的关系

`FISCO BCOS Wallet` 是主要面向用户的浏览器插件钱包，负责：

- 创建或恢复钱包；
- 管理账户和网络；
- 连接 DApp；
- 弹出交易确认窗口；
- 本地签名交易；
- 展示 ERC20 和 ERC721 合约资产。

`fisco-bcos-wallet-proxy` 是钱包测试和 DApp 调试时使用的辅助服务，负责：

- 连接 FISCO BCOS 节点；
- 统一 HTTP JSON-RPC 入口；
- 按路由选择链和 group；
- 对允许调用的 RPC 方法做白名单限制；
- 隔离浏览器端和链节点 SDK 连接细节。

生产或正式演示时，可以把 proxy 部署在受控网络环境中，只暴露钱包和 DApp 所需的有限 RPC 能力。


## 基础安全设计与生产化建议

当前 proxy 是一个基础可运行模板，适合本地开发、演示环境和受控测试环境。代码中已经包含一些基础防护：

- **RPC 方法白名单**：请求方法必须配置在 `allowed-methods` 中；
- **按 route 隔离链配置**：不同 route 可使用不同 group、SDK TOML、证书和白名单；
- **请求体大小限制**：通过 `max-request-bytes` 拒绝过大的请求体；
- **JSON-RPC batch 限制**：通过 `max-batch-size` 控制 batch 请求数量；
- **基础请求格式校验**：校验 `jsonrpc`、`method`、`params` 和 `id`；
- **错误信息收敛**：上游 SDK 详细异常只写入服务端日志，对客户端返回通用错误；
- **敏感文件忽略提交**：证书、账户文件和本地覆盖配置通过 `.gitignore` 排除。

如果要将 proxy 部署到公网、生产环境或多团队共享环境，建议继续增强以下能力：

- **访问认证**：增加 API Key、JWT、OAuth2、mTLS 或统一网关鉴权；
- **来源控制**：配置 CORS origin 白名单、IP 白名单、内网安全组或 VPN；
- **读写分离**：只读 route 开放 `call`、`getBlockNumber` 等方法，写链 route 单独控制 `sendTransaction`；
- **交易策略校验**：对 `sendTransaction` 增加目标合约、函数选择器、from 地址、value、gas/limit 等规则；
- **限流和熔断**：按 IP、route、DApp 或账户设置 QPS、并发和失败熔断策略；
- **审计日志**：记录来源、route、method、耗时、错误码和交易哈希，同时避免记录私钥、签名材料和敏感业务参数；
- **密钥管理**：证书和账户材料通过挂载目录、配置中心或密钥管理系统注入，不进入镜像和代码仓库；
- **Actuator 收敛**：生产环境只暴露必要端点，并对健康检查以外的管理端点加认证；
- **HTTPS 与反向代理**：通过 Nginx、网关或负载均衡统一终止 HTTPS，并配置超时、请求大小和安全响应头；
- **自动化测试**：新增 route、白名单或配置字段时同步补充配置加载和接口回归测试。
## 常见问题

### 找不到 application-chain.yml

默认配置已经打包在 classpath 中。如果需要部署环境覆盖，请将 `application-chain.yml` 放到启动目录的 `config/` 下，并确认 YAML 格式正确。

### SDK config does not exist

检查 `proxy.groups.<route>.sdk-config` 指向的 TOML 文件是否存在。相对路径会按当前启动目录解析。

### GM setting mismatch

检查 `application-chain.yml` 中的 `gm` 是否与 SDK TOML 中的 `useSMCrypto` 一致。国密链应同时配置国密证书。

### RPC 方法被拒绝

检查请求的 `method` 是否在全局 `allowed-methods` 或当前路由的 `allowed-methods` 中。proxy 默认采用白名单策略，不在白名单内的方法会被拒绝。

### 健康检查正常但链请求失败

健康检查只表示 Spring Boot 应用存活，不代表所有链路由都可用。请继续检查节点地址、证书、group ID、密码体系和网络连通性。

## 维护建议

- 新增路由时，优先在 `application-chain.yml` 中增加 `proxy.groups.<route>`；
- 每个链路由使用独立的 `config/fisco/<route>/config.toml` 和证书目录；
- 新增 RPC 能力时同步更新白名单和测试用例；
- 提交代码前执行 `mvn clean test`；
- 不要提交证书、账户文件、日志和本地环境覆盖配置。
