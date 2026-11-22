-- Thời gian dùng TikTok/YouTube theo ngày của học sinh (máy học sinh gửi lên, giáo viên xem ở /admin).
CREATE TABLE IF NOT EXISTS pop_app_usage (
  user_id UUID NOT NULL REFERENCES pop_users(id) ON DELETE CASCADE,
  usage_date DATE NOT NULL,
  app_key TEXT NOT NULL CHECK (app_key IN ('tiktok', 'youtube')),
  minutes INTEGER NOT NULL CHECK (minutes >= 0 AND minutes <= 1440),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, usage_date, app_key)
);
CREATE INDEX IF NOT EXISTS pop_app_usage_date_idx ON pop_app_usage(usage_date);
