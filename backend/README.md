# 智水云 AI 水表收费管理系统 —— 后端

A06 后端服务：**Spring Boot 3 + SQLite**，为前端 Vue 原型提供 REST API，
覆盖「自动抄表 → 阶梯计费 → 账单 → 异常检测 → 统计分析」核心闭环。

## 技术栈

- Java 17 + Spring Boot 3.3
- Spring Data JPA + SQLite（`org.xerial:sqlite-jdbc` + Hibernate 6 community dialect）
- Spring Security + JWT（无状态鉴权）
- SpringDoc OpenAPI（Swagger UI：`/swagger-ui.html`）
- Apache POI（报表导出预留）

## 前置条件

- JDK 17（本机 `C:\Program Files\Microsoft\jdk-17.0.12.7-hotspot`）
- Maven 3.9+（或使用仓库内 `mvnw` wrapper）

## 启动

```bash
cd backend
# 方式一：已安装 Maven
mvn spring-boot:run

# 方式二：使用 wrapper（首次会自动下载 Maven）
./mvnw spring-boot:run
```

首次启动会自动建表并写入种子数据（60 台水表、6 个账号、水价规则、示例异常与上报数据）。
服务监听 `http://127.0.0.1:8000`（与前端 Vite 代理一致）。

数据库文件默认生成在 `backend/water.db`，删除后重启可重置数据。

## 测试账号

所有账号初始密码统一为 **`admin123`**（仅测试环境；入库时为 BCrypt 哈希）。

| 登录名 | 角色 | 数据范围 |
|---|---|---|
| admin | 系统管理员 | 全部 |
| operator_east | 运营人员 | 滨江花园、江南新城 |
| finance01 | 财务人员 | 全部计费 |
| auditor01 | 审计人员 | 全部只读 |
| user_res_001 | 普通用户（张明远） | 仅 A-00218 |
| user_com_001 | 普通用户（青禾餐饮） | 仅 B-00126（禁用） |

## 登录

```http
POST /api/auth/login
{"username":"admin","password":"admin123"}
```

响应 `data.token`，后续请求携带 `Authorization: Bearer <token>`。

## 主要接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| GET | `/api/dashboard` | 运营总览 |
| GET | `/api/meters` | 水表分页（page/pageSize/keyword/type） |
| POST | `/api/meters` | 新增水表 |
| PUT | `/api/meters/{id}` | 编辑水表 |
| POST | `/api/readings/tasks` | 启动自动抄表（幂等） |
| GET | `/api/readings/tasks` | 抄表任务列表 |
| POST | `/api/readings` | 原始上报入库（DQ 校验） |
| GET | `/api/bills` | 账单批次 |
| POST | `/api/bills/recalculate` | 重新核算账单（幂等） |
| GET | `/api/anomalies` | 异常列表（level 可选） |
| PATCH | `/api/anomalies/{id}` | 更新异常状态 `{"status":"处理中"}` |
| POST | `/api/ai/query` | AI 问数 `{"question":"..."}` |

完整文档见 Swagger UI：`http://127.0.0.1:8000/swagger-ui.html`。

## 统一响应结构

```json
{ "code": "OK", "message": "成功", "data": {}, "requestId": "req_xxx", "timestamp": "..." }
```

失败时 `code` 为稳定业务错误码（如 `TEMPERATURE_OUT_OF_RANGE`、`METER_NOT_FOUND`、
`CUMULATIVE_ROLLBACK`、`DUPLICATE_REPORT` 等，见 `common/ErrorCode.java`）。

## 计费规则（P0）

- 居民阶梯：0-12 @2.80、12-20 @3.60、>20 @5.20（元/m³）
- 商业统一价 4.20、工业合同价 5.10
- 违约金 = 水费 × 0.0005 × 逾期天数
- 金额全程 `BigDecimal` + `RoundingMode.HALF_UP`，统一到「分」
- 关键样本：18.6 m³ 居民 = **57.36 元**

60 条计费参数化用例见 `BillingServiceTest`，覆盖测试文档 BILL-001 ~ BILL-060。

## 运行测试

```bash
cd backend
mvn test
```

## 目录结构

```text
src/main/java/com/zhishuiyun/water/
├─ common/        ApiResponse、ErrorCode、BusinessException、全局异常、RequestContext
├─ config/        SecurityConfig、DataInitializer（种子数据）、OpenAPI、RequestId 过滤器
├─ security/      JWT 生成/解析、过滤器、AuthUser、CurrentUser
├─ entity/        10 张表实体 + 领域枚举
├─ repository/    Spring Data JPA
├─ dto/           请求/响应视图对象
├─ service/       计费、上报校验、抄表、账单、异常、总览、AI 问数、审计
└─ controller/    REST 接口
```
