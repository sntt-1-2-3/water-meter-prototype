# 智水云——AI 水表抄表收费管理系统（Java 版）

竞赛本地演示项目：Vue 前端通过真实 REST API 连接 Spring Boot 后端，业务数据持久化到 SQLite。当前不是 FastAPI 项目，也不再使用浏览器中的模拟账号或明文密码完成登录。

## 当前范围

已打通登录与角色隔离、用户注册与管理员绑定、模拟自动采集与失败补抄、阶梯计费与模拟缴费、异常处置、个人通知与用户反馈。总览、用户用水趋势和 AI 问数使用当前账号可访问的数据库数据。

边界：采集尚未接入真实水表硬件，缴费尚未接入银行/微信/支付宝；报表目前为 CSV，不能宣称已完成 Excel（.xlsx）、PDF 或独立日报/周报/月报服务。外部 DeepSeek 调用尚未纳入本轮验收。

## 技术与环境

| 部分 | 实现 |
| --- | --- |
| 前端 | Vue 3 + TypeScript + Vite，Pinia、Element Plus、ECharts、Axios |
| 后端 | Java 编译目标 17，Spring Boot 3.3.5，Spring Data JPA |
| 安全 | Spring Security、JWT，密码使用 BCrypt |
| 数据库 | 默认 SQLite，不需要安装 MySQL |
| 本机联调工具 | JDK 21.0.8、Maven 3.9.16、Node 24.21.0、npm 11.19.0 |

后端需要完整 JDK 17 或更高版本，不能只有 JRE。前端依赖以 `package-lock.json` 为准，使用 `npm ci` 安装；队友可优先使用上述已验证的工具版本。

## 启动

在项目根目录双击 `start-all.bat`，它会打开后端、前端两个终端。首次启动可能下载依赖，浏览器打开不代表后端已经就绪，请确认终端没有报错。

也可以在项目根目录打开两个 PowerShell 终端：

终端一——前端：

~~~powershell
npm ci
npm run dev
~~~

终端二——后端：

~~~powershell
cd backend
# 本机 JAVA_HOME 未设置时，可显式指定完整 JDK
$env:JAVA_HOME = 'C:\jdk\jdk-21.0.8'
mvn.cmd spring-boot:run
~~~

队友请将 JDK 路径替换为自己电脑的路径，并保证 Maven 在 PATH 中；未安装系统 Maven 时可在 backend 使用 `mvnw.cmd spring-boot:run`，首次需要联网下载。两个服务都用 Ctrl+C 停止。

- 前端：通常为 http://localhost:5173，以 Vite 终端输出为准。
- 后端：http://127.0.0.1:8000。
- Swagger：http://127.0.0.1:8000/swagger-ui.html。

前端默认真实 API，`/api` 由 Vite 代理到后端。可参考 `.env.example` 创建本机 `.env.local`；保持 `VITE_USE_REAL_API=true`，修改环境变量后重启 Vite。若后端端口变化，设置 `API_PROXY_TARGET` 或调整代理配置。构建后部署需要另行配置 API 反向代理，不能假定 Vite 开发代理仍存在。

## 演示账号与权限

以下为数据库首次初始化的演示账号，初始密码统一为 `admin123`。已有数据库的账号不会在每次启动时被覆盖；这些公开演示密码仅适用于本地测试，不适用于公开部署。

| 账号 | 角色 | 权限与数据范围 |
| --- | --- | --- |
| admin | ADMIN / 系统管理员 | 全部业务、账号绑定、定时采集开关和模拟场景 |
| operator_east | OPERATOR / 运营人员 | 滨江花园、江南新城的设备、抄表、异常和反馈处置；不能收费 |
| finance01 | FINANCE / 财务人员 | 查询业务数据、生成账单及模拟收费；不能操作设备、抄表或处置反馈 |
| auditor01 | AUDITOR / 审计人员 | 管理端数据只读，不能执行业务变更 |
| user_res_001 | USER / 张明远 | 仅绑定水表 A-00218 的个人数据、本人缴费与反馈 |
| user_com_001 | USER / 青禾餐饮 | 绑定 B-00126，但账号禁用，不能登录 |

新用户从 `/register` 注册，只会创建 USER；客户端不能指定管理员角色或认领他人水表。注册后先显示“未绑定水表”和空业务数据，由 ADMIN 在“账号绑定”页面分配尚未被其他账号绑定的水表。绑定变更由后端按当前数据库身份生效；用户刷新个人页即可获取最新状态。

浏览器 localStorage 仅缓存 JWT 和会话资料，不保存账号密码或充当权限来源。后端每次请求检查账号是否启用及当前角色、辖区、绑定关系；修改浏览器中的角色不能获得权限。通知及已读状态保存在数据库，并按收件账号隔离。

