# 智水云 Java 后端

Spring Boot 3.3.5 + SQLite REST 服务，为 Vue 前端提供真实认证、权限隔离、采集、计费缴费、异常、通知和反馈接口。默认端口 8000，绑定本机 127.0.0.1；默认不是 MySQL，也不是 FastAPI。

## 环境与启动

Java 编译目标为 17，需要完整 JDK 17+。本机联调使用 JDK 21.0.8、Maven 3.9.16；项目根目录的 Vue 前端使用 Node 24.21.0、npm 11.19.0，依赖按 package-lock.json 安装。

从项目根目录打开后端终端：

~~~powershell
cd backend
$env:JAVA_HOME = 'C:\jdk\jdk-21.0.8'  # 本机示例，队友请替换
mvn.cmd spring-boot:run
~~~

也可双击根目录 `start-all.bat` 同时启动前后端。未安装系统 Maven 时，在 backend 使用 `mvnw.cmd spring-boot:run`；首次需联网下载 Maven/依赖。Maven 需要 JAVA_HOME 指向 JDK 根目录，而非 bin 目录。

服务：http://127.0.0.1:8000。Swagger：http://127.0.0.1:8000/swagger-ui.html。Swagger 可用于独立验证后端：先调用登录取得 `data.token`，再在 Authorize 中设置 Bearer 认证。

## 数据与初始化

- 默认连接 `jdbc:sqlite:./water.db`，相对路径由启动工作目录决定。上述命令会使用 backend/water.db。
- `ddl-auto=update` 保留已有记录并补充新表/列；`DataInitializer` 仅在各对应表为空时初始化。
- 全新数据库种子包含 60 台水表、6 个账号、水价规则、示例异常及近 7 天上报；已有数据库不会每次覆盖账号、密码或业务记录。
- 账户、读数、任务、账单、模拟付款、通知、反馈、操作日志均落库。
- SQLite 连接池当前为 1，适合本地原型；不要多个应用进程同时写同一文件，不代表生产多实例并发方案。

运行更新版前停止服务并备份原库。不要删除 water.db 重置数据；验收请使用独立副本，并通过 `SQLITE_PATH` 指向该副本。本轮没有直接清空或替换原库。

## 演示账号

初始密码统一为 `admin123`，保存为 BCrypt 哈希；公开演示凭据不得用于真实部署。

| 登录名 | 角色 | 当前种子权限 |
| --- | --- | --- |
| admin | ADMIN | 全部权限，包括账号绑定、模拟场景及调度开关 |
| operator_east | OPERATOR | 滨江花园、江南新城；设备/抄表/异常/反馈操作，账单只读 |
| finance01 | FINANCE | 数据查询、账单生成和收费；不能设备操作、采集、异常及反馈处理 |
| auditor01 | AUDITOR | 管理业务只读 |
| user_res_001 | USER | 本人 A-00218 数据、本人缴费与反馈 |
| user_com_001 | USER | 本人 B-00126，但账号禁用，登录会被拒绝 |

ADMIN 可通过账号绑定接口给 USER 绑定/解绑水表；不能把已绑定其他账号的水表直接再分配。公开注册永远 USER、默认不绑定，不能通过请求中的 role、areaScope 或 boundMeterId 提权/认领水表。

JWT 仅证明会话，过滤器每次从数据库重新读取账号启用状态、角色和数据范围，因此禁用账号或调整绑定对已有 token 同样生效。OPERATOR 无辖区时无设备数据；USER 无绑定时返回空业务范围，而非所有数据。

## API 与响应

除登录、注册和 API 文档外，业务请求需要 `Authorization: Bearer <token>`。统一响应为：

~~~json
{
  "code": "OK",
  "message": "成功",
  "data": {},
  "requestId": "req_xxx",
  "timestamp": "..."
}
~~~

