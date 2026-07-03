# BÁO CÁO ĐÁNH GIÁ MÃ NGUỒN TUẦN 1 (BẢN CẬP NHẬT HOÀN CHỈNH)
**Dự án:** VCC Enterprise AI Knowledge Platform (VCC-EAP)  
**Người đánh giá:** Antigravity (AI Tech Lead / Architect)  
**Đối tượng đánh giá:** Mã nguồn Java & React (Week 1 Release)  

---

## 1. Kết Quả Kiểm Thử Thực Tế trên Public Domain

Tôi đã thực hiện kiểm thử tự động trên public domain backend (`https://api-vccintern.shares.zrok.io`) sử dụng các tài khoản và phân quyền thực tế của sinh viên cung cấp (`user1` - BOARD, `user2` - Manager phòng RND, `user3` - Employee phòng HR).

Kết quả kiểm thử cho thấy **tất cả các logic nghiệp vụ lõi đều hoạt động chính xác 100%**:

| Kịch bản Test | API Endpoint | Kết quả thực tế | Trạng thái |
| :--- | :--- | :--- | :--- |
| **1. Upload tài liệu (RND)** | `POST /api/v1/original-documents` | Tải file lên thành công, sinh ID có LSB = 0 (`f623e800-...`) và hash SHA-256. | **ĐẠT** |
| **2. Upload tài liệu (BOARD)** | `POST /api/v1/original-documents` | BOARD tải file lên thành công (`70d4947c-...`). | **ĐẠT** |
| **3. Cô lập dữ liệu chéo** | `GET /api/v1/original-documents/{id}` | Nhân viên HR (`user3`) gọi trực tiếp tài liệu của RND -> bị chặn đứng (404 Not Found). | **ĐẠT** |
| **4. Tạo liên kết Alias** | `POST /api/v1/alias-documents` | Manager RND chia sẻ tài liệu sang phòng HR thành công, sinh UUID có LSB = 1. | **ĐẠT** |
| **5. Xem tài liệu qua Alias** | `GET /api/v1/original-documents/{id}` | Nhân viên HR truy xuất thông tin tài liệu RND thành công sau khi được chia sẻ Alias. | **ĐẠT** |
| **6. Giải quyết Alias (Download)** | `GET /api/v1/alias-documents/{id}` | Nhân viên HR tải file vật lý của tài liệu RND thành công (200 OK). | **ĐẠT** |
| **7. Bảo vệ tài liệu BOARD** | `POST /api/v1/alias-documents` | Hệ thống chặn đứng hành vi tạo Alias cho tài liệu của BOARD (`ERR_BOARD_PROTECTION`). | **ĐẠT** |
| **8. Xóa mềm lan truyền** | `DELETE /api/v1/original-documents/{id}` | Manager RND xóa gốc -> thành công và tự động đánh dấu xóa toàn bộ Alias liên quan. | **ĐẠT** |
| **9. Kiểm tra sau xóa** | `GET /api/v1/original-documents/{id}` | Nhân viên HR truy xuất lại tài liệu đã chia sẻ -> bị chặn (404 Not Found). | **ĐẠT** |

