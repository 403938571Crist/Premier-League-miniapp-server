# =================== 编译阶段 ===================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 先复制 pom.xml 利用 Docker 缓存层
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源码并编译
COPY src ./src
RUN mvn clean package -DskipTests

# =================== 运行阶段 ===================
FROM eclipse-temurin:17-jdk
WORKDIR /app

# 从编译阶段复制 jar 包
COPY --from=builder /app/target/*.jar app.jar

# 云托管会通过环境变量注入端口，这里暴露 8080
EXPOSE 8080

# 启动服务（带容器感知优化）
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
