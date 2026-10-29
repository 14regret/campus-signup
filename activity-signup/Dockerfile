# syntax=docker/dockerfile:1

# ============================================================
# 构建阶段：用 Maven 镜像编译出可执行 jar
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom 再拉依赖：只要 pom 没变，这一层就能命中缓存，
# 改代码时不用每次重新下载几百 MB 依赖。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ============================================================
# 运行阶段：只带 JRE，不把 Maven 和源码带进镜像
# ============================================================
FROM eclipse-temurin:21-jre-alpine

# 非 root 运行：容器逃逸时少一分风险
RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

USER app
EXPOSE 8080

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Duser.timezone=Asia/Shanghai"

# exec 让 java 成为 PID 1，docker stop 时能收到 SIGTERM 优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
