-- Thông báo toàn hệ thống (không thuộc lớp nào) kèm liên kết, dùng cho thông báo "có bản cập nhật mới".
ALTER TABLE pop_notifications ALTER COLUMN class_id DROP NOT NULL;
ALTER TABLE pop_notifications ADD COLUMN IF NOT EXISTS link_url TEXT
  CHECK (link_url IS NULL OR (char_length(link_url) <= 300 AND link_url ~ '^https://'));
CREATE INDEX IF NOT EXISTS pop_notifications_broadcast_idx
  ON pop_notifications(created_at DESC) WHERE class_id IS NULL;
