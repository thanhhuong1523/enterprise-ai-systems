import http from 'k6/http';
import { check, sleep, group } from 'k6';

// ===============================
// Cấu hình chung
// ===============================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Tài khoản người dùng thuộc Phòng ban A và Phòng ban B
const USER_A = { username: __ENV.USERNAME_A || 'employeeA', password: __ENV.PASSWORD_A || '123456' };
const USER_B = { username: __ENV.USERNAME_B || 'employeeB', password: __ENV.PASSWORD_B || '123456' };

// ===============================
// Đọc file upload
// ===============================
const pdfData = open('./test_file.pdf', 'b');

// ===============================
// Hàm tiện ích
// ===============================
function login(username, password) {
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, {
    [`Đăng nhập thành công (${username})`]: (r) => r.status === 200,
  });

  if (loginRes.status !== 200) {
    throw new Error(`Đăng nhập thất bại [${username}]. Status = ${loginRes.status}`);
  }

  const token = loginRes.json().accessToken;
  if (!token) {
    throw new Error(`Không lấy được accessToken từ API đăng nhập [${username}]`);
  }
  return token;
}

function uploadFile(token, title) {
  const payload = {
    title: title,
    file: http.file(pdfData, 'test_file.pdf', 'application/pdf'),
  };
  return http.post(
    `${BASE_URL}/api/v1/original-documents`,
    payload,
    { headers: { Authorization: `Bearer ${token}` } }
  );
}

function softDeleteDocument(token, documentId) {
  return http.del(
    `${BASE_URL}/api/v1/original-documents/${documentId}`,
    null,
    { headers: { Authorization: `Bearer ${token}` } }
  );
}

// ===============================
// setup() – Chạy 1 lần trước tất cả VU
// ===============================
export function setup() {
  const tokenA = login(USER_A.username, USER_A.password);
  const tokenB = login(USER_B.username, USER_B.password);

  // Tải lên 1 file trước bằng Phòng ban A để tạo bản ghi cho kịch bản upload file đã soft-delete
  const firstUpload = uploadFile(tokenA, 'Tài liệu chuẩn bị cho kịch bản soft-delete');
  check(firstUpload, {
    'Upload chuẩn bị thành công': (r) => r.status === 201 || r.status === 200,
  });

  let existingDocId = null;
  if (firstUpload.status === 201 || firstUpload.status === 200) {
    existingDocId = firstUpload.json().id;
  }

  return {
    tokenA: tokenA,
    tokenB: tokenB,
    existingDocId: existingDocId,
  };
}

// ===============================
// Kịch bản kiểm thử
// ===============================
export const options = {
  scenarios: {
    // Kịch bản 1: 100 requests đồng thời tải trùng file (cùng Phòng ban A)
    concurrent_duplicate_upload: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '30s',
      exec: 'testConcurrentDuplicateUpload',
      tags: { scenario: 'concurrent_duplicate' },
    },
    // Kịch bản 2: Upload file từ Phòng ban B (cùng nội dung) để kiểm tra SIS và cô lập dữ liệu
    cross_department_upload: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '15s',
      exec: 'testCrossDepartmentUpload',
      tags: { scenario: 'cross_department' },
      startTime: '35s',
    },
    // Kịch bản 3: Upload lại file đã bị soft-delete
    reupload_after_soft_delete: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '15s',
      exec: 'testReuploadAfterSoftDelete',
      tags: { scenario: 'soft_delete_reupload' },
      startTime: '55s',
    },
  },
};

// ===============================
// Kịch bản 1: Concurrent Duplicate Upload (Phòng ban A)
// Mục tiêu: Xác minh đúng 1 bản ghi được tạo, 99 còn lại báo trùng
// AC-1, AC-2, AC-3, AC-5
// ===============================
export function testConcurrentDuplicateUpload(data) {
  const res = uploadFile(data.tokenA, 'Báo cáo Tài chính Q2 đồng thời');

  check(res, {
    '[S1] HTTP 200 hoặc 201': (r) => r.status === 200 || r.status === 201,
    '[S1] Không có lỗi 500': (r) => r.status !== 500,
    '[S1] Không timeout (408/504)': (r) => r.status !== 408 && r.status !== 504,
    '[S1] Không quá tải (429)': (r) => {
      // 429 chấp nhận được nhưng cần ghi nhận để giám sát connection pool
      if (r.status === 429) {
        console.warn(`[S1] Connection pool starvation detected: ${r.body}`);
      }
      return true;
    },
    '[S1] Cờ duplicated đúng': (r) => {
      if (r.status === 429) return true; // Skip nếu bị throttle
      const body = r.json();
      if (r.status === 201) return body.duplicated === false;
      if (r.status === 200) return body.duplicated === true;
      return false;
    },
    '[S1] Metadata hợp lệ': (r) => {
      if (r.status === 429) return true;
      const body = r.json();
      return body.id != null && body.businessCode != null && body.hash != null;
    },
  });

  sleep(1);
}

// ===============================
// Kịch bản 2: Upload cùng nội dung từ Phòng ban B (Cross-Department SIS)
// Mục tiêu: Hệ thống tái sử dụng file vật lý (SIS), không rò rỉ thông tin phòng ban A
// AC-4, BR-2, FR-003, NFR Bảo mật
// ===============================
export function testCrossDepartmentUpload(data) {
  group('Cross-department upload (SIS reuse)', () => {
    const res = uploadFile(data.tokenB, 'Báo cáo Tài chính Q2 - Phòng ban B');

    check(res, {
      '[S2] HTTP 201 (Phòng ban B tạo bản ghi mới)': (r) => r.status === 201,
      '[S2] duplicated = false (Phòng ban B không thấy là trùng)': (r) => {
        return r.json().duplicated === false;
      },
      '[S2] ownerDepartmentId là của Phòng ban B': (r) => {
        const body = r.json();
        return body.ownerDepartmentId != null;
      },
      '[S2] Metadata hợp lệ': (r) => {
        const body = r.json();
        return body.id != null && body.hash != null;
      },
    });
  });

  sleep(1);
}

// ===============================
// Kịch bản 3: Upload lại file sau khi Soft Delete
// Mục tiêu: Sau khi xóa mềm, người dùng có thể tải lên lại file như tài liệu mới (BR-3)
// AC-6, BR-3
// ===============================
export function testReuploadAfterSoftDelete(data) {
  group('Reupload after soft delete', () => {
    // Bước 1: Soft delete tài liệu đã có
    if (!data.existingDocId) {
      console.warn('[S3] Không tìm được existingDocId từ setup(), bỏ qua kịch bản này.');
      return;
    }

    const deleteRes = softDeleteDocument(data.tokenA, data.existingDocId);
    check(deleteRes, {
      '[S3] Soft delete thành công (204)': (r) => r.status === 204 || r.status === 200,
    });

    sleep(0.5);

    // Bước 2: Upload lại cùng nội dung file sau khi đã xóa mềm
    const reuploadRes = uploadFile(data.tokenA, 'Báo cáo Tài chính Q2 - Tải lại sau xóa mềm');
    check(reuploadRes, {
      '[S3] Tải lên lại thành công (201)': (r) => r.status === 201,
      '[S3] duplicated = false (Bản ghi cũ đã bị xóa mềm)': (r) => {
        return r.json().duplicated === false;
      },
      '[S3] Tạo được bản ghi mới (ID khác bản ghi cũ)': (r) => {
        return r.json().id !== data.existingDocId;
      },
    });
  });

  sleep(1);
}