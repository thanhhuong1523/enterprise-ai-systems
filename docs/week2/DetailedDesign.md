# Tài liệu Thiết kế Chi tiết (Detailed Design) - Tuần 2
## Đặc tả Chi tiết Cơ chế Chống trùng lặp & Xử lý Tải đồng thời

---

## 1. Thiết kế Mô hình Dữ liệu (Database Design)

### 1.1. Cấu trúc bảng `documents` (Siêu dữ liệu tài liệu)

| Tên cột logic | Kiểu dữ liệu logic | Nullable | Ràng buộc / Khóa ngoại | Ý nghĩa nghiệp vụ |
| :--- | :--- | :--- | :--- | :--- |
| `id` | UUID | NO | Khóa chính (LSB: 0 = Original, 1 = Alias) | Mã định danh duy nhất của tài liệu. |
| `business_code` | VARCHAR(50) | NO | UNIQUE | Mã nghiệp vụ thân thiện hiển thị cho người dùng. |
| `title` | VARCHAR(255) | NO | | Tiêu đề hiển thị của tài liệu. |
| `file_reference` | VARCHAR(512) | YES | | Đường dẫn lưu trữ vật lý của file trên đĩa (null đối với alias). |
| `file_size` | BIGINT | YES | | Dung lượng tệp tính bằng byte (null đối với alias). |
| `hash` | VARCHAR(64) | YES | | Mã băm SHA-256 (64 ký tự hex) đại diện cho nội dung tệp. |
| `owner_department_id` | UUID | NO | Khóa ngoại | Phòng ban sở hữu tài liệu này. |
| `parent_id` | UUID | YES | Khóa ngoại (tới `documents.id`) | Liên kết tới tài liệu gốc nếu bản ghi là alias. |
| `creator_department_id`| UUID | YES | Khóa ngoại | Phòng ban thực hiện chia sẻ (chỉ áp dụng cho alias). |
| `created_by` | UUID | YES | Khóa ngoại | Người dùng thực hiện tải lên tệp tin. |
| `created_at` | TIMESTAMP | NO | | Thời điểm tạo bản ghi. |
| `updated_at` | TIMESTAMP | YES | | Thời điểm cập nhật tiêu đề gần nhất. |
| `deleted_at` | TIMESTAMP | YES | | Thời điểm xóa mềm tài liệu. |

### 1.2. Thiết kế Chỉ mục (Index Design)
*   **Chỉ mục duy nhất kết hợp lọc tiền tố (uq_documents_hash_dept)**:
    *   *Mục đích*: Bảo vệ dữ liệu không bị trùng lặp ở tầng DB, đồng thời làm chỉ mục chính phục vụ câu truy vấn gộp tìm kiếm theo mã băm.
    *   *Loại chỉ mục*: UNIQUE B-Tree.
    *   *Các trường*: `(hash, owner_department_id)`.
    *   *Điều kiện lọc (Partial Index)*: `WHERE deleted_at IS NULL`.
*   *Lưu ý*: Không tạo thêm chỉ mục phụ nào khác trên trường `hash` để tránh dư thừa và giảm hiệu năng ghi.

---

## 2. Đặc tả API (API Design)

### 2.1. API POST /api/v1/original-documents
*   **Method**: `POST`
*   **Content-Type**: `multipart/form-data`
*   **Tham số Request**:
    *   `title` (Chuỗi ký tự, bắt buộc, độ dài từ 1 đến 255 ký tự).
    *   `file` (Dữ liệu tệp nhị phân, bắt buộc, dung lượng tối đa 50MB).
