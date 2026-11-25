-- Nhật ký thao tác thêm/sửa/xóa dữ liệu từ trang quản trị hệ thống (/sysadmin → Dữ liệu).
CREATE TABLE IF NOT EXISTS pop_admin_audit_log (
  id BIGSERIAL PRIMARY KEY,
  table_name TEXT NOT NULL,
  action TEXT NOT NULL CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
  row_key JSONB NOT NULL,
  before_data JSONB,
  after_data JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS pop_admin_audit_log_created_idx ON pop_admin_audit_log(created_at DESC);
