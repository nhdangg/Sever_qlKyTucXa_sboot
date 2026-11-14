# Giai đoạn 1: Build file .jar bằng Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Giai đoạn 2: Chạy ứng dụng
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo sẵn thư mục uploads/avatars để tránh lỗi không tìm thấy đường dẫn khi lưu ảnh
RUN mkdir -p uploads/avatars

COPY --from=build /app/target/*.jar app.jar
ENV PORT = 8912
EXPOSE 8912
ENTRYPOINT ["java", "-Xmx380m", "-Xss256k", "-jar", "app.jar"]