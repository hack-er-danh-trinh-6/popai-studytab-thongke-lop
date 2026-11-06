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

**Gửi thông báo:** migration `003_timetable.sql` thêm `pop_timetable_entries`, `pop_timetable_settings`, `pop_timetable_dispatches`. Bộ lập lịch (`server/timetableService.js`) kiểm tra mỗi phút; mặc định lúc 19:30 ngày hôm trước (chỉnh trong trang admin), hệ thống gửi thời khóa biểu của ngày học kế tiếp tới học sinh đã được duyệt, ví dụ: `Sáng: Tiết 1: Toán · Tiết 2: Văn` / `Chiều: Tiết 1: Anh`. Chủ nhật 19:30 sẽ gửi lịch Thứ 2. Mỗi lớp tối đa một lần cho mỗi ngày học (khoá chính `pop_timetable_dispatches`, an toàn khi khởi động lại hoặc chạy nhiều máy chủ). Nếu máy chủ trễ giờ gửi thì gửi bù trong 4 giờ sau giờ gửi. Lớp không có tiết ngày kế tiếp hoặc chưa có học sinh được duyệt thì không gửi.

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

## Adminbot — dữ liệu học tập thử nghiệm

Trang quản trị bot tại `/adminbot` dùng để xem học sinh bot trong lớp bot và sinh dữ liệu mẫu vào các bảng hoạt động của app. Trang cần `ADMINBOT_PASSWORD`; nếu biến này chưa được đặt, có thể dùng lại `TEACHER_ADMIN_PASSWORD`. Có thể đặt riêng `ADMINBOT_SECRET`; nếu bỏ trống, phiên ký dùng lại `TEACHER_GATE_SECRET` hoặc khoá suy ra từ mật khẩu. Nên đặt mật khẩu/secret riêng trong môi trường production.

API tại `/api/adminbot/v1`:

- `POST /gate` — xác thực mật khẩu adminbot, trả token hết hạn.
- `GET /overview` — trả giáo viên/lớp bot, danh sách học sinh `is_synthetic = true` đã duyệt, tổng phiên tập trung, nhật ký, đánh giá, kế hoạch.
- `POST /seed` — sinh phiên tập trung, nhật ký, đánh giá tự theo dõi và kế hoạch cá nhân; tuỳ chọn số ngày (1–90), phiên/ngày (0–6), tỉ lệ ngày hoạt động, số bài đánh giá (0–5), bật/tắt nhật ký/kế hoạch, xoá dữ liệu bot cũ trước khi tạo, và lùi ngày duyệt vào lớp để thống kê trang `/admin` tính được dữ liệu.
- `POST /reset` — xoá dữ liệu hoạt động cá nhân của tài khoản bot trong lớp bot, không xoá tài khoản/lớp.
- `POST /state` — cập nhật cờ is_running trong `pop_adminbot_state`.

Các route yêu cầu `X-Gate-Token`. Lệnh sinh/xoá chỉ giới hạn vào học sinh `is_synthetic = true` trong lớp bot được lưu tại `pop_adminbot_state`; không đụng dữ liệu học sinh thật. Phiên tập trung có client session ID ổn định theo học sinh/ngày, các lần chạy có thể dùng tuỳ chọn xoá trước khi sinh lại. Trang tạo dữ liệu mặc định 14 ngày, tối đa 2 phiên/ngày, 75% ngày có hoạt động, 1 bài đánh giá/học sinh và có nhật ký/kế hoạch. Dữ liệu tự đánh giá chỉ để kiểm thử, không phải chẩn đoán y khoa.

**Triển khai:** đưa toàn bộ thư mục `Backend/` lên môi trường backend đang phục vụ POP AI, chạy `pnpm install --frozen-lockfile`, đặt các biến môi trường hiện có cùng `ADMINBOT_PASSWORD` (hoặc `TEACHER_ADMIN_PASSWORD`), sau đó khởi động `pnpm start`. Migration `004_adminbot.sql` phải hiện diện và `pop_adminbot_state` cần trỏ tới giáo viên/lớp bot. Mở `<BASE_URL>/adminbot` để thao tác. Kiểm tra bằng `pnpm run check` và `pnpm test`.

## Ngữ cảnh hội thoại cho POP-AI

## AI Giáo sư bài tập (`POST /ai/homework`)
Cùng định dạng request/response với `/ai/advice` (`prompt`, `history`) nhưng dùng prompt gia sư (`HOMEWORK_SYSTEM_PROMPT`): giải thích từng bước, văn bản thuần, `actionableSteps` luôn rỗng. Không đưa nhật ký hay số liệu hoạt động của người dùng vào prompt. Giới hạn riêng 20 lượt/15 phút/tài khoản.

## Nhạc tập trung (`GET /media/focus-music.mp3`)
File nhạc nằm ở `media/focus-music.mp3`, phục vụ có hỗ trợ `Range` để app Android stream thay vì đóng gói trong APK.

## Chống spin down trên Render (free tier)

`server/keepAlive.js` tự gọi `GET /healthz` mỗi 10 phút khi có `RENDER_EXTERNAL_URL` (Render tự đặt). Biến tùy chọn: `KEEP_ALIVE=off` để tắt, `KEEP_ALIVE_URL` để ghi đè URL, `KEEP_ALIVE_INTERVAL_MS` (tối thiểu 60000). Self-ping không đánh thức được server đã ngủ, nên nên đặt thêm UptimeRobot/cron-job.org ping `/healthz` mỗi 5-10 phút.

## Thông báo cập nhật app (`/updateapp`)

Migration `007_app_update_notifications.sql` cho phép thông báo không thuộc lớp nào (`class_id` null) và thêm cột `link_url`.

- Trang web: `/updateapp`. Đăng nhập bằng `SYSTEM_ADMIN_PASSWORD` (dùng chung phiên với `/sysadmin`). Khi có bản mới, admin (tuỳ chọn nhập số phiên bản) bấm **Thông báo có bản cập nhật mới** → tạo một thông báo gửi tới mọi tài khoản học sinh, kèm lịch sử và số người đã nhận/đã đọc.
- API (`/api/updateapp/v1`, Bearer token admin): `POST /login`, `GET /status`, `POST /send` (tối đa 5 lần/giờ).
- Liên kết mở khi nhấn thông báo mặc định `https://apphub-pop.onrender.com`; đổi bằng biến môi trường `APP_HUB_URL` (chỉ nhận `https://`). Liên kết do server quyết định, không nhận từ trình duyệt.
- App Android: `GET /notifications` và `/notifications/deliver` trả thêm `linkUrl`; nhấn thông báo điện thoại (hoặc nút "Mở App Hub để cập nhật" trong hộp thư) sẽ mở trình duyệt tới liên kết đó.
