# 使用OpenJDK 17作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制Maven配置文件
COPY pom.xml ./
COPY src ./src

# 如果已有编译好的jar文件，可以直接复制（推荐）
# COPY target/lcsc-crawler-1.0.0.jar app.jar

# 或者在容器中编译（需要Maven）
RUN apt-get update && apt-get install -y maven \
    && mvn clean package -DskipTests \
    && mv target/lcsc-crawler-*.jar app.jar \
    && rm -rf target src pom.xml ~/.m2 \
    && apt-get remove -y maven \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

# 创建日志目录
RUN mkdir -p /app/logs /app/data

# 创建非root用户
RUN groupadd -r lcsc && useradd -r -g lcsc lcsc
RUN chown -R lcsc:lcsc /app
USER lcsc

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 设置JVM参数和启动应用
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.awt.headless=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]