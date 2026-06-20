# Mini Discord

Mini Discord là ứng dụng chat dạng Discord thu nhỏ, gồm backend Spring Boot, frontend React/Vite và hạ tầng Docker cho MongoDB replica set, Redis, MinIO, Nginx.

## Chức năng chính

- Đăng ký, đăng nhập, refresh token, logout.
- Tạo server, tạo channel mặc định `general`, quản lý member theo role `OWNER` và `MEMBER`.
- Gửi message realtime qua WebSocket/STOMP.
- Upload file qua MinIO; quyền sở hữu upload được lưu bằng Redis key có TTL.
- Invite bằng code và direct invite. Direct invite lưu rõ `inviterId` là OWNER gửi lời mời, `inviteeId` là user nhận lời mời.
- Notification cho direct invite.
- MongoDB indexes, gồm text index trên `messages.content` để search message.

## Cấu trúc thư mục

```text
mini-discord/
  backend/   Spring Boot 4, Java 21, MongoDB, Redis, MinIO, STOMP WebSocket
  frontend/  React 19, TypeScript, Vite, TanStack Query, Zustand
  infra/     Docker Compose: MongoDB replica set, Redis, MinIO, backend, frontend, Nginx
```

## Yêu cầu môi trường

- Docker Desktop.
- Java 21+ nếu chạy backend local bằng `bootRun`.
- Node.js/npm nếu chạy frontend local không qua Docker.

Nếu máy không có Gradle, dùng Gradle wrapper trong repo:

```powershell
cd backend
.\gradlew.bat bootRun
```

Nếu máy không có npm, dùng các script Docker trong `frontend/`.

## Chạy nhanh bằng Docker

Đây là cách khuyến nghị để chạy full stack.

```powershell
cd D:\Git_repo\mini-discord
docker compose -f infra/docker-compose.yml up -d --build
```

Mở ứng dụng:

```text
http://localhost:8088
```

Các URL khác:

```text
Frontend container: http://localhost:5173
Backend API:        http://localhost:8080/api/v1
Swagger UI:         http://localhost:8080/swagger-ui/index.html
MinIO console:      http://localhost:9001
```

Tài khoản MinIO mặc định:

```text
username: minioadmin
password: minioadmin
```

Xem trạng thái container:

```powershell
docker ps
```

Xem log backend:

```powershell
docker logs -f mini-discord-backend
```

Dừng stack:

```powershell
docker compose -f infra/docker-compose.yml down
```

Xóa cả dữ liệu MongoDB/Redis/MinIO:

```powershell
docker compose -f infra/docker-compose.yml down -v
```

## Chạy backend local

Dùng cách này khi muốn debug backend bằng IDE hoặc `bootRun`.

1. Chạy hạ tầng phụ thuộc:

```powershell
cd D:\Git_repo\mini-discord
docker compose -f infra/docker-compose.yml up -d mongo redis minio
docker compose -f infra/docker-compose.yml run --rm mongo-rs-init
```

2. Nếu backend Docker đang chạy, dừng để trả port `8080`:

```powershell
docker stop mini-discord-backend
```

3. Chạy backend local:

```powershell
cd D:\Git_repo\mini-discord\backend
.\gradlew.bat bootRun
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Backend local dùng MongoDB URI mặc định:

```text
mongodb://localhost:27017/mini_discord?directConnection=true&replicaSet=rs0
```

URI này cần thiết trên Windows vì hostname Docker `mongo` chỉ resolve bên trong network Docker.

## Chạy frontend local

Nếu đã cài Node.js/npm:

```powershell
cd D:\Git_repo\mini-discord\frontend
npm install
npm run dev
```

Mở:

```text
http://localhost:5173
```

Vite dev server proxy `/api` và `/ws` sang backend local `http://localhost:8080`.

Nếu máy không có npm, dùng Docker wrapper:

```powershell
cd D:\Git_repo\mini-discord\frontend
.\npm-docker.ps1 ci
.\npm-docker.ps1 run dev -- --host 0.0.0.0
```

Nếu muốn chạy frontend build sẵn trong Docker Compose:

```powershell
cd D:\Git_repo\mini-discord\frontend
.\dev-docker.ps1
```

## Chạy test

Backend:

```powershell
cd D:\Git_repo\mini-discord\backend
.\gradlew.bat test
```

Frontend nếu có npm:

```powershell
cd D:\Git_repo\mini-discord\frontend
npm test -- --run
```

Frontend nếu không có npm:

```powershell
cd D:\Git_repo\mini-discord\frontend
.\npm-docker.ps1 ci
.\npm-docker.ps1 test -- --run
```

## Test đã có

Backend:

- `AuthServiceTest`: đăng ký normalize email/username, chặn duplicate email, login sinh access token và refresh token.
- `TokenHasherTest`: hash SHA-256 và refresh token URL-safe.
- `JwtServiceTest`: tạo/verify JWT, chặn secret sai, token hết hạn, secret quá ngắn.
- `InviteServiceTest`: direct invite lưu đúng `inviterId`, `inviteeId`, chặn invite PENDING trùng.

Frontend:

- `authStore.test.ts`: lưu và xóa trạng thái đăng nhập.
- `client.test.ts`: `unwrap`, `refreshToken`, retry request khi access token hết hạn.
- `AuthView.test.tsx`: bấm Register, gọi API register, rồi tự login và lưu access token.

## Biến môi trường quan trọng

Backend đọc các biến này từ môi trường, Docker Compose đã cấu hình sẵn giá trị dev:

```text
MONGODB_URI
REDIS_HOST
REDIS_PORT
MINIO_ENDPOINT
MINIO_PUBLIC_BASE_URL
JWT_SECRET
REFRESH_COOKIE_SECURE
CORS_ALLOWED_ORIGINS
UPLOAD_TTL_MINUTES
```

Ví dụ dev local nằm trong `.env.example`.

## Troubleshooting

### `gradle` không được nhận diện

Không cần cài Gradle global. Dùng wrapper:

```powershell
cd backend
.\gradlew.bat bootRun
```

### `npm` không được nhận diện

Cài Node.js hoặc dùng Docker wrapper:

```powershell
cd frontend
.\npm-docker.ps1 ci
.\npm-docker.ps1 run dev -- --host 0.0.0.0
```

### Port `8080` đang bận

Kiểm tra process:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
```

Nếu backend Docker đang giữ port:

```powershell
docker stop mini-discord-backend
```

Nếu là process Java local cũ:

```powershell
Stop-Process -Id <OwningProcess> -Force
```

### Không đăng ký được trên frontend

Kiểm tra backend đang chạy:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Nếu chạy full Docker, mở:

```text
http://localhost:8088
```

Nếu mở `http://localhost:5173`, frontend container hiện đã proxy `/api` sang backend Docker. Nếu vẫn lỗi, rebuild:

```powershell
docker compose -f infra/docker-compose.yml up -d --build frontend nginx
```
