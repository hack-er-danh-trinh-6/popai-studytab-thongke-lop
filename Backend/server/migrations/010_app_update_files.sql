-- Bảng lưu thông tin phiên bản ứng dụng và file APK để cập nhật.
-- Admin tải APK lên ở trang /updateapp, hệ thống lưu file và phiên bản.
-- App Android gọi GET /api/mobile/v1/update/latest để so sánh phiên bản khi mở.
CREATE TABLE IF NOT EXISTS pop_app_versions (
  id UUID PRIMARY KEY,
  version TEXT NOT NULL UNIQUE,
  version_code INTEGER,
  apk_file_name TEXT NOT NULL,
  apk_url TEXT NOT NULL,
  apk_size BIGINT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  uploaded_by TEXT
);

CREATE INDEX IF NOT EXISTS pop_app_versions_active_idx
  ON pop_app_versions(is_active, created_at DESC) WHERE is_active;

-- Giới hạn kích thước file APK tối đa 200 MB để tránhslant lưu trữ.
ALTER TABLE pop_app_versions
  ADD CONSTRAINT pop_app_versions_size_check
  CHECK (apk_size IS NULL OR apk_size <= 200 * 1024 * 1024);
