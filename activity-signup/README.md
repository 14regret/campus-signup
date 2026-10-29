# 校园活动报名系统

> 把校园活动的「发布 — 报名 — 确认」从群接龙和在线表格里拿出来，做成一个**名额不会超发**的系统。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.4-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=06131F)](https://react.dev/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](#license)

---

## 简介

学生浏览活动、申请报名；管理员发布活动、审核名单。核心难点不在 CRUD，而在**热门活动开放报名的那几分钟**：
几十上百人同时点「申请报名」，系统必须保证最终占位人数不超过名额、同一个人不会出现两条报名。

这个项目把这几个问题当作主线来做：

| 问题 | 做法 |
|---|---|
| 并发抢名额 | Redis 分布式锁 + 数据库条件扣减 + 唯一索引，三重防线 |
| 状态流转 | 枚举内置状态机，非法流转在编译期之外的运行时被拦截 |
| 状态与时间不一致 | 只持久化 4 个业务态，「报名中/已截止」由时间推导，定时任务不承担正确性 |
| 名额计数漂移 | 每日对账任务按明细重算，加锁修正 |
| 管理动作无迹可查 | AOP 切面自动写 `operation_log`，独立事务，失败不影响业务 |

## 界面预览

> 截图待补充（首页活动列表 / 活动详情 / 我的报名 / 管理后台 / 报名审核）

| 首页 | 详情 |
|---|---|
| `docs/screenshots/home.png` | `docs/screenshots/detail.png` |

| 我的报名 | 管理后台 |
|---|---|
| `docs/screenshots/my-signups.png` | `docs/screenshots/admin.png` |

---

## 核心亮点

### 1. 三重防超卖

抢名额的请求进来后，依次经过三道关卡，**任意一道失效，结果依然正确**：

```
请求 → ① Redis 分布式锁 → ② 数据库条件 UPDATE → ③ 唯一索引
        (串行化，削峰)      (真正的闸门)          (最后兜底)
```

**① Redis 锁**（`lock:activity:{id}`，SET NX PX + Lua 校验释放）

锁的 key 按活动隔离，不同活动互不影响；值是随机 UUID，释放时用 Lua 比对 token 再删，
避免「A 的锁过期了，B 拿到锁，A 执行完把 B 的锁删掉」。锁的 TTL（10s）只是兜底，
防止持锁线程崩溃把整个活动锁死。

**② 条件扣减**（真正的正确性来源）

```sql
UPDATE activity
   SET signed_up_count = signed_up_count + 1, version = version + 1
 WHERE id = :id
   AND status = 'PUBLISHED'
   AND signed_up_count < capacity;
```

受影响行数 ≠ 1 即视为满员。这是数据库行锁保证的原子判断，
**即使 Redis 整个挂掉、锁全部失效，也不会超卖**。

**③ 唯一索引**（`uk_signup_activity_user`）

锁过期或 Redis 抖动时，两个请求可能同时走到 INSERT，此时唯一索引会拦下第二条，
事务回滚，刚扣掉的名额一并退回。

> 关键设计：**锁是优化，不是正确性依赖**。压测时把锁关掉，结果依然不会超卖，只是冲突变多、响应变慢。

**取消后重新报名**不是 INSERT，而是复用同一行做 `CANCELLED/REJECTED → PENDING` 的状态复活，
否则必然撞唯一索引。

### 2. AOP 操作日志审计

管理员的关键动作（发布、取消、确认、拒绝、撤销、批量审核）通过自定义注解自动落库，
不侵入业务代码：

```java
@OperationLog(action = "SIGNUP_REVOKE", targetType = "SIGNUP", targetId = "#signupId")
@PostMapping("/signups/{signupId}/revoke")
public Result<Void> revoke(@PathVariable Long signupId) { ... }
```

**表结构**（Flyway `V1__init.sql`）：

```sql
CREATE TABLE operation_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id   BIGINT                DEFAULT NULL COMMENT '操作人，系统任务为 NULL',
    operator_name VARCHAR(50)           DEFAULT NULL COMMENT '操作人快照，用户改名后日志仍可读',
    action        VARCHAR(40)  NOT NULL COMMENT 'ACTIVITY_PUBLISH / SIGNUP_CONFIRM ...',
    target_type   VARCHAR(20)  NOT NULL COMMENT 'ACTIVITY / SIGNUP',
    target_id     BIGINT                DEFAULT NULL,
    detail        JSON                  DEFAULT NULL COMMENT '方法名 + 入参快照（截断 500 字符）',
    ip            VARCHAR(45)           DEFAULT NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_log_operator_time (operator_id, created_at),
    KEY idx_log_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**链路**：

```
@RequestMapping 方法
   │
   ├─ @OperationLog 注解（action / targetType / targetId 的 SpEL）
   │
   ▼
OperationLogAspect（@Around，Order.HIGHEST_PRECEDENCE）
   ├─ 1. proceed()  —— 业务先执行；抛异常直接冒泡，不写日志
   ├─ 2. 从 MethodSignature 取注解
   ├─ 3. 解析 targetId（SpEL，如 "#signupId"）
   ├─ 4. 从 SecurityContext 取操作人（JwtPayload）
   ├─ 5. 取请求 IP
   └─ 6. 交给我 OperationLogRecorder 落库
          └─ @Transactional(REQUIRES_NEW)：独立事务 + try-catch
             写入失败只打 warn，绝不影响主业务
```

三个刻意的设计：

1. **先执行再记日志** —— 日志记的是「做成了什么」，不是「试过什么」。重复撤销返回 409 时不会产生日志。
2. **切面在事务外层** —— `proceed()` 返回意味着业务事务已提交，不会出现「日志说发布了，其实回滚了」。
3. **独立事务** —— 审计写入失败不会把业务事务标记成 rollback-only。

> 踩坑记录：最初用 `@Around("@annotation(operationLog)")` 做参数绑定，在 `@PreAuthorize` 的 CGLIB 代理链下
> 会抛 `IllegalStateException: Required to bind 2 arguments, but only bound 1`。
> 改成从 `MethodSignature.getMethod().getAnnotation(...)` 直接取注解后解决。

### 3. 无状态的活动状态机

活动只持久化 4 个业务态：

```
DRAFT ──发布──> PUBLISHED ──结束时间已过──> FINISHED
  │                │
  └──取消──> CANCELLED <──取消──┘
```

**「报名中 / 已截止 / 进行中」不是状态**，而是由 `signupStartAt / signupEndAt / startAt / endAt`
和当前时间推导出来的展示属性：

```java
public boolean isSignupOpen(LocalDateTime now) {
    return status == PUBLISHED
        && !now.isBefore(signupStartAt)
        && now.isBefore(signupEndAt);   // 左闭右开
}
```

这样做的好处：定时任务晚跑一分钟，用户也不会在截止后报名成功——正确性来自时间判断，
而不是「任务有没有准时跑」。定时任务只负责把真正结束的活动推到终态。

报名侧同理，`SignupStatus.canTransitTo()` 是唯一入口，非法流转（如 `CANCELLED → CONFIRMED`）直接拒绝，
不允许流转到自身，逼着调用方先把「幂等重复请求」处理掉。

---

## 并发压测

**场景**：100 个线程同时抢 10 个名额（真实 MySQL 8 + Redis，非 H2、非 mock）。

```
并发结果: 成功=10 名额已满=90 等锁超时=0 异常=0
```

**验收断言**（`SignupConcurrencyTest`）：

| 断言 | 结果 |
|---|---|
| 成功数 == 10 | ✅ |
| `activity.signed_up_count` == 10 | ✅ |
| 明细中 PENDING/CONFIRMED 行数 == 10 | ✅ |
| 明细中 userId 无重复 | ✅ |

复现方式：

```bash
# 需要真实 MySQL + Redis
export RUN_CONCURRENCY_TEST=true
export DB_USERNAME=root
export DB_PASSWORD=你的密码
./mvnw test -Dtest=SignupConcurrencyTest
```

更详细的 JMeter 手动压测步骤见 [`docs/concurrency-test.md`](docs/concurrency-test.md)。

---

## 架构

```
                        ┌──────────────────────────────┐
                        │  React 19 + Vite + Ant Design │
                        │  首页 / 详情 / 我的报名 / 后台  │
                        └──────────────┬───────────────┘
                                       │ /api (vite proxy)
┌──────────────────────────────────────▼───────────────────────────────────────┐
│                          Spring Boot 3.5 / Java 21                            │
│                                                                               │
│  Controller ──► Service ──► Repository                                        │
│      │            │  │                                                        │
│      │            │  └──► Redis（分布式锁 lock:activity:{id}）                 │
│      │            │                                                           │
│      │            └──► MySQL 8（条件更新 + 唯一索引 + 乐观锁）                 │
│      │                                                                        │
│      ├── JWT 过滤器（无状态认证，role 写进 claim）                             │
│      ├── @OperationLog 切面 ──► operation_log（独立事务）                      │
│      └── 全局异常处理 ──► 统一返回体 {code, message, data}                     │
│                                                                               │
│  定时任务（ShedLock 保证多实例只跑一次）                                        │
│    · 活动状态推进（每 5 分钟）                                                  │
│    · 报名超时回收（每 10 分钟，PENDING 超 24h 自动取消）                        │
│    · 计数对账（每日 03:00，按明细重算）                                         │
└───────────────────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 3.5.16 |
| 语言 | Java | 21 |
| 持久层 | Spring Data JPA / Hibernate | 6.6 |
| 数据库 | MySQL | 8.4 LTS |
| 缓存 / 锁 | Redis（Lettuce） | 7.4 |
| 认证 | JJWT | 0.12.6 |
| 定时任务互斥 | ShedLock | 6.10.0 |
| 接口文档 | springdoc-openapi | 2.10.0 |
| 迁移 | Flyway | 11.x |
| 前端 | React + Vite + TypeScript | 19 / 8 / 6 |
| UI | Ant Design | 6.x |

---

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
# 1. 起 MySQL + Redis + 后端（首次会构建镜像，约 2-3 分钟）
docker compose up -d --build

# 2. 看启动日志，等到 "Started ActivitySignupApplication"
docker compose logs -f app

# 3. 打开接口文档
open http://localhost:8080/swagger-ui/index.html
```

首次启动会自动：
- 执行 Flyway 迁移建表；
- 创建管理员账号 `admin / admin12345`（可用 `ADMIN_PASSWORD` 覆盖）。

停止并保留数据：`docker compose down`
清空数据重来：`docker compose down -v`

### 方式二：本地运行

前置：JDK 21、Maven（或直接用 `./mvnw`）、本机 MySQL 8 + Redis 7。

```bash
# 1. 建库建用户
mysql -uroot -p -e "
  CREATE DATABASE campus_signup DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE USER 'campus'@'%' IDENTIFIED BY 'campus123';
  GRANT ALL PRIVILEGES ON campus_signup.* TO 'campus'@'%';"

# 2. 启动
export DB_USERNAME=campus
export DB_PASSWORD=campus123
./mvnw spring-boot:run
```

没有 Docker 时如何在本机装 MySQL + Redis，见 [`docs/local-setup.md`](docs/local-setup.md)。

---

## 接口文档

启动后访问 **<http://localhost:8080/swagger-ui/index.html>**

点右上角 **Authorize**，填入登录接口返回的 token（不用带 `Bearer` 前缀），之后所有需要认证的接口都会自动带上。

### 接口概览

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/auth/register` | 公开 | 注册（固定 USER 角色） |
| POST | `/api/auth/login` | 公开 | 登录，返回 JWT |
| GET | `/api/activities` | USER | 活动列表（仅已发布） |
| GET | `/api/activities/{id}` | USER | 活动详情 |
| POST | `/api/signups/{activityId}` | USER | 报名 |
| PUT | `/api/signups/{id}/cancel` | USER | 取消自己的报名 |
| GET | `/api/signups/my` | USER | 我的报名 |
| GET | `/api/admin/activities` | ADMIN | 活动列表（全状态） |
| POST | `/api/admin/activities` | ADMIN | 创建活动（草稿） |
| PUT | `/api/admin/activities/{id}` | ADMIN | 编辑（仅草稿） |
| POST | `/api/admin/activities/{id}/publish` | ADMIN | 发布 |
| POST | `/api/admin/activities/{id}/cancel` | ADMIN | 取消（级联取消报名） |
| GET | `/api/admin/activities/{id}/signups` | ADMIN | 报名名单 |
| POST | `/api/admin/signups/{id}/confirm` | ADMIN | 确认 |
| POST | `/api/admin/signups/{id}/reject` | ADMIN | 拒绝（退名额） |
| POST | `/api/admin/signups/{id}/revoke` | ADMIN | 撤销已确认（退名额） |
| POST | `/api/admin/signups/batch-review` | ADMIN | 批量确认/拒绝/撤销 |

### 统一返回体

```json
{ "code": 0, "message": "ok", "data": { } }
```

`code == 0` 表示成功；失败时 HTTP 状态码与业务码同时给出。业务码分段：

| 段 | 含义 |
|---|---|
| `1xxx` | 通用 / 认证（1001 参数错误、1002 未登录、1003 过期、1005 无权限） |
| `2xxx` | 用户（2001 用户名已存在、2002 登录失败） |
| `3xxx` | 活动（3001 不存在、3003 不可编辑、3005 时间非法） |
| `4xxx` | 报名（4001 名额已满、4002 重复报名、4004 状态不允许、4006 等锁超时） |

---

## 项目结构

```
src/main/java/com/campus/signup
├── audit/         @OperationLog 注解 + 切面 + 独立事务记录器
├── common/        Result / PageResult
├── config/        Security、JWT、锁、定时任务、OpenAPI
├── controller/    认证 / 活动 / 报名 / 管理端
├── dto/           按模块分包的请求与响应
├── entity/        4 张表的实体 + 状态枚举
├── exception/     错误码、业务异常、全局异常处理
├── job/           状态推进 / 超时回收 / 计数对账
├── lock/          Redis 分布式锁
├── repository/    JPA 仓储（含条件 UPDATE 与 CAS）
├── security/      JWT 过滤器、@CurrentUser 解析器
└── service/       业务编排（事务边界在这一层）
```

前端仓库：`../signup-web`（React + Vite + TS + Ant Design），设计规范见其 `DESIGN.md`。

---

## 一些工程约定

- **表结构只由 Flyway 管理**，`ddl-auto=validate`，实体和库对不上会在启动时直接失败。
- **不建物理外键**：靠唯一索引和应用层校验保证一致性，便于归档与后续分表。
- **时间统一 `DATETIME(3)` + `Asia/Shanghai`**，不用 `TIMESTAMP`（2038 与时区自动转换都不可控）。
- **数字用等宽字体 + `tabular-nums`**：名额从 9 变成 10 时布局不抖动（前端规范）。
- **多实例安全**：定时任务用 ShedLock（Redis 实现），锁名与 `lockAtMostFor` 写在注解上。

## 设计权衡：为什么不用 MQ 和 Elasticsearch

面试时常被问「为什么不用 RabbitMQ / Kafka 削峰」「为什么不上 ES 做搜索」。结论先说：
**当前量级下，引入这两样属于过度设计。**

前提是：热门活动开放报名的瞬间也就百人级并发，数据量是「百级活动 × 百级报名」。
在这个量级上，技术选型标准不是「能不能扛更大」，而是「用最小的运维成本和心智负担把事情做对」。

| | 引入 MQ | 引入 Elasticsearch |
|---|---|---|
| 能解决什么 | 削峰、异步解耦、失败重试 | 全文检索、聚合分析、复杂筛选 |
| 代价 | 多一个中间件要部署、监控、处理堆积与重复消费；还要接受「报名结果不再同步返回」 | 多一套索引与同步链路（双写或 Canal），还要处理索引与库不一致 |
| 本项目的真实需求 | 报名必须**同步**返回「报上了没有」，异步化反而破坏体验 | 只有「按标题搜活动」一个查询，MySQL 的 `LIKE` + 索引足够 |

具体判断：

- **不引 MQ**：名额判定必须同步且强一致——用户点完按钮要立刻知道结果。
  真要削峰，Redis 锁已经在数据库前挡了一层；再往上堆 MQ，等于把一个同步问题拆成两个异步问题
  （提交 + 回调），复杂度翻倍而收益为零。
- **不引 ES**：活动检索只有关键词匹配，百级数据量下全表扫描都在毫秒级。
  ES 的价值在复杂查询与海量数据，这里一条都用不上。

**保持轻量、单机高可用**是现阶段的合理选择：一套 Compose 就能起完整环境，
没有额外组件需要人盯。同时这套代码也**给未来留了口子**——
`SignupService` 的「锁 → 事务 → 条件更新」边界是收敛的，
将来真要做「报名成功后发通知 / 发券」，只需在事务提交后投递一条事件，主链路不用改；
检索量级上来后，也可以加 ES 做读侧索引，写仍然只写 MySQL。

## Roadmap

- [x] 报名超时自动取消（PENDING 24h）
- [x] 活动取消时级联取消报名
- [x] 管理员撤销已确认报名
- [x] 操作日志审计
- [ ] 候补队列（名额满后排队，有人取消自动补位）
- [ ] 活动封面图上传（对象存储）
- [ ] 站内消息通知（确认 / 拒绝 / 即将开始）

## License

MIT