## 页面入口

| 页面 | 路由 | 主要使用者 |
| --- | --- | --- |
| 登录 / 注册 | `/login`、`/register` | 所有人 |
| 运营总览 | `/admin/dashboard` | 管理角色 |
| 水表管理 / 抄表中心 | `/admin/meters`、`/admin/reading` | 管理角色查询，ADMIN/OPERATOR 操作 |
| 计费账单 | `/admin/billing` | 管理角色查询，ADMIN/FINANCE 生成与收费 |
| 异常中心 / 统计报表 | `/admin/anomalies`、`/admin/reports` | 按角色范围查询；异常仅 ADMIN/OPERATOR 处置 |
| 账号绑定 | `/admin/accounts` | 仅 ADMIN |
| 用户反馈 | `/admin/feedback` | ADMIN/OPERATOR 处置，AUDITOR 只读 |
| 个人工作台 | `/user/home` | USER |

## 业务注意事项

- 抄表任务与读数落库，页面轮询真实进度；可勾选“模拟一次采集失败”观察自动补抄。仍失败的任务可手动重试，不重复采集已成功水表。
- 定时模拟采集默认关闭，间隔 60 秒（配置值最小按 10 秒执行）；GET 查询和 PATCH 开关均仅 ADMIN 可用。页面开关只改变本次进程内存，重启后恢复启动配置，不等于持久化调度配置。
- 每个“水表 + 账期”生成一次账单快照。再次生成返回已有账单，不覆盖起止读数、水费/违约金率快照或已缴记录；应先完成抄表再生成。违约金及未缴应付金额可随实际逾期天数增加，已缴金额冻结。
- 支付金额由服务端根据账单计算，前端仅提交账单 ID 和幂等键；重复请求不重复记账。流水明确标记 SIMULATED，不发生真实扣款。
- 异常按“待处理 → 处理中 → 已完成”处置，同类未关闭报警去重。用户可查看本人异常但不能处置。
- USER 绑定后提交反馈，ADMIN 或辖区 OPERATOR 处理；结果通过本人通知返回。FINANCE 无反馈管理权限。
- AI 默认本地规则问答，不需要 API Key，只读取当前账号授权范围；不是大模型已验收的声明。

## 数据保护与交接

默认数据库是从 backend 目录启动时的 `backend/water.db`。Hibernate 启动时更新表结构，初始化器仅向对应空表写入演示数据，不清空已有业务记录。不要删除数据库来“重置”；演示会产生任务、账单、付款和通知，重复验收应使用独立副本。

首次运行更新版前，先停止后端并备份数据库；可通过 `SQLITE_PATH` 指向新的演示副本。勿同时用多个后端进程写同一 SQLite 文件。没有 `JWT_SECRET` 时会生成本机 `backend/.jwt-secret`；请保留它以维持已有会话，不要公开或提交。

共享源码时不提交数据库及备份、`.jwt-secret`、JWT/API Key、`.env.local`、其他私密配置或构建临时数据；不要直接执行未检查的 `git add .`。仓库仅交接源码与演示资料，队友首次启动会创建自己的演示数据库及 JWT 密钥。

默认使用 `start-all.bat`，不需要外部 AI 网关。保留的 `start-all-deepseek.bat` / `start-deepseek-gateway.bat` 是历史可选入口，依赖第三方 `token-free-gateway`（未锁版本），可能安装全局包并授权网页会话；未纳入本轮验证，不建议作为比赛演示入口，也不要提交其凭据。

## 验证与目录

~~~powershell
# 项目根目录
npm run build

# 后端目录；接口测试使用独立 SQLite 测试库
cd backend
mvn.cmd test
~~~

前端构建已通过；大图表 chunk 警告为后续优化项。后端最终测试 92 项（API 27、计费 62、JWT 3）全部通过，失败/错误/跳过均为 0。浏览器主要流程验收也已通过，实际观察与功能边界记录在《五项功能验收与演示指南.md》。

`test/` 中保留队友在 2026-09-22 提交的历史测试计划与模拟数据，其中旧版现状描述不代表当前 Java 版；本版验收以 `backend/src/test/` 和《五项功能验收与演示指南.md》为准。

~~~text
src/                  Vue 页面、组件、API、状态与类型
backend/src/main/     Java Controller、Service、安全配置与实体
backend/src/test/     计费、JWT 与 API 集成测试
package-lock.json     前端锁定依赖
start-all.bat         Windows 双终端启动入口
~~~

详细接口及后端配置见 `backend/README.md`；比赛演示按《五项功能验收与演示指南.md》执行。
