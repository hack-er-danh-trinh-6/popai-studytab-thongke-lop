-- Thời khóa biểu theo lớp: mỗi lớp có thời khóa biểu riêng.
-- weekday theo cách gọi của trường học: 2 = Thứ 2 ... 6 = Thứ 6.
-- Mỗi ngày có tối đa 2 buổi (sáng/chiều), mỗi buổi tối đa 5 tiết; ngày/buổi/tiết nào không có dữ liệu thì là trống.
CREATE TABLE IF NOT EXISTS pop_timetable_entries (
    class_id UUID NOT NULL REFERENCES pop_classes(id) ON DELETE CASCADE,
    weekday SMALLINT NOT NULL CHECK (weekday BETWEEN 2 AND 6),
    session TEXT NOT NULL CHECK (session IN ('MORNING', 'AFTERNOON')),
    period SMALLINT NOT NULL CHECK (period BETWEEN 1 AND 5),
    subject TEXT NOT NULL CHECK (char_length(subject) BETWEEN 1 AND 40),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (class_id, weekday, session, period)
);

-- Cấu hình chung (một dòng duy nhất): giờ gửi thông báo mỗi ngày và bật/tắt.
CREATE TABLE IF NOT EXISTS pop_timetable_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    send_time TEXT NOT NULL DEFAULT '19:30' CHECK (send_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO pop_timetable_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- Nhật ký gửi: mỗi lớp tối đa một lần gửi tự động mỗi ngày (khoá chính chống gửi trùng, kể cả khi chạy nhiều máy chủ).
CREATE TABLE IF NOT EXISTS pop_timetable_dispatches (
    class_id UUID NOT NULL REFERENCES pop_classes(id) ON DELETE CASCADE,
    send_date DATE NOT NULL,
    notification_id UUID REFERENCES pop_notifications(id) ON DELETE SET NULL,
    recipient_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (class_id, send_date)
);
