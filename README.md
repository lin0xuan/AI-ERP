# AI ERP

面向电商业务的 AI 原生 ERP 二次开发项目，覆盖商品、订单、采购、库存、仓库、发货、售后、店铺和系统权限等核心场景，并提供 AI 对话分析、业务工具调用、实时消息和外部通知能力。

本项目当前基于 `qihang-erp-open 4.1.0` 继续开发，可以按下面的步骤在本地跑起来。平台拉取需要各平台自己的开放接口凭证。AI 对话里的工具目前只查询数据，不会直接改单据。

## 项目状态

- 后端采用 Spring Boot 单体架构，默认端口为 `8088`
- 主前端位于 `vue3/`，默认开发端口为 `88`
- 数据存储使用 MySQL 8，缓存使用 Redis 7
- 数据库初始化脚本包含 161 张业务表
- AI 能力基于 Spring AI，并通过 OpenAI 兼容接口接入大模型
- `vue2/` 已冻结，只作迁移对照，不参与运行。说明见 `vue2/README.md`

## 主要功能

### ERP 业务

- 商品库、SKU、分类、品牌和店铺商品管理
- 多平台、多店铺订单聚合与处理
- 采购订单、供应商、报价和采购入库
- 仓库、仓位、库存及出入库流水
- 手工发货、电子面单、供应商发货和发货记录
- 售后、退款、退货、换货和补发处理
- 用户、角色、部门、菜单、字典和接口授权

### AI 与自动化

- 使用自然语言查询订单、商品、库存、售后和采购数据
- 基于 Spring AI `@Tool` 的查询工具。对话不会直接创建或修改单据
- SSE 流式对话与浏览器实时通知
- 定时扫描销售额为零、发货积压、退款过多、库存不足和发货超时。阈值可在系统参数里用 `alert.ship.pending.min`、`alert.refund.excess.min`、`alert.ship.timeout.hours`、`alert.stock.max.per.run` 覆盖
- 销售、库存、运营分析读取当前库里的汇总。没有默认模型时只返回汇总，不生成结论
- 飞书、钉钉和企业微信 Webhook 通知
- DeepSeek 等 OpenAI 兼容模型接入。模型密钥保存在「AI 智能 / 模型配置」

### 平台对接

淘宝、京东、拼多多、抖店、微信小店、快手、小红书的订单、售后和商品拉取会写入统一的店铺订单和商品表。Amazon、Shopify 同样会写入这两张表，另外保留跨境接口做连通性检查、库存同步和发货回传。实际使用时需要自行申请开放接口权限，并在店铺里填写 AppKey、AppSecret、token 和回调地址。

初始化数据里的回调地址是 `http://127.0.0.1:8088`。生产环境要改成自己的域名。走 Nginx 的 `/prod-api/` 前缀时，回调路径要和反向代理一致。Shopify 公开应用的 OAuth 尚未接入，当前在店铺里手工保存 access token。Amazon 与 Shopify 的字段说明见 `docs/AMAZON-SHOPIFY-INTEGRATION.md`。

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 4.1.0、Spring Security 7.1、Spring AI 2.0 |
| 数据访问 | MyBatis-Plus 3.5.16、MySQL 8 |
| 缓存 | Redis 7 |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、Pinia |
| 通信 | REST API、SSE、Webhook |
| 构建 | Maven 3.9、Node.js 20.20.0、npm 10 |

项目根 Maven 坐标为 `com.aierp:ai-erp:4.1.0`，内部模块统一采用 `com.aierp:ai-erp-*` 命名。

## 项目结构

```text
.
├─ common/       通用结果、异常、配置和工具类
├─ security/     登录认证、JWT、权限与安全配置
├─ model/        实体、业务对象、DTO 和 VO
├─ mapper/       MyBatis-Plus Mapper 和 XML 映射
├─ service/      ERP 领域服务及业务实现
├─ open-sdk/     电商平台开放接口 SDK
├─ erp-api/      Spring Boot 应用入口及控制器
├─ vue3/         Vue 3 主前端
├─ vue2/         已冻结的 Vue 2 前端，仅作对照
├─ config/       本地配置示例。application.yml 不提交
├─ docs/         数据库脚本、迁移和项目资料
├─ evaluation/   公开商品目录导入和接口压测
└─ docker-compose.yml
```

后端 Maven 模块关系：

```text
erp-api
├─ security
├─ service
│  ├─ mapper
│  ├─ model
│  └─ common
└─ open-sdk
```

## 本地开发

### 1. 环境要求

请先准备以下环境：

- JDK 17
- Maven 3.9 或更高版本
- Node.js 20.20.0
- npm 10 或兼容版本
- MySQL 8
- Redis 7

确认命令可以正常执行：

