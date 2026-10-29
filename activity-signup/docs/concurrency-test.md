# 并发抢名额测试说明

目标：验证「100 个并发请求抢 10 个名额」时，最终**恰好 10 个成功、0 超卖、0 重复报名**。

两条路，任选：

- **A. JUnit（推荐）**：已经写好在代码里，一条命令跑完，自动断言三个不变量并清理数据。
- **B. JMeter**：手动压测，适合看吞吐、响应时间分布。

---

## A. JUnit 方式

测试类：`src/test/java/com/campus/signup/service/SignupConcurrencyTest.java`

它默认**不执行**（`@EnabledIfEnvironmentVariable`），否则 `mvn test` 会在没有 MySQL/Redis 的机器上失败。

```powershell
$env:RUN_CONCURRENCY_TEST = 'true'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '你的密码'
.\mvnw.cmd test -Dtest=SignupConcurrencyTest
```

### 断言了什么

| 断言 | 含义 |
|---|---|
| `成功数 == 10` | 不多不少，既没超卖也没漏卖 |
| `activity.signed_up_count == 10` | 冗余计数正确 |
| `signup 表中 PENDING/CONFIRMED 行数 == 10` | 明细与计数一致 |
| `userId 不重复` | 唯一索引确实生效，没有一人多条 |

### 实测输出（本机 MySQL 8 + Redis）

```
并发结果: 成功=10 名额已满=90 等锁超时=0 异常=0
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

### 测试做了什么

1. 建一个 `PUBLISHED` 活动，capacity=10，报名窗口覆盖当前时刻；
2. 起 100 个线程，用 `CountDownLatch` 让它们**同时**发起报名（每个线程用不同的 userId）；
3. 收集结果：成功 / 名额已满 / 等锁超时 / 其他异常；
4. 断言三组数字一致；
5. `@AfterEach` 删掉本次产生的报名和活动，不污染库。

> 测试里把 `app.lock.wait-time` 放大到 60s，避免机器慢时请求因等锁超时失败，干扰「成功数」的断言。

---

## B. JMeter 方式

JMeter 的难点不是压测本身，而是**每个请求要带不同的 token**（同一个用户重复报名会被 `ALREADY_SIGNED_UP` 挡掉，测不出超卖）。

### 1. 准备 100 个用户和 token

```powershell
# 注册 100 个用户并导出 tokens.csv（首行是变量名，JMeter 需要）
$rows = @('token')
for ($i = 1; $i -le 100; $i++) {
    $u = "jmeter$i"
    $p = 'JmeterPass2026'
    try {
        Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/register' `
            -ContentType 'application/json' `
            -Body (@{ username = $u; password = $p } | ConvertTo-Json) | Out-Null
    } catch { }   # 已存在则忽略
    $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' `
        -ContentType 'application/json' `
        -Body (@{ username = $u; password = $p } | ConvertTo-Json)
    $rows += $login.data.token
}
$rows | Set-Content -Path tokens.csv -Encoding UTF8
```

### 2. 建一个名额 10 的活动并发布

Postman 里用 admin 调：

```
POST /api/admin/activities           → 记下 id，比如 1
POST /api/admin/activities/1/publish
```

body 里 `capacity: 10`，报名窗口覆盖当前时间。

### 3. JMeter 配置

| 组件 | 配置 |
|---|---|
| Test Plan → 用户定义的变量 | `host=localhost`、`port=8080`、`activityId=1` |
| Thread Group | Threads = **100**，Ramp-up = **1**，Loop Count = **1** |
| CSV Data Set Config | Filename=`tokens.csv`，Variable Names=`token`，Recycle on EOF=false，Sharing mode=All threads |
| HTTP Request | Method=**POST**，Path=`/api/signups/${activityId}`，Server=`${host}`，Port=`${port}` |
| HTTP Header Manager | `Authorization` = `Bearer ${token}` |
| 监听器 | Summary Report + View Results Tree（调试时开，正式压测关掉） |

建议在 HTTP Request 下加一个 **Response Assertion**，只校验 `HTTP 201`，这样 Summary Report 里的 error% 就是「没抢到」的比例。

### 4. 跑

```powershell
jmeter -n -t signup-concurrency.jmx -l result.jtl
```

`-n` 是无界面模式，跑完看 `result.jtl` 或者生成报告：

```powershell
jmeter -g result.jtl -o report
```

### 5. 用 SQL 验结果（最关键的一步）

```sql
-- 必须是 10，不能是 11
SELECT signed_up_count FROM activity WHERE id = 1;

-- 必须是 10
SELECT COUNT(*) FROM signup WHERE activity_id = 1 AND status IN ('PENDING','CONFIRMED');

-- 必须没有重复，返回空
SELECT user_id, COUNT(*) c FROM signup
 WHERE activity_id = 1 AND status IN ('PENDING','CONFIRMED')
 GROUP BY user_id HAVING c > 1;
```

JMeter 里的合格线：`HTTP 201` 恰好 10 条，其余 90 条是 `4001 名额已满`。如果出现 `4006 等锁超时`，说明 `app.lock.wait-time` 太小或机器太慢，不算超卖，但会影响体验。

---

## 出问题怎么判断

| 现象 | 可能原因 |
|---|---|
| 成功数 > 10 | 条件 UPDATE 被改坏了（`where signed_up_count < capacity` 丢了），锁再严也没用 |
| 成功数 < 10 且大量 4006 | 锁等待时间太短 / Redis 慢 |
| 成功数 < 10 且大量 401 | token 过期或 CSV 只读到了一行（Sharing mode 配错） |
| 出现重复 userId | 唯一索引没建，或者「重新报名」走了 INSERT 而不是行复用 |
| 计数为 0 但明细有 10 条 | 扣减和插入不在同一个事务里 |
