# POP AI API

Backend REST cho ứng dụng Android POP AI. API giữ các đường dẫn `api/mobile/v1` của ứng dụng, lưu dữ liệu vào PostgreSQL Aiven và gọi API AI tương thích OpenAI (`https://literouter.fun/api/v1`) cho POP-AI. `DATABASE_URL` chỉ được đọc từ secret runtime phía server; không ghi secret vào APK hoặc log.

## Chạy trong Manus Preview

Yêu cầu Node.js 22+, pnpm 10.18.0 và secret `DATABASE_URL`. Chạy `pnpm install`, sau đó `pnpm dev`. Khi server khởi động, migration additive `server/migrations/001_pop_mobile_api.sql` tạo các bảng mới có tiền tố `pop_` trong schema `public`; migration có phiên bản và không xoá hoặc ghi đè bảng khác. API lắng nghe cổng `PORT` (mặc định 3000).

Aiven xác nhận `sslmode=require` bật mã hóa TLS nhưng không xác minh chứng chỉ server. Backend giữ chế độ đó và đặt TLS tối thiểu 1.2, đúng với URI đã cung cấp; muốn xác thực CA/hostname mạnh hơn, cần CA bundle tải từ trang Overview của dịch vụ Aiven.

`GET /healthz` kiểm tra kết nối PostgreSQL. Các endpoint người dùng nằm dưới `/api/mobile/v1` và cần bearer token, ngoại trừ đăng ký, đăng nhập và health check. `GET /plan` trả `404` khi tài khoản chưa lưu kế hoạch; không tạo kế hoạch mẫu.

## API

- `POST /api/mobile/v1/auth/register`, `POST /auth/login`, `POST /auth/refresh`, `GET /auth/me`, `POST /auth/logout`
- `POST /assessment/submit`, `GET /assessment/history`
- `GET /plan`, `POST /plan`
- `POST /focus/session`
- `POST /journal`, `GET /journal/today`
- `GET /progress/summary?days=7`
- `POST /ai/advice`
- `POST /ai/homework`

Các đường dẫn trên được ghép với prefix `/api/mobile/v1`. Tư vấn AI gọi `POST {AI_API_URL}/chat/completions` với `AI_API_KEY` (bắt buộc, đặt trong biến môi trường server). `AI_API_URL` mặc định `https://literouter.fun/api/v1`, `AI_MODEL` mặc định `gemini-3.8-flash`; không cần API key riêng trong app. Phần tổng hợp tiến độ nhận `endDate` (`dd/MM/yyyy`) và `timeZone` dạng IANA từ thiết bị để thống nhất ngày lịch.

## POP-AI (`POST /ai/advice`)

Yêu cầu: `{ "prompt": string (≤ 2000 ký tự), "includeReflection": boolean, "reflectionDate": "dd/MM/yyyy", "timeZone": "IANA" }`. Phản hồi: `{ "adviceText": string, "actionableSteps": string[], "reflectionUsed": boolean }`.

- **Dữ liệu thật làm ngữ cảnh.** Server tự gắn số liệu tổng hợp 7 ngày gần nhất theo `timeZone` của thiết bị (phiên tập trung, số lần bị ngắt, video ngắn trung bình, cấu hình kế hoạch, điểm tự đánh giá gần nhất) để POP-AI không phải đoán xu hướng. Chỉ gửi số liệu và cấu hình; văn bản tự do (nhiệm vụ trong kế hoạch, ghi chú nhật ký) không được gửi trừ khi người dùng bật `includeReflection`. Nếu chưa có dữ liệu, POP-AI được hướng dẫn nói rõ thay vì bịa số liệu.
- **Nhật ký chỉ khi đồng ý.** `reflectionUsed` cho biết nhật ký của `reflectionDate` có thực sự được dùng (false nếu hôm đó chưa có nhật ký).
- **Định dạng đầu ra.** Server chấp nhận JSON thuần, JSON bọc trong khối mã hoặc có lời dẫn; chuẩn hoá các bước (tối đa 5, mỗi bước ≤ 200 ký tự để lưu được vào kế hoạch). JSON sai cấu trúc trả `502` thay vì hiển thị JSON thô.
- **An toàn.** Dữ liệu người dùng được đặt trong các khối có thẻ riêng và bị gỡ ký tự `<` `>` để chống giả mạo chỉ dẫn; system prompt hướng người dùng tới hỗ trợ khẩn cấp khi có dấu hiệu khủng hoảng và không đưa chẩn đoán y khoa.
- **Giới hạn.** 10 yêu cầu/15 phút theo tài khoản. Lỗi dịch vụ AI trả `503`/`504`/`502` kèm thông báo tiếng Việt; message gốc của nhà cung cấp không được chuyển tiếp.