前端统一取 data，不能把 AI 响应当成裸 `{"answer":"..."}`。失败返回相应 HTTP 状态及稳定业务 code/message；null 字段可能省略。分页水表列表支持 page、pageSize（1–200）、keyword、type。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | username/password 登录，返回 token 及会话资料 |
| POST | `/api/auth/register` | username/name/password/可选 phone，返回 USER 会话 |
| GET | `/api/auth/me` | 当前数据库身份，含 role、boundMeterId、enabled |
| GET | `/api/accounts` | ADMIN 账号列表，不返回密码 |
| PATCH | `/api/accounts/{id}/binding` | ADMIN，账号数字 ID；boundMeterId 字符串或 null |
| GET | `/api/dashboard` | 当前权限范围总览 |
| GET | `/api/user/summary` | USER 个人汇总；无绑定为空数据 |
| GET / POST / PUT | `/api/meters` / `/api/meters/{id}` | 水表查询/新增/编辑；写操作仅 ADMIN/辖区 OPERATOR |
| POST / GET | `/api/readings/tasks` | 提交/查询任务；POST 可选 simulateFailure |
| POST | `/api/readings/tasks/{id}/retry` | 失败任务补抄；仅 ADMIN 或有权限的原运营创建者 |
| POST | `/api/readings` | 原始读数上报及数据质量校验；ADMIN/辖区 OPERATOR |
| GET / PATCH | `/api/readings/scheduler` | ADMIN 查询/设置 enabled |
| POST | `/api/readings/simulate` | ADMIN，meterId + normal/high_flow/alarm/offline |
| GET | `/api/bills`、`/api/bills/items` | 范围内账单批次/明细；明细可按 batch、period 过滤 |
| POST | `/api/bills/recalculate` | ADMIN/FINANCE 生成快照，可传 period、dueDate |
| GET | `/api/bills/rules`、`/api/bills/payments` | 价格规则/范围内付款流水 |
| POST | `/api/bills/items/{id}/pay` | 本人 USER 或 ADMIN/FINANCE，提交 idempotencyKey |
| GET / PATCH | `/api/anomalies` / `/api/anomalies/{id}` | 范围内异常列表/状态更新；用户仅本人只读 |
| GET | `/api/notifications`、`/api/notifications/{id}` | 本人通知列表/详情 |
| PATCH / POST | `/api/notifications/{id}/read` / `/api/notifications/read-all` | 本人已读/全部已读 |
| GET / POST / PATCH | `/api/feedback` / `/api/feedback/{id}` | 范围内反馈查询、USER 提交、ADMIN/OPERATOR 处理 |
| POST | `/api/ai/query` | question；授权范围数据库问答 |

通知 ID、账号 ID、账单明细 ID、抄表任务 ID 为数字；水表编号和异常编号为业务字符串。完整请求与响应以 Swagger 和 DTO 为准。反馈也保留 `/api/feedbacks` 别名，前端统一使用单数路径。

## 采集、补抄与异常

采集为后端模拟器，不是已接入真实硬件。任务持久化目标/成功/失败水表、进度和重试次数，后台分批执行；同一创建者的活动任务重复提交会返回已有任务。失败自动补抄一次，仍失败转“失败待重试”，手动补抄只重试失败水表。

非系统任务执行时重新检查账号当前 enabled、角色和辖区，权限撤销后不继续越权采集。上报有温度、累计读数倒退、重复报告等校验。

定时模拟采集默认 `SIMULATOR_ENABLED=false`，间隔 `SIMULATOR_INTERVAL_SECONDS=60`（实际最小 10 秒）。GET 查询与 PATCH 修改均要求 ADMIN。页面开关仅改变内存，重启恢复启动配置；已创建任务/读数不因重启丢失。关闭定时开关只阻止后续自动创建，不是取消已经提交的任务。

异常识别包含设备报警、持续高流量及离线；同一水表的同类未关闭报警去重，正常读数不会自动抹掉未处理报警。风险有高/中/低，处置必须“待处理 → 处理中 → 已完成”。只有 ADMIN 或辖区 OPERATOR 可处置，处理人/时间和操作日志落库，本人收到进度通知。

持续高流量判定为：近 10 分钟内最近连续 3 次采样的瞬时流量均严格超过阈值，居民 5、商业 20、工业 100 m³/h，生成高风险 E01。它不是累计用水量或单次读数超过阈值即报警；ADMIN 的 high_flow 演示场景会写入 3 次模拟采样。

## 计费与模拟支付

