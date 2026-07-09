import http from 'k6/http';
import { check, sleep } from 'k6';

// Cấu hình kịch bản stress test: 100 requests đồng thời từ 100 người dùng ảo (VUs)
export const options = {
  scenarios: {
    concurrent_uploads: {
      executor: 'per-vu-iterations',
      vus: 100,         // 100 người dùng ảo giả lập đồng thời
      iterations: 1,    // Mỗi VU chỉ thực hiện gửi đúng 1 request
      maxDuration: '30s',
    },
  },
};

// Đọc tệp tin nhị phân test (Đảm bảo file test_file.pdf tồn tại cùng thư mục chạy k6)
const pdfData = open('./test_file.pdf', 'b'); 

export default function () {
  const url = 'http://localhost:8080/api/v1/original-documents';
  
  // Tạo dữ liệu dạng Multipart Form
  const payload = {
    title: 'Báo cáo Tài chính Q2 đồng thời',
    file: http.file(pdfData, 'test_file.pdf', 'application/pdf'),
  };

  // Cấu hình Headers (Trích xuất token xác thực của Nhân viên thuộc Phòng ban A)
  const params = {
    headers: {
      'Authorization': 'Bearer JWT_TOKEN_CUA_NHAN_VIEN_PHONG_BAN_A',
    },
  };

  // Gửi request POST upload tệp tin
  const res = http.post(url, payload, params);

  // Kiểm tra kết quả phản hồi theo cam kết SLA
  check(res, {
    'HTTP status là 200 hoặc 201': (r) => r.status === 200 || r.status === 201,
    'Không xảy ra lỗi hệ thống 500': (r) => r.status !== 500,
    'Không xảy ra lỗi timeout': (r) => r.status !== 504 && r.status !== 408,
    'Cờ duplicated hoạt động chính xác': (r) => {
       const json = r.json();
       // Request tạo thành công đầu tiên trả về 201 và duplicated=false
       // 99 requests trùng lặp xếp hàng sau trả về 200 và duplicated=true
       return r.status === 201 ? json.duplicated === false : json.duplicated === true;
    },
    'Trả về siêu dữ liệu tệp cũ hợp lệ': (r) => {
       const json = r.json();
       return json.id !== null && json.businessCode !== null && json.hash !== null;
    }
  });

  // Nghỉ 1 giây để hoàn tất tiến trình
  sleep(1);
}
