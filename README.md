# 智水云——AI 水表抄表收费管理系统前端

竞赛原型前端，覆盖管理员端和居民用户端。默认使用模拟数据，可以独立演示；FastAPI 后端准备完成后，可通过环境变量切换为真实 API。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router：页面路由与按页面拆包
- Pinia：角色、业务数据和界面状态
- Element Plus：表格、表单、弹窗和反馈组件
- ECharts：用水趋势与用户类型图表
- Axios：FastAPI 接口请求

## 本地运行

```powershell
npm install
npm run dev
```

浏览器访问终端输出的地址，通常为 `http://localhost:5173`。

生产构建：

```powershell
npm run build
npm run preview
```

## 页面路由

| 页面 | 地址 |
| --- | --- |
| 运营总览 | `/admin/dashboard` |
| 水表管理 | `/admin/meters` |
| 抄表中心 | `/admin/reading` |
| 计费账单 | `/admin/billing` |
| 异常中心 | `/admin/anomalies` |
| 统计报表 | `/admin/reports` |
| 居民用户端 | `/user/home` |

## 对接 FastAPI

复制 `.env.example` 为 `.env.local`，然后修改：

```dotenv
VITE_USE_REAL_API=true
VITE_API_BASE_URL=/api
```

开发服务器会把 `/api` 请求代理到 `http://127.0.0.1:8000`。接口定义集中在 `src/api`，后端地址变化时只需要修改环境变量或 `vite.config.ts`。

AI 助手的真实接口约定：

```http
POST /api/ai/query
Content-Type: application/json

{"question":"哪个小区本月用水量最高？"}
```

响应格式：

```json
{"answer":"中央商务区本月用水量最高……"}
```

## 目录结构

```text
src/
├─ api/          Axios 客户端和接口定义
├─ components/   公共组件
├─ layouts/      管理端/用户端公共布局
├─ mocks/        演示模拟数据
├─ router/       路由配置
├─ stores/       Pinia 状态
├─ types/        TypeScript 业务类型
├─ utils/        报表导出等工具
└─ views/        各业务页面
```

## 团队开发建议

每位成员在独立分支开发，完成后通过 Pull Request 合并：

```powershell
git pull
git switch -c feature/姓名-功能
git add .
git commit -m "完成某功能"
git push -u origin feature/姓名-功能
```

不要把 `.env`、数据库密码或 API Key 提交到 GitHub。