- 居民阶梯：0–12 m³ 为 2.80 元/m³，12–20 为 3.60，超过 20 为 5.20；商业 4.20，工业 5.10。
- 违约金 = 水费原值 × 快照违约金率 × 实际逾期天数，默认率 0.0005；BigDecimal 计算，金额按 HALF_UP 到分。18.6 m³ 居民水费为 57.36 元。
- 账期格式 yyyy-MM，禁止未来账期；默认截止日期为账期月底后 15 天，可在首次生成时指定。
- 每表每账期一次生成，保存起止累计读数、水费/费率及截止日期快照。接口名 recalculate 不代表覆盖重算；再次调用保留已有快照和已缴记录。
- 未缴账单实际应付金额随逾期增加，读取/后台刷新更新状态；已缴冻结付款时金额。总览与 AI 同样采用当前应付金额。
- 不允许在已有后续快照后补建更早账期，以防重复计费。先抄表、按账期顺序生成；读数不全时返回错误，不造固定账单。
- 支付请求只接收账单 ID 和 8–100 位幂等键，金额由后端计算。重复键/已缴账单不产生第二笔付款，不可拿同一键支付其他账单。
- 支付渠道标记 SIMULATED，流水号以 SIM- 开头，仅完成本地记账，不涉及真实支付、退款或资金清算。

## 注册、通知、反馈与 AI

注册用户名为 3–40 位字母/数字/下划线，姓名最长 60 字符，密码 8–72 字符且 UTF-8 不超过 72 字节；手机号可选。密码不可从账号接口返回。

通知按收件账号查询、详情和已读，其他账号不能访问通知 ID。绑定后 USER 可提交反馈，类型与描述持久化；状态为“待处理/处理中/已完成”，完成必须提供处理结果，处理后通知原提交者。FINANCE 无反馈查询/处理权限，AUDITOR 只读。

AI 默认本地规则：按当前角色/绑定/辖区读取真实水表、账单、异常，结合问题生成摘要，不需要外部 Key。可选 DeepSeek 由以下环境变量配置：

| 变量 | 默认 / 用途 |
| --- | --- |
| DEEPSEEK_ENABLED | false；显式开启外部模型 |
| DEEPSEEK_API_KEY | 不提供默认 Key，使用官方凭据 |
| DEEPSEEK_BASE_URL | https://api.deepseek.com |
| DEEPSEEK_MODEL | deepseek-chat |
| DEEPSEEK_TIMEOUT_SECONDS | 60 |

外部调用不可用时会回退权限范围内规则回答。本轮没有验收外部 DeepSeek 网络/额度/回答质量；不要承诺外部大模型已完成，更不要将业务数据发送到不明免费网关。

## 密钥与其他配置

`JWT_SECRET` 未配置时，会生成持久化随机本机密钥到 `JWT_SECRET_FILE`（默认 ./.jwt-secret）。没有公开默认签名密钥；重启保留该文件可保持已有 token 可验证。勿打印、共享或提交真实密钥。

`SQLITE_PATH` 指定数据库，`SERVER_ADDRESS` 默认 127.0.0.1，`JWT_EXPIRE_MINUTES` 默认 480。首次公开部署前需替换演示账号策略、配置 HTTPS、数据库/备份与密钥管理，并审查 localStorage token 的生产安全策略。

本轮未上传 GitHub；数据库、密钥、环境私密配置和备份不应提交。

## 测试与已知边界

~~~powershell
# 在 backend 目录
mvn.cmd test
~~~

2026-10-10 最终测试通过：共 92 项（API 集成 27、计费 62、JWT 3），失败 0、错误 0、跳过 0。覆盖阶梯/违约金计算、JWT 密钥持久化与安全、真实 API 认证/角色隔离、绑定、通知、反馈、采集补抄、异常、服务端模拟支付及 AI 范围。API 集成测试使用独立 SQLite 测试文件，不连接已有 water.db。浏览器主要流程及旧库副本启动验收已通过，详见根目录《五项功能验收与演示指南.md》。

当前报表是 Vue 对已授权 API 数据的账期/区域汇总和 CSV 下载。虽有 POI 依赖，但尚无已完成的 .xlsx/PDF 下载接口，也不能宣称日报/周报/月报自动服务已完成。操作日志已有持久化，独立日志检索页面尚未提供。