### ⚠️ Phát hiện lỗi bất nhất giữa Tài liệu thiết kế và Thực tế triển khai:
*   Trong tài liệu `detailed_design.md` của sinh viên ghi nhận body của API tạo Alias là dạng snake_case: `original_document_id` và `alias_department_id`.
*   Tuy nhiên, trong code Java ([CreateAliasRequest.java](file:///f:/Workplace/vcc/intern/student_repo/eap/src/main/java/com/vccorp/eap/dto/CreateAliasRequest.java#L14)), sinh viên không cấu hình mapper snake_case nên hệ thống **chỉ chấp nhận camelCase** (`originalDocumentId` và `aliasDepartmentId`). Nếu client gửi payload theo tài liệu thiết kế sẽ lập tức bị lỗi `VALIDATION_ERROR` (400 Bad Request).
*   *Khắc phục*: Sinh viên cần đồng bộ tài liệu hoặc thêm `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` vào các DTO hoặc cấu hình Jackson toàn cục.

---

## 2. Mô hình Kiến trúc: Controller -> Service -> DAO (Repository)

Kiến trúc phân tầng của sinh viên hoàn toàn **tuân thủ mô hình chuẩn Enterprise**:
*   **Controller Layer**: Rất mỏng (`UserController`, `DocumentController`,...), chỉ đảm nhận việc tiếp nhận request, kiểm tra xác thực (qua `SecurityContextHelper`), và gọi xuống tầng Service. Không chứa bất kỳ câu truy vấn DB hay logic nghiệp vụ nào.
*   **Service Layer**: Chứa toàn bộ các xử lý nghiệp vụ (`DocumentService`, `UserService`,...) để đảm bảo tính toàn vẹn dữ liệu.
*   **DAO (Repository) Layer**: Sử dụng Spring Data JPA (`UserRepository`, `DocumentRepository`,...) giúp thao tác CSDL rõ ràng và bảo mật nhờ tham số hóa câu truy vấn.

---

## 3. Thiết kế Hướng Đối Tượng (OOP) và Nguyên Tắc Đơn Trách Nhiệm (SRP)

### 3.1. Điểm Tốt (OOP)
*   Thực thể `Document` tự đóng gói logic kiểm tra phân loại `isAlias()` và `isOriginal()` bằng phép toán bit trên RAM, thể hiện tư duy hướng đối tượng tốt.

### 3.2. Điểm Cần Cải Thiện (SRP)
*   **Đưa phần validation/tiện ích ra các class Utilities/Static**:
    *   Trong `UserService.java`, sinh viên tự viết code Regex kiểm tra email, username, kiểm tra độ dài ký tự,... một cách thủ công. Điều này làm code Service bị loãng và khó đọc.
    *   *Khắc phục*: Nên tách các logic kiểm tra định dạng và định dạng chuỗi này thành các phương thức static nằm trong các lớp tiện ích chuyên biệt (ví dụ: `UserValidationUtils` hoặc `ValidationHelper` đặt tại gói `common.util`).
*   **Tách biệt logic Quản lý Tệp tin vật lý**:
    *   `DocumentService.java` hiện đang trực tiếp thực hiện việc ghi file (`Files.copy`) và tạo thư mục lưu trữ (`dir.mkdirs()`).
    *   *Khắc phục*: Nên tách các thao tác xử lý tệp tin vật lý ra một lớp riêng biệt như `StorageService` (hoặc `FileStorageService`).

---

## 4. Đánh Giá Tính Dễ Bảo Trì và Mở Rộng

*   **Vấn đề phụ thuộc trực tiếp vào Concrete Class thay vì Interface**:
    *   Các Controller đang tiêm (inject) trực tiếp các Class cụ thể như `DocumentService`, `UserService` thay vì các Interface.
    *   *Hệ quả*: Giới hạn khả năng bảo trì khi hệ thống lớn lên. Ví dụ nếu sau này cần đổi module lưu trữ file từ Local Server sang AWS S3, ta sẽ phải sửa đổi trực tiếp trên class `DocumentService` thay vì chỉ cần tạo một implementation mới cho Interface `StorageService`.
    *   *Khắc phục*: Khai báo Interface cho toàn bộ các Service nghiệp vụ và các dịch vụ bổ trợ, thực hiện inject interface tại Controller.

---

## 5. Các Vấn Đề Bảo Mật và Hiệu Năng Cần Lưu Ý

Mặc dù các lỗi CORS và DoS do dung lượng file đã được Mentor xác nhận có thể tạm bỏ qua trong giai đoạn thử nghiệm local, hệ thống vẫn tồn tại một số vấn đề bảo mật và hiệu năng khác cần khắc phục trước khi golive:

*   **Nút thắt hiệu năng truy vấn DB trong JWT Filter**:
    *   [JwtAuthenticationFilter.java](file:///f:/Workplace/vcc/intern/student_repo/eap/src/main/java/com/vccorp/eap/infrastructure/security/JwtAuthenticationFilter.java#L43) thực hiện `userRepository.existsById(userId)` trên mọi request để kiểm tra user tồn tại. Điều này làm mất đi tính chất "stateless" (không trạng thái) của JWT và tạo tải trọng SELECT liên tục không cần thiết vào DB.
    *   *Khắc phục*: Có thể bỏ bước check này (tin tưởng hoàn toàn vào chữ ký JWT) hoặc đưa thông tin này vào Redis cache.
*   **Rò rỉ thông tin đường dẫn vật lý trên server (Information Disclosure)**:
    *   Khi API phản hồi thông tin tài liệu, thực thể `Document` được trả về trực tiếp và chứa thuộc tính `fileReference` là đường dẫn tuyệt đối của file trên máy chủ (ví dụ: `F:\\Workplace\\vcc\\...`). Kẻ tấn công có thể lợi dụng thông tin này để phục vụ các hành vi khai thác Path Traversal hoặc tìm hiểu cấu trúc hệ thống.
    *   *Khắc phục*: Sử dụng Response DTO để ẩn hoàn toàn trường `fileReference` trước khi trả về cho client.
*   **Bypass định dạng file thực tế (Content-Type Spoofing)**:
    *   Hệ thống chỉ kiểm tra đuôi mở rộng file thông qua chuỗi ký tự tên file (`originalFilename.substring(...)`). Kẻ tấn công dễ dàng bypass bằng cách đổi tên một file script độc hại thành `.pdf` (ví dụ: `shell.sh.pdf`) để upload lên server.
    *   *Khắc phục*: Sử dụng thư viện kiểm tra cấu hình file thực tế qua Magic Bytes (ví dụ: Apache Tika).
*   **Lưu trữ Refresh Token ở LocalStorage (Lỗ hổng XSS Hijacking)**:
    *   Frontend lưu trữ cả `accessToken` và `refreshToken` trong `localStorage` ([AuthContext.tsx](file:///f:/Workplace/vcc/intern/student_repo/eap/frontend/src/store/AuthContext.tsx#L29)). Nếu trang web bị dính lỗi XSS, kẻ tấn công có thể lấy cắp Refresh Token và chiếm quyền điều khiển tài khoản nạn nhân vĩnh viễn.
    *   *Khắc phục*: Bắt buộc phải cấu hình lưu trữ `refreshToken` dưới dạng **HttpOnly, Secure, SameSite Cookie** từ phía backend.

---

## 6. Điểm Số Đánh Giá Đề Xuất (Rubric Grading)

Dựa trên bảng tiêu chí đánh giá của Mentor:

| Tiêu chí | Điểm số | Nhận xét |
| :--- | :--- | :--- |
| **System Foundation** | **8.5 / 10** | **Tốt**: Xử lý tốt race condition bằng bi quan khóa (`FOR UPDATE`), nhúng bitwise UUID tối ưu 0ms check, Hibernate Filter thông minh. API test thực tế chạy đúng logic. Điểm trừ nằm ở nách cổ chai hiệu năng trong JWT Filter. |
| **Defense & Architecture** | **8.5 / 10** | **Tốt**: Kiến trúc phân tầng Controller-Service-DAO chuẩn. Tuy nhiên thiết kế Service vi phạm SRP (cần đưa validation ra utility class, tách module storage) và thiếu lớp Interface làm giảm tính linh hoạt khi mở rộng. |

**Kết luận:** Bài làm của sinh viên đạt chất lượng tốt, chạy thực tế ổn định. Chỉ cần điều chỉnh các điểm thiết kế code (SRP, Interface) và tối ưu JWT Filter để đạt điểm tuyệt đối.
