# POP AI development source bundle

This ZIP preserves the complete Android and Backend source trees from the supplied project archive.

- Android source: `Android/` (build with `./gradlew :app:assembleDebug`).
- Backend source: `Backend/` (Node.js 22, pnpm 10.18; run `pnpm install` then `pnpm dev`).
- For development as requested, the PostgreSQL Aiven URI is embedded directly in `Backend/server/db.js`; no `.env` file is included.

**Security:** the source contains the database credential in plain text. Share privately and do not commit this bundle publicly; rotate the database credential if it is exposed. AI credentials are not included; AI uses `AI_API_KEY` (required), `AI_API_URL` (default `https://trucmod.fun/api/v1`) and `AI_MODEL` (default `gemini-3-7-flash`) set as server environment variables.

## Quản lý lớp học

- Backend: migration `002_classroom.sql`, `server/routes/teacher.js` (API giáo viên), `server/routes/classroom.js` (API học sinh), `server/classroomDomain.js` (hàm thuần + test), trang web giáo viên ở `Backend/public/admin/` (truy cập `/admin`).
- Cần đặt `TEACHER_ADMIN_PASSWORD` trên máy chủ để giáo viên đăng ký/đăng nhập (xem `Backend/README.md`).
- Android: `ui/classroom/` (chọn trường/lớp, trạng thái, hộp thư), `data/repository/ClassroomRepository.kt`, `core/notification/` (thông báo điện thoại qua WorkManager + kiểm tra 30 giây khi app mở). Sau khi đăng ký, học sinh được hỏi chọn lớp một lần.
- Tài khoản giáo viên không đăng nhập được trong app (chỉ trên web).
- Chưa biên dịch Android trong môi trường tạo bundle này: hãy chạy `./gradlew :app:assembleDebug` để kiểm tra.

## Khắc phục: "Máy chủ chưa hỗ trợ tính năng lớp học"

**Nguyên nhân.** App nhận `404` kèm thông báo mặc định `"Không tìm thấy API."` khi gọi `GET /api/mobile/v1/schools`
và `GET /api/mobile/v1/class/membership`. Mã nguồn trong `Backend/` đã có các route này (`server/routes/classroom.js`),
nên máy chủ đang chạy tại `BASE_URL` (`Android/app/build.gradle.kts`) là bản **cũ, chưa được triển khai lại** sau khi thêm lớp học.
Sửa code Android không làm hết lỗi này; phải triển khai lại `Backend/`.

**Kiểm tra nhanh máy chủ** (mở trong trình duyệt, không cần đăng nhập): `<BASE_URL>api/mobile/v1/schools`

- `{"message":"Không tìm thấy API."}` (404) → máy chủ vẫn là bản cũ → triển khai lại `Backend/`.
- `{"message":"Vui lòng đăng nhập để tiếp tục."}` (401) → đã là bản mới, route lớp học hoạt động.
- `{"message":"Máy chủ đang khởi động. Vui lòng thử lại."}` (503) → migration chưa chạy xong hoặc lỗi kết nối DB; xem log máy chủ.

**Triển khai lại.** Chạy lại toàn bộ thư mục `Backend/` (Node 22, `pnpm install --frozen-lockfile`, `pnpm start`; có `Dockerfile`).
Khi khởi động, `002_classroom.sql` tự tạo các bảng lớp học. Đặt biến môi trường `TEACHER_ADMIN_PASSWORD` để giáo viên dùng được `/admin`.

**Sau khi triển khai.** Giáo viên mở `/admin`, nhập mật khẩu admin, đăng ký tài khoản và khai báo trường + lớp.
Học sinh mở Lớp học trong app sẽ thấy trường đó (danh sách trống khi chưa có giáo viên nào khai báo là hành vi bình thường).

**Thay đổi ở app (`ui/classroom/`).** Lỗi tải danh sách trường/lớp không còn bị hiển thị như "chưa có trường nào";
khi chưa tải được trạng thái lớp, app ẩn bộ chọn trường và hiện nút **Thử lại**.


## Chế độ tập trung (khoá khi đồng hồ chạy)

Khi bấm **Bắt đầu/Tiếp tục** ở tab Tập trung (`FocusSessionState.RUNNING`):

