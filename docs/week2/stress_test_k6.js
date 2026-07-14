import http from 'k6/http';
import { check, sleep } from 'k6';

// ===============================
// Cấu hình
// ===============================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const USERNAME = __ENV.USERNAME || 'employeeA';
const PASSWORD = __ENV.PASSWORD || '123456';

// ===============================
// Kịch bản kiểm thử
// ===============================
export const options = {
  scenarios: {
    concurrent_uploads: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

// ===============================
// Đọc file upload
// ===============================
const pdfData = open('./test_file.pdf', 'b');

// ===============================
// setup()
// Chỉ chạy 1 lần trước khi toàn bộ VUs bắt đầu
// ===============================
export function setup() {

  const loginPayload = JSON.stringify({
    username: USERNAME,
    password: PASSWORD,
  });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    loginPayload,
    {
      headers: {
        'Content-Type': 'application/json',
      },
    }
  );

  check(loginRes, {
    'Đăng nhập thành công': (r) => r.status === 200,
  });

  if (loginRes.status !== 200) {
    throw new Error(`Đăng nhập thất bại. Status = ${loginRes.status}`);
  }

  const token = loginRes.json().accessToken;

  if (!token) {
    throw new Error('Không lấy được accessToken từ API đăng nhập');
  }

  return {
    token: token,
  };
}

// ===============================
// Hàm chính
// ===============================
export default function (data) {

  const payload = {
    title: 'Báo cáo Tài chính Q2 đồng thời',
    file: http.file(
      pdfData,
      'test_file.pdf',
      'application/pdf'
    ),
  };

  const params = {
    headers: {
      Authorization: `Bearer ${data.token}`,
    },
  };

  const res = http.post(
    `${BASE_URL}/api/v1/original-documents`,
    payload,
    params
  );

  check(res, {
    'HTTP status là 200 hoặc 201': (r) =>
      r.status === 200 || r.status === 201,

    'Không xảy ra lỗi 500': (r) =>
      r.status !== 500,

    'Không xảy ra timeout': (r) =>
      r.status !== 408 && r.status !== 504,

    'Cờ duplicated hoạt động đúng': (r) => {
      const body = r.json();

      if (r.status === 201) {
        return body.duplicated === false;
      }

      if (r.status === 200) {
        return body.duplicated === true;
      }

      return false;
    },

    'Metadata trả về hợp lệ': (r) => {
      const body = r.json();

      return (
        body.id != null &&
        body.businessCode != null &&
        body.hash != null
      );
    },
  });

  sleep(1);
}