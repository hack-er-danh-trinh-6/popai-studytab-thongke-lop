# Tracker thời gian dùng TikTok / YouTube

- Màn hình: Home → thẻ "Theo dõi TikTok, YouTube" (route `tracker`).
- Người dùng bật theo dõi, cấp quyền **Truy cập dữ liệu sử dụng** (Settings) và tự nhập hạn mức phút/ngày cho từng app.
- `core/tracker/UsageStatsReader` đọc thời gian foreground từ 0h bằng `UsageStatsManager.queryEvents`.
- `UsageTrackerService` (foreground service, kiểm tra mỗi 20 giây) + `UsageTrackerWorker` (dự phòng 15 phút) + `BootReceiver`.
- Thông báo: mốc 80% ("sắp hết"), mốc vượt hạn, và nhắc lại mỗi 15 phút nếu vẫn đang dùng. Chạm thông báo mở màn Theo dõi.
- Package được theo dõi: `TrackedApp.kt` (TikTok: `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`; YouTube: `com.google.android.youtube`).
- Test quy tắc: `UsageRulesTest`.

## Build APK bằng GitHub Actions (không cần máy tính)
1. Tạo repo GitHub, upload toàn bộ nội dung thư mục dự án (có `Android/`, `Backend/`, `.github/`).
2. Vào tab **Actions** → **Build debug APK** → **Run workflow**.
3. Khi chạy xong, mở lần chạy đó, tải artifact **popai-debug-apk** (zip chứa `app-debug.apk`), giải nén rồi cài lên điện thoại.

## Quyền chạy nền (màn Theo dõi → "Giữ theo dõi chạy nền")
- **Chạy nền không giới hạn**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (hộp thoại hệ thống).
- **Tự khởi động**: mở màn riêng của Xiaomi/Oppo/Vivo/Huawei/Samsung/Asus, máy khác mở trang thông tin ứng dụng.
- **Quản trị viên thiết bị**: `PopDeviceAdminReceiver` (không đặt chính sách), chỉ để chống gỡ nhầm.
- Hạn mức có gợi ý nhanh 1 giờ, 1 giờ 30, 2, 3, 4 giờ; tối đa 24 giờ.

## Nhạc tập trung (stream từ backend)
- Khi đồng hồ tập trung chạy, `FocusSoundService` phát `Backend/media/focus-music.mp3` (Zen Melody, ~1 giờ, lặp lại) ở âm lượng 30% (`FocusMusic.VOLUME`, đặt trên MediaPlayer nên không đổi âm lượng hệ thống).
- File nhạc nằm ở backend, phục vụ qua `GET /media/focus-music.mp3` (hỗ trợ Range) nên APK không phải chứa ~28 MB. URL = `BASE_URL` + `media/focus-music.mp3`.
- Cần mạng; nếu không tải được sẽ báo một lần và đồng hồ vẫn chạy bình thường. Tự dừng khi tạm dừng/hoàn thành, rút tai nghe, hoặc app khác chiếm âm thanh (tạm dừng khi bị gián đoạn ngắn). Có thể tắt/bật trên thẻ "Nhạc tập trung". Test: `FocusSoundTest`.

## Các tab chính
- **Hôm nay · Kế hoạch · Tập trung · Học tập · Tiến bộ** (+ nút POP-AI).
- **Kế hoạch** gộp Kế hoạch và Nhật ký: hai nút lớn ở đầu trang để chuyển qua lại (`HabitsScreen`). Chỉ giữ các ô thiết yếu ở ngoài; các ô ít dùng nằm trong phần thu gọn. Lối tắt "Ghi nhật ký" ở màn Hôm nay mở sẵn mục Nhật ký.
- **Học tập** (`StudyScreen`): lớp đang tham gia (vào/rời lớp và thông báo mở màn Lớp học), thời khóa biểu theo ngày, thẻ AI Giáo sư bài tập và danh sách thành viên lớp. Dữ liệu từ `GET /class/members`, `GET /class/timetable`; tài khoản không phải học sinh chỉ thấy thẻ AI Giáo sư.
- **AI Giáo sư bài tập**: màn chat dùng lại `POPAIChatScreen` với `AiMode.HOMEWORK`, gọi `POST /ai/homework`, lịch sử lưu riêng.
- Test: `TimetableRulesTest`.