- Khoá chuyển màn hình: thanh điều hướng dưới mờ đi, bấm tab/nút POP-AI khác chỉ hiện Toast nhắc; nút Back (kể cả Back để thoát app) bị chặn (`AppNavHost`, `BackHandler` đặt sau `NavHost`). Vẫn dùng được các nút trên màn đồng hồ (Tạm dừng, Hoàn thành, ghi nhận gián đoạn).
- Giữ màn hình sáng (`keepScreenOn`) để màn hình tự tắt không bị tính là rời app.
- Lớp phủ cảnh báo `ui/focus/FocusLockOverlay.kt` hiện khi (a) app xuống nền (`ON_STOP` của Activity) rồi quay lại, hoặc (b) đang ở route khác tab Tập trung (ví dụ chạm thông báo của giáo viên mở màn Lớp học). Lớp phủ nuốt mọi chạm; nút **Quay lại đồng hồ tập trung** đưa về tab Tập trung.
- Tạm dừng/Hoàn thành/đăng xuất thì mở khoá và tắt lớp phủ. Đăng xuất gọi `FocusRepository.reset()`.
- `FocusViewModel` đếm theo mốc thời gian (`SystemClock.elapsedRealtime`) thay vì trừ 1 giây mỗi lần `delay`, nên đồng hồ vẫn đúng sau khi app ở nền.
- `AndroidManifest.xml`: thêm `android:configChanges` (xoay màn hình, kích thước...) để Activity không bị tạo lại giữa phiên (repository giữ trong bộ nhớ theo Activity).
- Giới hạn: lớp phủ chỉ hiện trong app. Hiện lớp phủ đè lên app khác cần quyền "Hiển thị trên ứng dụng khác" (`SYSTEM_ALERT_WINDOW`) — chưa làm.
- Chưa biên dịch trong môi trường tạo thay đổi này (không có Android SDK): hãy chạy `./gradlew :app:assembleDebug`.

## Thời khóa biểu + thông báo hằng ngày

- Trang admin hệ thống riêng: `Backend/public/sysadmin/` (truy cập `/sysadmin`, mật khẩu `SYSTEM_ADMIN_PASSWORD`), API `Backend/server/routes/sysadmin.js`, logic thuần + test `timetableDomain.js`, bộ lập lịch `timetableService.js`, migration `003_timetable.sql`. Chi tiết trong `Backend/README.md`.
- Chưa chạy end-to-end với `express`/`pg` thật trong môi trường tạo thay đổi (không cài được npm): hãy chạy `pnpm install && pnpm check && pnpm test` rồi thử `/sysadmin` sau khi triển khai.

## Tab POP-AI dạng hội thoại + lưu lịch sử chat

- Giao diện: `Android/.../ui/ai/POPAIChatScreen.kt` (bong bóng chat, ngăn cách theo ngày, hiệu ứng đang trả lời, ô nhập cố định phía dưới, nút thử lại khi lỗi, nút thùng rác xoá lịch sử có xác nhận). `AIViewModel` giữ danh sách tin nhắn.
- Lưu lịch sử: `data/local/ChatHistoryStore.kt` (`EncryptedChatHistoryStore`, mã hoá bằng EncryptedSharedPreferences, mỗi tài khoản một khoá; đăng xuất không xoá). Tối đa 150 tin / 120.000 ký tự (`ChatHistoryRules`). Nếu bộ nhớ mã hoá không khởi tạo được, app vẫn chạy nhưng không lưu lịch sử.
- Ngữ cảnh: mỗi câu hỏi gửi kèm tối đa 6 lượt gần nhất (`history`, mỗi lượt cắt 600 ký tự) để POP-AI hiểu câu hỏi nối tiếp. Server cũ bỏ qua trường này nên app/server nâng cấp độc lập được. Server coi `history` là dữ liệu (khối `<hoi_thoai_truoc>`), không phải chỉ dẫn.
- Logic thuần có test: `ChatHistoryRulesTest`, `ChatHistoryCodecTest`, `ChatTimeFormatTest` (Android) và `parseHistory`/`buildMessages` trong `Backend/server/ai.test.js`.
- Chưa build/chạy trên Android trong môi trường tạo thay đổi (không có Gradle/Kotlin): hãy chạy `./gradlew testDebugUnitTest assembleDebug` và thử trên máy thật (bàn phím, xoay màn hình, mở lại app).

