# 校园活动报名系统

> 面向「开放报名瞬间抢名额」场景的校园活动报名系统，前后端分离。
> 学生申请名额，管理员发布活动与审核名单——核心解决**不超卖、不重复、状态可解释**。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.4-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=06131F)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](#license)

---

## Monorepo 结构

```
campus-signup/
├── activity-signup/                 # 后端模块（Spring Boot 3.5 + MySQL 8 + Redis）
│   ├── src/main/java/com/campus/signup/
│   │   ├── audit/                   # @OperationLog 注解 + AOP 审计切面
│   │   ├── controller/              # 认证 / 活动 / 报名 / 管理端
│   │   ├── service/                 # 业务编排，事务边界在这一层
│   │   ├── repository/              # 条件 UPDATE、状态 CAS
│   │   ├── entity/                  # 4 张表 + 状态枚举（内置状态机）
│   │   ├── lock/                    # Redis 分布式锁（SETNX + Lua 释放）
│   │   ├── job/                     # ShedLock 定时任务（推进 / 回收 / 对账）
│   │   └── config/                  # Security / JWT / OpenAPI / 调度
│   ├── src/main/resources/db/migration/   # Flyway 迁移脚本
│   ├── Dockerfile                   # 多阶段构建
│   ├── compose.yaml                 # MySQL + Redis + 后端
│   └── README.md                    # ★ 后端详细设计 & 压测报告
│
└── signup-web/                      # 前端模块（React 19 + Vite + Ant Design）
    ├── src/pages/                   # 首页 / 活动详情 / 我的报名 / 管理后台
    ├── src/components/              # 活动卡片、名额进度条、状态标签…
    ├── DESIGN.md                    # 设计系统（色板 / 字体 / 组件规范）
    └── PRODUCT.md                   # 产品定位与用户画像
```

前端通过 Vite 代理把 `/api` 转发到后端 `8080`，开发期不涉及跨域配置。

---

## 核心功能

- **活动管理**：草稿 → 发布 → 结束，支持取消并级联取消该活动下所有占位报名
- **两段式报名**：学生「申请报名」进入待确认，管理员确认后才正式占用名额
- **我的报名**：查看报名状态、提交时间，支持取消
- **报名审核**：名单查看、单条/批量确认、拒绝、撤销已确认（均会正确增减名额）
- **名额实时性**：剩余名额来自服务端，前端不做乐观扣减

## 技术亮点

### 1. 三重防线防超卖

```
Redis 分布式锁  →  数据库条件 UPDATE  →  唯一索引
（串行化，削峰）    （真正的闸门）        （最后兜底）
```

锁的 key 按活动隔离，值是随机 token，释放用 Lua 比对后删除；
真正的正确性来自 `WHERE signed_up_count < capacity` 这条原子更新——
**即使 Redis 挂掉、锁全部失效，也不会超卖**。

### 2. 无状态状态机 + ShedLock 定时任务

活动只持久化 `DRAFT / PUBLISHED / CANCELLED / FINISHED` 四个业务态，
「报名中 / 已截止」由时间推导，定时任务晚跑不影响正确性。
三个定时任务（状态推进、超时回收、计数对账）用 ShedLock 保证多实例只跑一次。

### 3. 基于 AOP 的操作审计

自定义 `@OperationLog` 注解 + 切面，自动记录操作人、动作、目标 ID 与入参快照到 `operation_log`。
业务先执行、成功才记日志；日志走独立事务（`REQUIRES_NEW`），**写入失败不影响主业务**。

### 4. 赛博朋克 / 毛玻璃风格 UI

「深夜教学楼的电子公告栏」为视觉北极星：哑光近黑底 + 半透明玻璃面 + 单一荧光青强调色。
PC 优先（三列卡片栅格），移动端响应式降级为单列 + 底部固定操作条；
名额进度条、等宽数字、骨架屏都按设计系统实现。

---

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
cd activity-signup
docker compose up -d --build
# 后端:      http://localhost:8080
# 接口文档:  http://localhost:8080/swagger-ui/index.html
```

首次启动会自动执行 Flyway 建表，并创建管理员账号 `admin / admin12345`。

### 方式二：分别启动

```bash
# 后端（需要本机 MySQL 8 + Redis 7）
cd activity-signup
./mvnw spring-boot:run

# 前端
cd signup-web
npm install
npm run dev          # http://localhost:5173
```

---

## 文档导航

| 文档 | 内容 |
|---|---|
| **[activity-signup/README.md](activity-signup/README.md)** | **后端并发架构设计、三重防线细节、`operation_log` 审计链路、100 并发压测报告** |
| [activity-signup/docs/concurrency-test.md](activity-signup/docs/concurrency-test.md) | 并发测试方法（JUnit + JMeter） |
| [activity-signup/docs/local-setup.md](activity-signup/docs/local-setup.md) | 无 Docker 时本地安装 MySQL / Redis |
| [signup-web/DESIGN.md](signup-web/DESIGN.md) | 前端设计系统（色板、字体、组件规范） |
| [signup-web/PRODUCT.md](signup-web/PRODUCT.md) | 产品定位、用户与场景 |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |

> 想快速了解这个项目「难在哪」，直接看后端 README 里的**核心亮点**和**并发压测**两节：
> 100 个线程抢 10 个名额，成功 10、满员 90、异常 0。

## License

MIT