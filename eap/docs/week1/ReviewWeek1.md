# BÁO CÁO NGHIỆM THU CHI TIẾT TUẦN 1 (BẢN CẬP NHẬT)
**Dự án:** VCC Enterprise AI Knowledge Platform (VCC-EAP)  
**Người đánh giá:** Antigravity (AI Tech Lead / Architect)  
**Đối tượng đánh giá:** Mã nguồn Java & React (Week 1 Release - Phiên bản cập nhật nghiệm thu)  

---

## 1. Kết Quả Nghiệm Thu Tổng Quan

Sinh viên đã tiến hành sửa đổi các lỗi bảo mật, lỗi hiệu năng và lỗi edge case được chỉ ra trong đợt review trước:

1.  **Chặn BOARD nhận Alias (BOARD target protection)**:
    *   *Kết quả*: Sinh viên đã bổ sung hàm kiểm tra `validateAliasTargetDepartment` trong `DocumentServiceImpl.java`. Hệ thống từ chối và ném lỗi `ERR_BOARD_PROTECTION` khi có yêu cầu tạo liên kết Alias hướng tới phòng Ban Giám Đốc (`BOARD`).
2.  **Khắc phục Nút thắt hiệu năng truy vấn DB trong JWT Filter**:
    *   *Kết quả*: Cải tiến `JwtAuthenticationFilter` hoạt động stateless. Thực hiện giải mã JWT Claims trực tiếp trên RAM để lấy thông tin của `User` (id, username, email, role, departmentId) và gán vào SecurityContext mà không cần truy vấn CSDL (`userRepository.existsById` đã bị gỡ bỏ). Điều này giúp giảm tải trọng truy vấn trên mỗi API request.
3.  **Khắc phục rò rỉ Refresh Token ở LocalStorage**:
    *   *Kết quả*: Backend (`AuthController`) đã chuyển sang thiết lập `refreshToken` thông qua header `Set-Cookie` cấu hình `HttpOnly`, `Secure`, và `SameSite` phù hợp. Trường `refreshToken` trong Response Body được gán về `null` để hạn chế nguy cơ bị khai thác qua lỗi XSS.
    *   *Frontend*: `AuthContext.tsx` và API client đã cập nhật để gửi request Refresh Token thông qua Axios với tùy chọn `{ withCredentials: true }`, thực hiện xoay vòng token tự động qua Cookie.
4.  **Bổ sung hệ thống Integration Tests**:
    *   Sinh viên đã xây dựng các lớp kiểm thử tích hợp tự động: `AliasIntegrationTest`, `RefreshTokenIntegrationTest`, `SecurityIntegrationTest`, và `JwtAuthenticationFilterTest` giúp kiểm soát tốt các thay đổi mã nguồn.

---

## 2. Các Điểm Then Chốt Đánh Giá (Keynotes)

*   **Keynote 1: Bảo mật và Phân quyền chéo (Cross-department Security)**
    *   Mô hình lai kết hợp Hibernate Filter tự động và giải pháp Bitwise UUID cho phép phân loại tài liệu (Original/Alias) trực tiếp trên RAM với độ trễ tiệm cận 0ms, đồng thời đảm bảo an toàn tuyệt đối cho phân vùng dữ liệu giữa các phòng ban nghiệp vụ.
*   **Keynote 2: Phòng thủ Upload Tệp độc hại & Rò rỉ thông tin (File Security & Info Disclosure)**
    *   Việc áp dụng thư viện Apache Tika để đối chiếu Magic Bytes thực tế của tệp tin tải lên giúp loại bỏ rủi ro bypass định dạng (Content-Type Spoofing). Bên cạnh đó, việc đóng gói dữ liệu phản hồi qua DTO giúp che giấu hoàn toàn đường dẫn vật lý trên server (`fileReference`), ngăn ngừa nguy cơ bị khai thác Path Traversal.
*   **Keynote 3: Bảo mật phiên Stateless & Tối ưu hóa hiệu năng (Stateless Auth & Session Security)**
    *   Cơ chế lưu trữ Refresh Token trong HttpOnly Cookie kết hợp kỹ thuật xoay vòng (Token Rotation) một lần giúp triệt tiêu nguy cơ bị đánh cắp phiên qua lỗi XSS. JWT filter hoạt động hoàn toàn trên RAM giúp giải phóng cơ sở dữ liệu khỏi tải trọng SELECT liên tục trên mỗi HTTP request.