Tài liệu TLS: [Aiven TLS/SSL certificates](https://aiven.io/docs/platform/concepts/tls-ssl-certificates).

## Quản lý lớp học (học sinh ↔ giáo viên)

Migration `002_classroom.sql` thêm các bảng `pop_schools`, `pop_classes`, `pop_class_members`, `pop_notifications`, `pop_notification_recipients`.

**Biến môi trường mới**

- `TEACHER_ADMIN_PASSWORD` (bắt buộc để dùng trang giáo viên): mật khẩu giáo viên nhập ở `/admin` để được đăng nhập/đăng ký. Chưa đặt thì API trả `503`.
- `TEACHER_GATE_SECRET` (tuỳ chọn): khoá ký phiên nhập mật khẩu admin; mặc định suy ra từ `TEACHER_ADMIN_PASSWORD`.

**Luồng**

1. Học sinh đăng ký trong app → app hỏi chọn trường/lớp → `POST /api/mobile/v1/class/join-request`.
2. Giáo viên mở `/admin` → nhập mật khẩu admin → đăng ký tài khoản giáo viên (khai báo trường và lớp) hoặc đăng nhập → dashboard: duyệt học sinh, xem thói quen tập trung, gửi thông báo.
3. Học sinh nhận thông báo như thông báo điện thoại (app kiểm tra 30 giây/lần khi mở và 15 phút/lần khi đóng).

**API học sinh** (`/api/mobile/v1`, cần token học sinh): `GET /schools?q=`, `GET /schools/:id/classes`, `GET|DELETE /class/membership`, `GET /class/members` (họ tên thầy cô và bạn cùng lớp, chỉ khi đã được duyệt), `GET /class/timetable` (thời khóa biểu của lớp đã được duyệt), `POST /class/join-request`, `GET /notifications`, `POST /notifications/deliver`, `POST /notifications/read`.

**API giáo viên** (`/api/teacher/v1`): `POST /gate`; `POST /auth/register|login` (cần header `X-Gate-Token`); `GET /auth/me`, `POST /auth/logout`; `GET|POST /classes`; `GET /classes/:id/requests`; `POST /classes/:id/requests/approve-all`, `/requests/:userId/approve|reject`; `GET /classes/:id/students?days=&timeZone=`; `GET /classes/:id/students/:userId/focus`; `DELETE /classes/:id/students/:userId`; `GET|POST /classes/:id/notifications`.

**Quyền riêng tư:** giáo viên chỉ thấy phiên tập trung (phút, số phiên, lần ngắt, tự đánh giá, tên nhiệm vụ) từ lúc học sinh được duyệt; không thấy nhật ký, bài đánh giá hay hội thoại POP-AI.

## Thời khóa biểu theo lớp + thông báo hằng ngày (trang admin hệ thống)

Trang admin **riêng** (không phải trang giáo viên `/admin`): mở `/sysadmin`, đăng nhập bằng mật khẩu `SYSTEM_ADMIN_PASSWORD`.

**Biến môi trường mới**

- `SYSTEM_ADMIN_PASSWORD` (bắt buộc để dùng `/sysadmin`): chưa đặt thì API `/api/admin/v1` trả `503`. Khác với `TEACHER_ADMIN_PASSWORD`; token của hai trang không dùng lẫn được.
- `SYSTEM_ADMIN_SECRET` (tuỳ chọn): khoá ký phiên admin (sống 8 giờ); mặc định suy ra từ mật khẩu.
- `TIMETABLE_TIME_ZONE` (tuỳ chọn, mặc định `Asia/Ho_Chi_Minh`): múi giờ để xác định "hôm nay" và giờ gửi.
- `TIMETABLE_SCHEDULER=off` (tuỳ chọn): tắt bộ lập lịch trên máy chủ này.

**Cách dùng:** admin chọn lớp (lớp do giáo viên khai báo) -> với mỗi ngày Thứ 2-6 tích chọn buổi sáng và/hoặc buổi chiều (ngày nào cũng có thể chỉ 1 buổi hoặc không học) -> nhập tối đa 5 tiết mỗi buổi (tiết trống bị bỏ qua) -> Lưu. Mỗi lớp có thời khóa biểu riêng; có nút sao chép từ lớp khác, xóa toàn bộ và "Gửi thông báo ngay" để thử.

**Gửi thông báo:** migration `003_timetable.sql` thêm `pop_timetable_entries`, `pop_timetable_settings`, `pop_timetable_dispatches`. Bộ lập lịch (`server/timetableService.js`) kiểm tra mỗi phút; từ giờ gửi (mặc định 06:00, chỉnh trong trang admin) vào Thứ 2-6, mỗi lớp có tiết học hôm đó sẽ nhận một thông báo (loại `SYSTEM`) gửi tới học sinh đã được duyệt, ví dụ: `Sáng: Tiết 1: Toán · Tiết 2: Văn` / `Chiều: Tiết 1: Anh`. Mỗi lớp tối đa một lần mỗi ngày (khoá chính `pop_timetable_dispatches`, an toàn khi khởi động lại hoặc chạy nhiều máy chủ). Nếu máy chủ trễ giờ gửi thì gửi bù trong 4 giờ sau giờ gửi. Lớp không có tiết hôm đó hoặc chưa có học sinh được duyệt thì không gửi.

Thông báo đi qua cơ chế hiện có của app (WorkManager 15 phút/lần khi đóng app, 30 giây/lần khi mở app) nên **không cần sửa Android**; muốn đẩy tức thì cần FCM (xem `TODO.md`).

**API admin** (`/api/admin/v1`, header `Authorization: Bearer <token>` từ `POST /login`): `GET /classes?q=`, `GET|PUT|DELETE /classes/:id/timetable`, `POST /classes/:id/timetable/send-now`, `GET|PUT /settings`.


## Thống kê dữ liệu người dùng (trang `/sysadmin` → tab "Thống kê")

API dành cho admin hệ thống (cùng token `Bearer` với các API `/api/admin/v1`), chỉ trả số liệu tổng hợp; không trả nội dung tự do (nhật ký phản tư, tên nhiệm vụ, câu hỏi gửi POP-AI).

- `GET /api/admin/v1/stats/overview?days=7|30|90`: KPI, chuỗi theo ngày (đăng ký, người dùng hoạt động, phút tập trung, nhật ký), phân bố điểm đánh giá, kế hoạch, lý do gián đoạn, yếu tố xao nhãng, thống kê theo lớp. Ngày tính theo múi giờ `TIMETABLE_TIME_ZONE`.
- `GET /api/admin/v1/stats/users?q=&role=&sort=&dir=&page=&pageSize=`: bảng người dùng có tìm kiếm, lọc vai trò, sắp xếp (cột nằm trong whitelist) và phân trang; `all=1` trả tối đa 5000 dòng để xuất CSV.
- `GET /api/admin/v1/stats/users/:userId`: chi tiết một người dùng (lịch sử đánh giá, 30 ngày tập trung/nhật ký, kế hoạch).

Lưu ý: câu hỏi/câu trả lời POP-AI hiện không được lưu trong cơ sở dữ liệu nên chưa thống kê được lượt dùng POP-AI.

## Ngữ cảnh hội thoại cho POP-AI

`POST /api/mobile/v1/ai/advice` nhận thêm trường tuỳ chọn `history`: mảng `{ "role": "user" | "assistant", "content": string }`. Server chỉ nhận hai role đó (bỏ `system` và mọi giá trị khác), gỡ ký tự `<` `>`, cắt mỗi lượt còn 600 ký tự và giữ 6 lượt gần nhất, rồi đưa vào khối dữ liệu `<hoi_thoai_truoc>` của tin nhắn người dùng (không tạo lượt assistant giả). Không gửi `history` thì hành vi như cũ.

## AI Giáo sư bài tập (`POST /ai/homework`)
Cùng định dạng request/response với `/ai/advice` (`prompt`, `history`) nhưng dùng prompt gia sư (`HOMEWORK_SYSTEM_PROMPT`): giải thích từng bước, văn bản thuần, `actionableSteps` luôn rỗng. Không đưa nhật ký hay số liệu hoạt động của người dùng vào prompt. Giới hạn riêng 20 lượt/15 phút/tài khoản.

## Nhạc tập trung (`GET /media/focus-music.mp3`)
File nhạc nằm ở `media/focus-music.mp3`, phục vụ có hỗ trợ `Range` để app Android stream thay vì đóng gói trong APK.
