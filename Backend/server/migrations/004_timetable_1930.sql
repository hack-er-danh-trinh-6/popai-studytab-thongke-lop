-- Gửi thời khóa biểu vào 19:30 ngày trước ngày học.
UPDATE pop_timetable_settings SET send_time = '19:30', updated_at = NOW() WHERE id = 1;
