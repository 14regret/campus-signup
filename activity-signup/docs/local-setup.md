# 本机直装 MySQL 8 + Redis（没有 Docker 时的替代方案）

> 装了 Docker Desktop 之后直接 `docker compose up -d` 更省事（`compose.yaml` 在第五步给）。
> 这份文档是兜底路径，两条路的库名、账号、密码必须和 `src/main/resources/application.yml` 对齐。

当前配置约定：

| 项 | 值 |
|---|---|
| 库名 | `campus_signup` |
| 账号 / 密码 | `campus` / `campus123` |
| MySQL 端口 | `3306` |
| Redis 端口 | `6379`，本机默认**不设密码**（设了就把 yml 里 `password` 那行打开） |

---

## 一、MySQL 8

### 1. 安装

两条路，选一条：

- **MSI（推荐新手）**：dev.mysql.com 下载 `MySQL Installer for Windows`，选 `Server only`，
  安装时设 root 密码，端口保持 3306，Windows Service 勾上（开机自启）。
- **ZIP 免安装**：下载 `mysql-8.4.x-winx64.zip`，解压到 `D:\mysql-8.4`，然后：

```powershell
# 初始化数据目录（只需要一次）
D:\mysql-8.4\bin\mysqld --initialize-insecure --datadir=D:\mysql-8.4\data

# 前台启动，方便看日志
D:\mysql-8.4\bin\mysqld --console --datadir=D:\mysql-8.4\data
```

`--initialize-insecure` 表示 root 无密码，仅本机开发可接受。

### 2. 建库建账号

```sql
CREATE DATABASE IF NOT EXISTS campus_signup
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'campus'@'%' IDENTIFIED BY 'campus123';
GRANT ALL PRIVILEGES ON campus_signup.* TO 'campus'@'%';
FLUSH PRIVILEGES;
```

Flyway 建表需要 `CREATE / ALTER / INDEX / REFERENCES` 权限，`GRANT ALL` 已包含，不用再单独给。

### 3. 验证

```powershell
mysql -u campus -p -e "SELECT VERSION(); SHOW DATABASES;"
```

看到 `8.0.x` / `8.4.x` 和 `campus_signup` 就对了。

> **别手动建表。** 4 张表由 Flyway 在应用启动时执行 `db/migration/V1__init.sql` 创建，
> 手工建会撞上 `ddl-auto=validate` 的字段比对。

---

## 二、Redis

Redis 官方不提供 Windows 版，三个可选方案，按推荐度排序：

### 方案 A：Memurai（最省事，Windows 原生服务）

- 官网下 `Memurai Developer`（免费），MSI 一路下一步，默认 6379 端口、注册为 Windows 服务。
- 验证：`memurai-cli ping` → `PONG`

### 方案 B：tporadowski/redis（Redis 5.0.14 的 Windows 移植）

- GitHub `tporadowski/redis` 下 release zip，解压后：

```powershell
.\redis-server.exe                      # 前台
.\redis-server.exe --service-install    # 装成服务
.\redis-cli.exe ping                    # 应返回 PONG
```

功能足够本项目（Lua 脚本、SETNX、过期时间都支持）。

### 方案 C：WSL2（最接近生产）

```powershell
wsl --install -d Ubuntu
# 进入 Ubuntu 后
sudo apt update && sudo apt install -y redis-server
sudo service redis-server start
redis-cli ping
```

注意 WSL2 的 Redis 默认只监听 `127.0.0.1`，Windows 侧能直连；改了 `bind` 或 `protected-mode` 才需要额外处理。

### 如果要设密码

在 `redis.conf`（或 Memurai 的配置文件）里加：

```
requirepass campus123
```

同时把 `application.yml` 里被注释掉的 `password: campus123` 打开。两边不一致的报错长这样：

- 服务端没密码、客户端发了 AUTH → `ERR Client sent AUTH, but no password is set`
- 服务端有密码、客户端没发 → `NOAUTH Authentication required`

---

## 三、常见坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `Public Key Retrieval is not allowed` | MySQL 8 默认 `caching_sha2_password`，非 SSL 连接需要取公钥 | JDBC URL 已带 `allowPublicKeyRetrieval=true`，别删 |
| 启动报 `Schema-validation: missing table [activity]` | 连到了空库，Flyway 没跑或没连对库 | 检查 `flyway.enabled=true`、URL 里的库名 |
| 启动报 `Schema-validation: wrong column type` | 手改过表或手动建了表 | 删库重建，让 Flyway 全新建表 |
| 时间整体差 8 小时 | MySQL 服务端时区是 UTC | `SET GLOBAL time_zone = '+08:00';` 或 my.ini 里 `default-time-zone='+08:00'` |
| `Access denied for user 'campus'@'localhost'` | 账号只建了 `'campus'@'%'` 而本地走 socket/主机名匹配 | 补一条 `'campus'@'localhost'`，或统一用 `127.0.0.1` 连接 |
| 3306 被占用 | 之前装过 MySQL | `netstat -ano | findstr :3306` 找到 PID 后停掉旧服务 |

---

## 四、启动顺序

1. 起 MySQL → 2. 起 Redis → 3. 项目根目录执行 `.\mvnw.cmd spring-boot:run`

启动日志里应看到：

```
Successfully applied 1 migration to schema `campus_signup`, now at version v1
```

以及 Tomcat 起在 8080。
