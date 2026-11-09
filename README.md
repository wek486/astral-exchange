# 星际交易所 · Astral Exchange

**上一根 K 线还在庆功，下一根已经掉头。你的仓位能撑到收盘吗？**

🎮 **[在线试玩 · AI 股「空中飞人」](https://wek486.github.io/astral-exchange/)** — 打开就玩，无需安装或注册。

这是一款把**原神 × 崩坏：星穹铁道**放进同一个虚构交易市场的同人小游戏。北国银行、黑塔科研和星际和平公司都能交易；每个交易日，你和其他玩家拿着同样的初始资金出发。

### 先玩一局，再决定要不要开大仓

1. **选方向。** 黑塔科研拿到算力订单，你选择做多，还是提前做空？
2. **选杠杆。** 从 1× 到 100×，用同一笔保证金观察强平价怎么变化。
3. **揭晓下一幕。** 消息、抛售、反弹依次登场。换个仓位重玩，看看这次能不能撑到收盘。

「空中飞人」是给价格轨迹起的绰号。关卡的氛围受[《FX 战士久留美》](https://fxkurumi-info.com/)启发，剧情借了 [2026 年 11 月初 AI 股抛售](https://apnews.com/article/7ee9c212805aa5e70cf34d25aab7a36e)的题材。剧本、标的、价格和新闻均为虚构，纯模拟，无充值提现。没有使用官方角色立绘或动画素材。

| 玩法 | 你能体验到什么 |
| --- | --- |
| **网页试玩** | 固定剧情，独立结算，可反复比较多空方向和不同杠杆，不影响多人账户 |
| **多人市场** | 八个虚构标的、实时 K 线、玩家下单影响价格、逐仓强平和每日排行榜 |

多人市场可以在本地运行，也可以按下文部署自己的服务器。GitHub Pages 托管免登录试玩。

## 部署多人版

根目录 `Dockerfile` 打包 Vue 和 Spring Boot，浏览器从同一个站点访问页面、API 和行情推送。应用需要一套**独立的 MySQL 8**，首次启动自动迁移数据库。

例如在 [Railway](https://railway.com/guides/spring-boot) 新建项目，添加 MySQL 服务，再从本仓库部署应用服务。应用从根目录构建 `Dockerfile`，只运行 **1 个副本**。在应用服务配置以下变量，数据库服务名称不同时替换 `MySQL`：

| 变量 | 值 |
| --- | --- |
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8` |
| `DB_USER` | `${{MySQL.MYSQLUSER}}`（有条件时使用仅能访问该库的应用账号） |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `COOKIE_SECURE` | `true` |

给应用服务生成 HTTPS 域名即可分享；数据库保持内网访问。托管费用和试用条件以 [Railway 的定价](https://railway.com/pricing)为准。

更新免登录试玩的静态构建：在 `frontend/` 执行 `npm ci`，再运行 `npm run build -- --mode demo --base=./`，将产物发布到 `gh-pages` 分支根目录（保留 `.nojekyll`）。

## 本地启动

需要 **JDK 21 或更新版本、Maven 3.9、Node.js 22.12+、MySQL 8**。项目不使用 Redis。确保 `JAVA_HOME` 指向实际 JDK，且 Java、Maven、Node 在 PATH 中。

### 方式一：使用已有 MySQL

1. 执行 `database/create-database.sql`，创建数据库和应用用户。
2. 把根目录 `.env.example` 复制为 `.env`，填写数据库连接和密码。`.env` 已被 Git 忽略。
3. 在两个 PowerShell 窗口分别运行：

```powershell
./scripts/start-backend.ps1
```

```powershell
./scripts/start-frontend.ps1
```

访问 **http://127.0.0.1:5173**，后端位于 **http://127.0.0.1:8080**。首次启动 Flyway 自动建表并初始化八个标的，不创建默认玩家账户。

### 方式二：使用隔离的 Windows MySQL 实例

本机已安装 MySQL Server、但未启动数据库时，可运行：

```powershell
./scripts/init-local-mysql.ps1
```

该脚本只在项目 `.local/mysql-data` 下创建独立数据，绑定 `127.0.0.1:3307`，随机生成数据库密码写入被忽略的 `.env`，不修改系统 MySQL 服务。若安装路径不同，传入 `-MySqlBin '你的 MySQL/bin 路径'`。已有 `.env` 时脚本会拒绝覆盖。

然后运行前后端启动脚本。电脑重启后，先运行 `./scripts/restart-local-mysql.ps1` 恢复这个独立数据库。不要删除 `.local/mysql-data`，其中保存账户和交易数据。

### macOS / Linux

准备 MySQL，使用 `.env.example` 中的同名环境变量运行 `mvn spring-boot:run`；前端目录执行 `npm ci && npm run dev`。后端自动读取进程环境变量，根目录 `.env` 由 PowerShell 启动脚本加载，其他系统需自行导出变量。

## 交易规则

- 每日北京时间零点结算，每个账户重新获得 **100,000** 虚拟信用点，历史每日榜单永久保存。新用户立即获得本日本金。
- 仅支持市价做多、做空与逐笔全部平仓。保证金至少 **100**，杠杆为 **1、5、10、20、50、100** 倍。
- 开仓名义金额 = 保证金 × 杠杆。开仓扣除保证金与名义金额 **0.05%** 的手续费，平仓也按成交金额收取 **0.05%**。
- 恒定乘积池报价，买入减少基础资产储备、增加报价储备，卖出相反。开多和平空为买入，开空和平多为卖出。成交有价格冲击，不保证按屏幕最新价成交。
- 单笔名义规模不超过报价池 **1%**，总空头数量不超过成交后基础资产储备 **25%**，为平空预留流动性。
- 浮盈亏 = 方向符号 × 数量 ×（标记价 − 开仓价）。保证金 + 浮盈亏 ≤ 当前名义仓位 × **0.5%** 时触发强平。
- 强平按实际平仓均价结算，回到账户的资金最低为零，不扣其他持仓保证金，不形成负债。界面强平价是触发参考价，不是保证成交价。
- 无持仓且余额不足 100 时进入旁观状态，下一赛季重新开始。每笔记录会区分开仓、平仓、强平和赛季结算。
- 系统每 2 秒产生微小报价扰动，每分钟至多一条虚构利好或利空新闻，新闻会调整报价储备。系统活动不计入玩家成交量。玩家成交仍通过做市池影响价格。
- 日终使用统一截止标记价结算持仓并收取平仓费，不让结算顺序影响价格。停机期间不补算波动，跨日重启先结算旧赛季，再开启当前赛季。

## 数据与一致性

MySQL 保存用户、账户、持仓、交易、做市池、分钟 K 线、新闻、赛季、榜单和 Spring Session 登录会话。金额、数量和价格使用 Java `BigDecimal` 与 MySQL `DECIMAL(30,10)`。

单后端实例通过进程内锁串行化交易，在数据库事务内提交全部经济状态，再刷新行情快照并发布 SSE。失败不会留下部分资金或持仓变更。`requestId` 是按用户隔离的 UUID，同一内容可重试，不同内容复用编号会被拒绝。

**仅支持一个后端实例**，不要启动多个实例共同操作同一个数据库；多实例部署需要先改造交易队列与协调机制。后端重启从 MySQL 恢复，浏览器刷新和后端重启后会话均可保留；退出登录会失效会话。

浏览器断线时保留最后一次显示数据并标识连接状态，SSE 自动重连后接收完整市场快照，并重新读取账户。K 线只来自实际运行时记录，不伪造历史数据；新市场需要等待分钟数据积累。当前图表展示最近 360 根分钟 K 线，成交页面展示最近 60 笔，数据库保留完整记录。

## API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/csrf` | 获取 CSRF 令牌 |
| POST | `/api/auth/register`、`/api/auth/login` | `{username,password}` 注册 / 登录 |
| POST | `/api/logout` | 退出登录 |
| GET | `/api/market` | 全市场完整快照及赛季时间 |
| GET | `/api/events` | SSE，事件名 `market`，携带完整公共快照 |
| GET | `/api/candles/{symbol}` | 分钟 OHLCV，时间为 Unix 秒 |
| GET | `/api/account` | 当前用户资金、持仓、最近成交 |
| POST | `/api/orders/preview` | 预计成交均价、费用和强平价 |
| POST | `/api/orders` | 市价开仓 |
| POST | `/api/positions/{id}/close` | `{requestId}` 全部平仓 |
| GET | `/api/ranking?day=YYYY-MM-DD` | 实时或指定历史赛季榜单 |

开仓及预览请求：

```json
{"requestId":"唯一 UUID","symbol":"IPC","side":"LONG","margin":1000,"leverage":5}
```

`side` 支持 `LONG` / `SHORT`。账户、交易及预览接口需要登录。所有写请求需携带 `X-XSRF-TOKEN`，值取自 `/api/csrf` 返回的 `token`，同时发送会话和 CSRF Cookie。前端已封装。注册及登录按来源地址限制每分钟 20 次。

## 构建和验证

```powershell
cd frontend
npm.cmd ci
npm.cmd run build
```

```powershell
cd backend
mvn.cmd test
mvn.cmd package -DskipTests
```

普通 `mvn test` 使用仅测试范围的 H2 MySQL 模式，应用运行仍只使用 MySQL。用真实 MySQL 验证：先创建 `astral_exchange_test` 并授予应用用户权限（隔离初始化脚本已自动创建），再在根目录运行：

```powershell
./scripts/test-mysql.ps1
```

**测试会清空专用测试库中的业务表，不能指向真实业务库。** 自定义测试配置可通过 `TEST_DB_URL`、`TEST_DB_USER`、`TEST_DB_PASSWORD`、`TEST_DB_DRIVER` 环境变量传入。

后端启动后运行 `node scripts/api-smoke.mjs` 可验证双账户、重复提交、市价成交和平仓、SSE 与退出登录；该脚本会创建两个测试玩家并留下成交记录，不会修改其他玩家。设置 `API_BASE` 可更换后端地址。

## 项目结构与当前边界

`frontend/` 为完整 Vue 界面，`backend/` 为 Spring Boot 和 Flyway 迁移，`scripts/` 为本地启动与验证脚本，`database/` 为建库示例。

当前版本不包含充值提现、真实金融市场、限价单、管理后台和 Redis。字体网络不可用时会自动回退本机字体。行情、角色组织名称和事件均用于非官方同人模拟。

未来上线时需配置 HTTPS、`COOKIE_SECURE=true`、同源反向代理和单实例运行，并按实际访问量评估数据库备份、行情归档与负载能力。当前版本未进行大规模并发压测。

图表使用 [TradingView Lightweight Charts](https://tradingview.github.io/lightweight-charts/)，登录会话采用 [Spring Session JDBC](https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html)。