*   **Keynote 4: Định hướng kỹ thuật cho Tuần 2 (Next Steps for Week 2)**
    *   Sinh viên cần đặc biệt lưu ý kiểm soát các xung đột CSDL (deadlock) khi triển khai cơ chế kiểm tra tệp tin trùng lặp đồng thời (Concurrent upload & duplication check) ở Tuần 2. Cơ chế khóa bi quan (`PESSIMISTIC_WRITE`) hiện tại cần được tối ưu hóa để đảm bảo khả năng chịu tải cao khi có hàng trăm request đồng thời.

---

## 3. Kết Quả Kiểm Thử API Thực Tế (Quy trình kiểm thử)

Kiểm thử tự động trên public domain backend (`https://api-vccintern.shares.zrok.io`) xác nhận các luồng nghiệp vụ hoạt động ổn định:

| Kịch bản Test | API Endpoint | Kết quả thực tế | Trạng thái |
| :--- | :--- | :--- | :--- |
| **1. Upload tài liệu (RND)** | `POST /api/v1/original-documents` | Tải tệp tin thành công. Apache Tika xác thực cấu trúc PDF hợp lệ. | **ĐẠT** |
| **2. Cô lập dữ liệu chéo** | `GET /api/v1/original-documents/{id}` | Nhân viên HR truy cập trực tiếp tài liệu gốc RND bị chặn (404 Not Found). | **ĐẠT** |
| **3. Tạo liên kết Alias** | `POST /api/v1/alias-documents` | Manager RND chia sẻ tài liệu sang HR thành công. LSB của ID Alias = 1. | **ĐẠT** |
| **4. Xem tài liệu qua Alias** | `GET /api/v1/original-documents/{id}` | Nhân viên HR xem chi tiết tài liệu RND thành công qua Alias liên kết. | **ĐẠT** |
| **5. Tải file qua Alias (Download)** | `GET /api/v1/alias-documents/{id}` | Nhân viên HR tải file gốc từ RND qua link Alias thành công (200 OK). | **ĐẠT** |
| **6. Chống rò rỉ tài liệu BOARD (Source)** | `POST /api/v1/alias-documents` | Hệ thống chặn hành vi tạo Alias từ tài liệu gốc của BOARD. | **ĐẠT** |
| **7. Chống rác tài liệu BOARD (Target)** | `POST /api/v1/alias-documents` | Hệ thống chặn hành vi tạo Alias chia sẻ tài liệu RND đến BOARD (`ERR_BOARD_PROTECTION`). | **ĐẠT** |
| **8. Xóa mềm lan truyền (Cascade)** | `DELETE /api/v1/original-documents/{id}`| Xóa tài liệu gốc RND thành công, tự động soft-delete các Alias liên quan. | **ĐẠT** |
| **9. Kiểm tra sau xóa mềm** | `GET /api/v1/original-documents/{id}` | Truy xuất sau khi xóa bị chặn (404 Not Found). | **ĐẠT** |

---

## 4. Điểm Số Đánh Giá Nghiệm Thu Đề Xuất (Rubric Grading)

| Tiêu chí | Điểm số | Nhận xét |
| :--- | :--- | :--- |
| **System Foundation** | **9.0 / 10** | **Tốt**: Đã giải quyết các edge case nghiệp vụ, cô lập dữ liệu chéo tốt, tối ưu hóa stateless JWT filter giảm truy vấn DB, cấu hình an toàn HttpOnly cookie cho Refresh Token. Hệ thống hoạt động đúng theo đặc tả nghiệp vụ.<br>*Điểm cần lưu ý thêm*: Chưa xây dựng cơ chế thu hồi (revoke) access token tức thì trước khi hết hạn (ví dụ sử dụng Redis blacklist cho token bị rò rỉ). |
| **Defense & Architecture** | **9.0 / 10** | **Tốt**: Kiến trúc phân tầng Controller-Service-DAO chuẩn hóa qua Interface. Thiết kế tuân thủ nguyên tắc SRP, bổ sung validators tiện ích và storage service độc lập, hệ thống Integration Tests đầy đủ.<br>*Điểm cần lưu ý thêm*: Code xử lý lỗi chung (Global Exception Handler) có thể chi tiết hơn đối với một số lỗi hệ thống cấp thấp. |

**Kết luận:** Bài làm Tuần 1 của sinh viên đạt yêu cầu nghiệm thu **(9.0/10 - Tốt)**. Hệ thống hoạt động đúng logic nghiệp vụ và đáp ứng các tiêu chuẩn bảo mật cơ bản đặt ra cho tuần đầu. Đủ điều kiện để bàn giao và tiếp tục thực hiện Tuần 2.
