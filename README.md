# Mini Discord

Mini Discord là một ứng dụng chat nhỏ theo phong cách Discord, gồm backend Spring Boot, frontend React/Vite và hạ tầng Docker cho MongoDB, Redis, MinIO và Nginx.

## Scope

Repo tập trung vào các luồng chính của một ứng dụng chat:

- xác thực người dùng
- tạo và quản lý server/channel
- gửi tin nhắn realtime
- upload file đính kèm
- mời thành viên vào server
- thông báo và hỗ trợ tìm kiếm message

## Tính năng chính

- Đăng ký, đăng nhập, refresh token, logout
- Tạo server, tự tạo channel `general`
- Quản lý member theo role `OWNER` và `MEMBER`
- Chat realtime qua WebSocket/STOMP
- Upload file lên MinIO
- Invite bằng code và direct invite
- Notification cho invite
- Search message qua text index trên MongoDB

## Cấu trúc thư mục

```text
mini-discord/
  backend/   Spring Boot 4, Java 21, MongoDB, Redis, MinIO, WebSocket
  frontend/  React 19, TypeScript, Vite, TanStack Query, Zustand
  infra/     Docker Compose, Nginx, MongoDB replica set, Redis, MinIO
```

## Yêu cầu môi trường

- Docker Desktop
- Java 21+ nếu chạy backend local
- Node.js/npm nếu chạy frontend local

## Setup

Tạo file `.env` ở root từ `.env.example` và chỉnh các biến cần thiết.

Các giá trị dev mặc định đã được chuẩn bị cho local và Docker Compose.

## Cách chạy

### 1. Chạy full stack bằng Docker

```powershell
cd D:\Git_repo\mini-discord
docker compose -f infra/docker-compose.yml up -d --build
```

Mở ứng dụng tại:

```text
http://localhost:8088
```

Các URL hữu ích:

```text
Frontend:  http://localhost:5173
Backend:   http://localhost:8080/api/v1
Swagger:   http://localhost:8080/swagger-ui/index.html
MinIO:     http://localhost:9001
```

### 2. Chạy backend local

```powershell
cd D:\Git_repo\mini-discord\backend
.\gradlew.bat bootRun
```

Backend sẽ chạy ở:

```text
http://localhost:8080
```

### 3. Chạy frontend local

```powershell
cd D:\Git_repo\mini-discord\frontend
npm install
npm run dev
```

Frontend sẽ chạy ở:

```text
http://localhost:5173
```

### 4. Chạy test

Backend:

```powershell
cd D:\Git_repo\mini-discord\backend
.\gradlew.bat test
```

Frontend:

```powershell
cd D:\Git_repo\mini-discord\frontend
npm test -- --run
```