*   **Các quy tắc validation đầu vào**:
    *   Tiêu đề không được để trống hoặc chỉ chứa khoảng trắng.
    *   Dung lượng file tải lên phải lớn hơn 0 và nhỏ hơn hoặc bằng 50MB.
    *   Định dạng tệp thực tế phải thuộc danh sách được hỗ trợ (`application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, v.v.) bằng cách kiểm tra cấu trúc nhị phân của tệp (magic bytes) qua thư viện phân tích cấu trúc nhị phân độc lập.

### 2.2. Đặc tả Phản hồi API (API Response Design)

#### Trường hợp 201 Created (Tải lên thành công tệp mới hoặc SIS)
```json
{
  "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a10",
  "businessCode": "ORIG_A8F2E4D1",
  "title": "Báo cáo doanh thu quý 2",
  "ownerDepartmentId": "b2f63f58-5d29-45e0-8151-24db58804791",
  "fileSize": 120540,
  "hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "parentId": null,
  "creatorDepartmentId": null,
  "createdBy": "c3d9a184-7a2e-4b48-8df3-bf7b1348a27b",
  "createdAt": "2026-07-09T15:30:00",
  "updatedAt": null,
  "duplicated": false
}
```

#### Trường hợp 200 OK (Phát hiện tệp trùng lặp trong cùng phòng ban)
```json
{
  "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a10",
  "businessCode": "ORIG_A8F2E4D1",
  "title": "Báo cáo doanh thu quý 2",
  "ownerDepartmentId": "b2f63f58-5d29-45e0-8151-24db58804791",
  "fileSize": 120540,
  "hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "parentId": null,
  "creatorDepartmentId": null,
  "createdBy": "c3d9a184-7a2e-4b48-8df3-bf7b1348a27b",
  "createdAt": "2026-07-09T15:30:00",
  "updatedAt": null,
  "duplicated": true
}
```

#### Trường hợp 429 Too Many Requests (Hết thời gian chờ khóa hoặc cạn kết nối DB)
```json
{
  "errorCode": "ERR_CONCURRENT_UPLOAD",
  "message": "Yêu cầu tải lên tệp tin đang được xử lý đồng thời. Vui lòng thử lại sau."
}
```

---

## 3. Quy trình Xử lý Nghiệp vụ Chi tiết (Detailed Workflows)

### 3.1. Thuật toán Hashing & Lưu tệp tạm thời 1-pass (Single-Pass Storage & Hashing)
Nhằm tránh việc đọc file hai lần gây lãng phí tài nguyên I/O đĩa cứng, hệ thống áp dụng luồng xử lý như sau:
1.  API tiếp nhận tệp tin dưới dạng luồng dữ liệu (`InputStream`).
2.  Khởi tạo bộ xử lý tính toán mã băm mật mã (SHA-256) và luồng ghi tệp tạm thời.
3.  Vừa đọc luồng dữ liệu đầu vào vừa ghi trực tiếp vào một tệp tạm thời trong thư mục `/tmp/eap-uploads`, đồng thời cập nhật trạng thái của bộ tính toán mã băm cho mỗi khối đệm dữ liệu (kích thước đệm khuyến nghị: 8KB).
4.  Khi luồng dữ liệu đọc hết, hoàn tất quá trình ghi tệp tạm thời và kết xuất chuỗi Hex đại diện cho mã băm SHA-256.
5.  Phương pháp này đảm bảo chỉ đọc dữ liệu nhị phân của tệp tin đúng một lần duy nhất.

### 3.2. Cấu trúc Phân tách Giao dịch & Tối ưu hóa Khóa
Để giải phóng khóa cố vấn PostgreSQL ngay lập tức và tránh chiếm dụng tài nguyên kết nối lâu, logic nghiệp vụ được phân tách thành hai tầng phương thức:

#### Phương thức chính (Ngoài Giao dịch - Non-Transactional)
1.  Nhận tiêu đề, tệp tin và thông tin người dùng.
2.  Thực hiện validation vai trò và định dạng tệp tin.
3.  Gọi bộ xử lý "Hashing & Lưu tệp tạm thời 1-pass" để lấy mã băm `hash` và tệp tạm `tempFile`.
4.  **Bảo vệ tài nguyên bằng khối Try-Finally**:
    *   Để đảm bảo không bị rò rỉ đĩa cứng khi có sự cố, toàn bộ luồng xử lý từ bước này phải nằm trong khối `try-finally`.
5.  **Fast-Check (Ngoài Giao dịch)**: Thực thi truy vấn gộp cơ sở dữ liệu lọc theo `hash`.
    *   Nếu kết quả cho thấy đã có bản ghi hoạt động trong cùng phòng ban hiện tại: Đặt cờ hoàn thành, nhảy đến khối `finally` để xóa tệp tạm `tempFile` và trả về phản hồi trùng lặp (HTTP 200 OK + `duplicated: true`).
6.  Nếu chưa có bản ghi trùng lặp trong phòng ban, chuyển tiếp yêu cầu đến phương thức giao dịch để thực hiện ghi nhận.

#### Phương thức Giao dịch (Trong Giao dịch - Transactional)
1.  Mở giao dịch (Transaction) cơ sở dữ liệu mới.
2.  Yêu cầu khóa cố vấn giao dịch PostgreSQL (Advisory Lock) bằng câu lệnh băm chuỗi tích hợp của database:
    `SELECT pg_advisory_xact_lock(hashtext(concat(phòng_ban, ':', mã_băm)))`
3.  **Double-Check (Trong Giao dịch)**: Thực thi lại câu truy vấn gộp cơ sở dữ liệu lọc theo `hash`.
    *   Nếu phát hiện tệp đã tồn tại trong phòng ban (do luồng khác vừa ghi và commit trong lúc chờ khóa): Giải phóng giao dịch, báo hiệu hoàn thành để khối `finally` xóa tệp tạm `tempFile`, và trả về phản hồi trùng lặp (HTTP 200 OK + `duplicated: true`).
4.  **Xử lý lưu trữ vật lý**:
    *   Nếu kết quả truy vấn gộp cho thấy tệp vật lý đã từng tồn tại trên hệ thống (cột `oldestFileRef` không rỗng): Tái sử dụng liên kết tệp vật lý cũ nhất này, đồng thời báo hiệu để khối `finally` xóa tệp tạm `tempFile`.
    *   Nếu tệp vật lý chưa từng tồn tại: Di chuyển tệp tạm `tempFile` vào đường dẫn lưu trữ chính thức đặt tên theo mã băm (`/eap-storage/{hash}`) bằng thao tác đổi tên tệp tức thời (atomic rename ở tầng hệ điều hành).
        *   *Lưu ý xử lý xung đột liên phòng ban*: Nếu phát hiện lỗi tệp đích đã tồn tại (do phòng ban khác chạy song song vừa thực hiện rename thành công), tiến hành bắt ngoại lệ `FileAlreadyExistsException`, báo hiệu để khối `finally` tự động hủy tệp tạm `tempFile` và tái sử dụng đường dẫn tệp đích đã có.
5.  Ghi nhận bản ghi siêu dữ liệu tài liệu mới vào cơ sở dữ liệu.
6.  Kết thúc giao dịch (Commit). Database tự động thu hồi khóa cố vấn và đóng kết nối giao dịch.

#### Khối Dọn dẹp Cuối cùng (Khối Finally của Phương thức chính)
*   Nếu tệp tạm `tempFile` vẫn tồn tại trên đĩa và luồng xử lý chưa đánh dấu di chuyển tệp thành công -> Thực hiện xóa tệp tạm `tempFile` ngay lập tức để giải phóng tài nguyên đĩa.

### 3.3. Đặc tả truy vấn gộp Aggregate
Câu truy vấn gộp duy nhất thực hiện quét trên chỉ mục duy nhất `uq_documents_hash_dept` lọc theo `hash` để trả về đúng 1 dòng logic chứa 3 thông số:
1.  `hasActiveInDept`: Trạng thái boolean kiểm tra tồn tại bản ghi hoạt động (`deleted_at IS NULL`) của phòng ban đang yêu cầu.
2.  `activeDocId`: Mã định danh UUID của bản ghi hoạt động đó (nếu có).
3.  `oldestFileRef`: Đường dẫn tệp vật lý được tạo sớm nhất có cùng `hash` trên toàn hệ thống (sử dụng hàm gom mảng sắp xếp theo thời gian tạo tăng dần `created_at ASC`, **không lọc theo trạng thái xóa mềm**).

---

## 4. Quản lý Exception & Timeout (Exception & Timeout Management)

### 4.1. Lỗi Hết hạn kết nối DB (Connection Pending Timeout)
*   **Kịch bản**: stress test 150 requests đồng thời trong khi pool size là 110. 40 requests xếp hàng sau sẽ bị chặn quá 5 giây tại pool chờ kết nối DB.
*   **Xử lý**: Hệ thống bắt ngoại lệ chờ kết nối (`SQLTransientConnectionException` hoặc tương đương) tại Handler toàn cục, ghi log cảnh báo và trả về HTTP 429 Too Many Requests (JSON lỗi `ERR_CONCURRENT_UPLOAD`) thay vì ném lỗi HTTP 500.

### 4.2. Lỗi vi phạm UNIQUE constraint (Lá chắn cuối cùng)
*   **Kịch bản**: Sự cố bất ngờ khiến Double-Check bị vượt qua và xảy ra lỗi vi phạm ràng buộc unique `uq_documents_hash_dept` ở database.
*   **Xử lý**: Tầng Handler toàn cục bắt ngoại lệ `DataIntegrityViolationException`, ghi nhận cảnh báo (Mức độ WARN) kèm mã băm tệp, tự động truy vấn lại thông tin bản ghi cũ theo mã băm để phản hồi thành công (HTTP 200 OK + `duplicated: true`) cho người dùng một cách âm thầm.

---

## 5. Kế hoạch Dọn dẹp Định kỳ (Daemon Cleanup Strategy)

*   **Mục đích**: Dọn dẹp các tệp tạm thời hoặc tệp mồ côi (file vật lý tồn tại trên đĩa nhưng không có metadata trong DB) phát sinh do sự cố sập nguồn đột ngột của node ứng dụng.
*   **Tiến trình 1: Dọn dẹp tệp đệm tạm thời**:
    *   *Tần suất*: Chạy định kỳ mỗi 1 giờ (Cron job).
    *   *Logic*: Quét thư mục tạm `/tmp/eap-uploads` và xóa bỏ toàn bộ các tệp đệm tạm thời có thời gian tạo cũ hơn 1 giờ.
*   **Tiến trình 2: Dọn dẹp tệp vật lý mồ côi (Orphaned Files)**:
    *   *Tần suất*: Chạy định kỳ mỗi 24 giờ vào khung giờ thấp điểm (02:00 AM).
    *   *Logic*: Quét thư mục lưu trữ `/eap-storage`, so khớp tên tệp (hash) với danh sách `hash` đang hoạt động trong bảng `documents`. Nếu tệp vật lý trên đĩa không tồn tại bất kỳ tham chiếu nào trong cơ sở dữ liệu (kể cả bản ghi đã xóa mềm - theo quy tắc BR-4), tiến hành xóa tệp vật lý đó để giải phóng ổ đĩa.
