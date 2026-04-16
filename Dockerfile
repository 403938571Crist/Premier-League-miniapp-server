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
# 使用 JRE 而非 JDK，显著减小生产镜像体积
FROM eclipse-temurin:17-jre
WORKDIR /app

# 从编译阶段复制 jar 包
COPY --from=builder /app/target/*.jar app.jar

# 云托管会通过环境变量注入端口，这里暴露 8080
EXPOSE 8080

# JVM 容器优化参数（可在 CloudBase Run 环境变量中覆盖 JAVA_OPTS）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# 明确的启动命令：先展开 JAVA_OPTS，再执行 java -jar
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