```bash
java -version
mvn -version
node -v
npm -v
```

### 2. 初始化数据库

创建数据库：

```sql
CREATE DATABASE `qihang-erp`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

然后按顺序导入：

```text
docs/qihang-erp.sql
docs/migrations/20260910_add_amazon_shopify.sql
```

`qihang-erp.sql` 包含表结构和初始化数据。迁移脚本补上 Amazon、Shopify 平台行。导入前请确认目标数据库，避免覆盖已有同名表。

需要一批可查询的商品和订单时，见 `evaluation/README.md`。那份数据来自公开商品目录，订单是本地生成的。

### 3. 配置后端

主要配置文件：

```text
erp-api/src/main/resources/application.yml
```

仓库里的 `application.yml` 不含数据库密码和 JWT 密钥。在项目根目录复制本地配置：

```powershell
Copy-Item config\application.yml.example config\application.yml
```

然后填写 `spring.datasource.password` 和 `token.secret`。从项目根目录启动时，Spring Boot 会读取 `config/application.yml` 并覆盖同名项。这个文件已在 `.gitignore` 中。

也可以不建文件，直接设置环境变量 `MYSQL_PASSWORD` 和 `JWT_SECRET`。

如需让 Spring AI 自动配置带上 DeepSeek 密钥：

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
```

页面里的模型配置保存在数据库中，优先于这个环境变量。

依赖也可以用 Docker 启动：

```bash
docker compose up -d
```

这会启动 MySQL 8.4 和 Redis 7，并把初始化 SQL 导入空数据卷。默认 root 密码是 `changeme`，可用 `MYSQL_PASSWORD` 覆盖，并写进 `config/application.yml`。

### 4. 构建并启动后端

在项目根目录执行：

```bash
mvn clean install
java -jar erp-api/target/ai-erp-api-4.1.0.jar
```

后端默认地址：

```text
http://localhost:8088
```

### 5. 安装并启动前端

```bash
cd vue3
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:88
```

初始化 SQL 中的演示管理员账号：

```text
账号：admin
密码：QHerp@23
```

首次登录后请立即修改默认密码。生产环境不得继续使用演示账号和默认凭据。

## 构建生产包

### 后端

```bash
mvn clean package
```

构建结果位于：

```text
erp-api/target/ai-erp-api-4.1.0.jar
```

### 前端

```bash
cd vue3
npm run build
```

构建结果位于：

```text
vue3/dist/
```

## 反向代理与 SSE

生产部署时，可由 Nginx 托管前端静态资源，并把 `/prod-api/` 转发到后端。SSE 连接需要关闭代理缓冲并适当增加超时时间：

```nginx
location /prod-api/ {
    proxy_set_header Host $http_host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_read_timeout 1800s;
    proxy_send_timeout 1800s;

    proxy_pass http://127.0.0.1:8088/;
}
```

## 开发约定

- 新功能优先在 `vue3/` 和现有 Spring Boot 单体模块中开发
- 数据库变更应提供可重复执行或可追踪的迁移脚本
- 新增敏感配置时只提交变量名和示例值，不提交真实密钥
- 修改接口时同步更新前端调用、权限配置和相关文档
- 提交前至少运行一次后端构建和前端生产构建

```bash
mvn clean install
cd vue3 && npm run build
```

## 后续计划

- 整理并统一项目名称、包名和模块坐标
- 把数据库变更从全量 SQL 拆成可重复执行的迁移
- 补持续集成，当前只有预警规则的单元测试
- 优化 AI 工具调用的权限、审计和数据隔离
- Vue 2 目录只保留对照，新页面放在 `vue3/`
- 接入 Shopify 公开应用 OAuth
- 完善生产部署、备份、监控和故障恢复方案

## 上游项目与修改说明

本项目基于开源项目 [qihang-erp-open](https://github.com/zeasin/qihang-erp-open) 进行二次开发。

- 上游项目名称：qihang-erp-open
- 当前开发基线：4.1.0
- 本项目修改起始日期：2026-09-01
- 本项目将继续保留上游许可证和必要的版权声明
- 后续新增代码、功能调整和维护记录以本仓库提交历史为准

这里对上游项目名称和来源的说明仅用于履行开源许可和记录技术来源，不代表上游作者为本项目后续修改提供担保或技术支持。

## 许可证

本项目沿用上游项目的 [GNU Affero General Public License v3.0](LICENSE)。

你可以在许可证允许的范围内使用、研究、修改和分发本项目。发布修改版本时，需要保留许可证与相关声明、标明修改情况，并遵守 AGPL-3.0 对源代码提供等事项的要求。通过网络向用户提供本项目功能时，也应向这些用户提供取得对应源代码的方式。

本项目按“现状”提供，不附带任何明示或暗示的担保。
