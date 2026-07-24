# Tài liệu Thiết kế Kiến trúc (Architecture Design) - Tuần 2
## Giải pháp Chống trùng lặp & Xử lý Ghi đồng thời

---

## 1. Kiến trúc Tổng thể & Các tầng Component (Overall Architecture & Layers)

Hệ thống VCC-EAP áp dụng kiến trúc phân lớp (Layered Architecture), tách biệt Controller, Service, Database và Storage nhằm đảm bảo mã nguồn đơn giản, dễ bảo trì.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                          Client Layer (React)                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP Requests
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        API Controller Component                        │
│ - Tiếp nhận Multipart Request, trích xuất thông tin người dùng.        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         Service Layer Component                        │
│ - Điều phối luồng xử lý chính: Validate, Hashing, Fast-Check.          │
│ - Điều phối Transaction, Advisory Lock, Double-Check.                  │
└─────────────────────┬─────────────────────────────┬────────────────────┘
                      │                             │
                      ▼                             ▼
┌─────────────────────────────┐             ┌────────────────────────────┐
│  Database Layer Component   │             │  Physical Storage Layer    │
│ - Quản lý Advisory Lock.    │             │ - Quản lý tệp vật lý.      │
│ - Thực thi truy vấn gộp.    │             │ - Thực thi di chuyển tệp   │
│ - Đảm bảo ràng buộc UNIQUE. │             │   tạm thời nguyên tử (OS). │
└─────────────────────────────┘             └────────────────────────────┘
```

---

## 2. Luồng Dữ liệu Kiến trúc (High-Level Data Flow)

Hệ thống phân tách luồng xử lý thành hai pha độc lập nhằm tối ưu hóa kết nối cơ sở dữ liệu:

### Pha 1: Tiếp nhận và Lưu tạm thời (Ngoài Giao dịch)
1.  API tiếp nhận yêu cầu gồm tiêu đề và luồng tệp tin.
2.  Hệ thống thực hiện kiểm tra quyền hạn của người dùng.
3.  Hệ thống vừa đọc luồng tệp tin vừa tính toán mã băm SHA-256 của tệp, đồng thời ghi trực tiếp nội dung vào một tệp tạm thời trên ổ đĩa.
4.  Tính toán mã băm kết thúc, hệ thống thực hiện một truy vấn kiểm tra nhanh (Fast-Check) để xác định tệp đã tồn tại trong phòng ban chưa. Nếu phát hiện tài liệu hoạt động (deleted_at IS NULL) đã tồn tại trong cùng phòng ban, hệ thống hủy tệp tạm và trả về HTTP 409 Conflict (Duplicate Document) mà không đi vào Transaction.

### Pha 2: Khóa cố vấn, Giao dịch & Đảm bảo tính nhất quán (Single-Connection Transaction Boundary)
Toàn bộ các thao tác xin khóa cố vấn cấp giao dịch (`pg_try_advisory_xact_lock`), Double-Check, di chuyển tệp vật lý và ghi bản ghi metadata được đóng gói thực thi bên trong cùng một Database Transaction. Điều này đảm bảo advisory lock luôn có hiệu lực trong toàn bộ giao dịch và được PostgreSQL tự động giải phóng khi Transaction kết thúc (Commit hoặc Rollback).

1. Bắt đầu một Database Transaction.
2. Thử lấy khóa bằng pg_try_advisory_xact_lock với phạm vi (ownerDepartmentId, hash).
   * Nếu chưa lấy được khóa, request kết thúc Transaction hiện tại, chờ theo chính sách Retry và thử lại. Sau tối đa 5 lần retry vẫn không lấy được khóa thì trả về HTTP 429 (Too Many Requests).
3. Thực hiện truy vấn gộp (Double-Check) trên kết nối A để kiểm tra lại trạng thái tệp tin. Nếu phát hiện tệp đã được tạo bởi luồng khác: Ngắt giao dịch (rollback) để tự động giải phóng khóa cố vấn, hủy tệp tạm và trả về HTTP 409 Conflict (Duplicate Document).
4. Kiểm tra sự tồn tại của tệp vật lý trên đĩa:
    * **Đã tồn tại**: Tái sử dụng liên kết tệp vật lý có sẵn.
    * **Chưa tồn tại**: Di chuyển tệp tạm vào thư mục lưu trữ chính thức bằng thao tác đổi tên tức thời (atomic rename ở tầng hệ điều hành). Nếu gặp lỗi đụng độ do phòng ban khác vừa rename thành công (ném ra `FileAlreadyExistsException`), tiến hành bắt ngoại lệ và tự động tái sử dụng đường dẫn tệp vật lý có sẵn đó mà không gây lỗi giao dịch.
5. Thực hiện câu lệnh SQL INSERT bản ghi siêu dữ liệu tài liệu vào database (sử dụng sequence `doc_business_code_seq` để sinh `business_code` nguyên tử).
6. Hoàn tất giao dịch (Commit Transaction), PostgreSQL tự động giải phóng khóa cố vấn transaction-level và trả kết nối A về HikariCP pool.

---

## 3. Các Quyết định Kiến trúc Bổ sung (Architecture Decision Records - ADR)

### 3.1. ADR-008: Sử dụng Transaction-level Advisory Lock
*   **Context (Bối cảnh)**: Advisory Lock ở cấp Transaction chỉ có hiệu lực trong phạm vi Transaction đang thực thi. Nếu các thao tác đồng bộ không được thực hiện trong cùng một Transaction, hệ thống có thể mất tính nhất quán hoặc làm Advisory Lock không còn hiệu lực như mong muốn.
*   **Rationale (Lý do chọn)**:
    *   Sử dụng Spring `TransactionTemplate` bao bọc toàn bộ chuỗi thao tác (xin khóa giao dịch `pg_try_advisory_xact_lock`, Double-Check, di chuyển tệp, và INSERT metadata).
    *   Đảm bảo 100% các câu lệnh SQL trong luồng đồng bộ đều thực thi trên đúng **1 kết nối JDBC vật lý duy nhất (Pinned Connection)**, loại bỏ rủi ro lệch kết nối. Tất cả câu lệnh SQL trong một Transaction phải sử dụng cùng một physical JDBC Connection. Hệ thống không được tạo thêm physical connection phụ nào khác hoặc làm mất Connection Pinning (ghim kết nối) của giao dịch. Thiết kế kiến trúc này độc lập với thư viện triển khai và không ép buộc sử dụng JdbcTemplate hay DataSourceUtils.
    *   Khóa cố vấn ở cấp độ giao dịch (`pg_try_advisory_xact_lock`) được tự động giải phóng bởi PostgreSQL ngay khi giao dịch kết thúc (Commit hoặc Rollback), giúp loại bỏ hoàn toàn nguy cơ rò rỉ khóa do lỗi lập trình và không cần mở khóa thủ công.
    *   Nếu chưa lấy được khóa, request kết thúc Transaction hiện tại và thực hiện Retry ngoài Transaction. SRetry chỉ áp dụng trong trường hợp không lấy được Advisory Lock. Retry không áp dụng đối với trường hợp tài liệu đã tồn tại sau Double-Check. Khi đó request kết thúc ngay bằng HTTP 409 Conflict.

### 3.2. ADR-009: Thiết lập Phạm vi Khóa theo Phòng ban và Mã băm tệp với 64-bit Lock ID
*   **Context (Bối cảnh)**: Khóa chống trùng lặp cần đảm bảo không gây nghẽn chéo giữa các phòng ban khác nhau khi tải lên cùng một nội dung tệp tin.
*   **Rationale (Lý do chọn)**:
    *   Chuẩn hóa cách sinh Lock ID 64-bit từ PostgreSQL:
        - ownerDepartmentId: UUID.toString()
        - hash: SHA-256 lowercase
        - delimiter: ":"
        - Lock ID: `hashtextextended(concat(ownerDepartmentId, ':', hash), 0)` (trả về kiểu BIGINT 64-bit).
    *   Phạm vi khóa (Lock Scope): Chỉ thực hiện khóa cặp duy nhất `(ownerDepartmentId, hash)`. Hệ thống tuyệt đối không thực hiện khóa trên toàn bảng `documents`, không khóa trên toàn bộ phòng ban (`ownerDepartmentId`) và không khóa toàn bộ hệ thống lưu trữ (storage).
    *   Các phòng ban khác nhau tải lên cùng 1 file tại cùng 1 thời điểm sẽ có Lock ID khác nhau, hoàn toàn không bị chặn chéo nhau. Tái sử dụng tệp vật lý (SIS) được xử lý an toàn ở tầng OS qua thao tác atomic rename.

### 3.3. ADR-010: Sinh Mã Nghiệp vụ Nguyên tử bằng PostgreSQL Sequence (`doc_business_code_seq`)
*   **Context (Bối cảnh)**: Cột `business_code` có ràng buộc UNIQUE constraint (`ORIG_xxxxxx`). Việc sinh chuỗi trong RAM ứng dụng dễ gây va chạm Unique Constraint khi có 100 concurrent requests.
*   **Rationale (Lý do chọn)**:
    *   Khởi tạo PostgreSQL sequence `doc_business_code_seq` trong Flyway migration V9 (`START WITH 100000 INCREMENT BY 1`).
    *   Khi INSERT bản ghi mới, câu lệnh SQL gọi trực tiếp `nextval('doc_business_code_seq')` để ghép chuỗi `'ORIG_' || lpad(nextval('doc_business_code_seq')::text, 8, '0')`.
    *   Đảm bảo mã nghiệp vụ duy nhất 100% và nguyên tử ở tầng CSDL với tốc độ tiệm cận 0ms, loại bỏ hoàn toàn đụng độ Unique constraint under stress load.

### 3.4. ADR-011: Áp dụng cơ chế Fast-Check trước khi yêu cầu Khóa
*   **Context (Bối cảnh)**: Khi xảy ra thói quen click đúp hoặc dưới tải cao, nhiều request trùng lặp được gửi lên liên tục. Nếu tất cả đều đi thẳng vào hàng đợi khóa cố vấn, hệ thống sẽ bị tuần tự hóa toàn bộ và nghẽn tài nguyên kết nối.
*   **Rationale (Lý do chọn)**:
    *   Fast-Check giúp loại bỏ các request đến muộn khi tài liệu đã tồn tại trong cùng phòng ban. Các request này được trả ngay HTTP 409 Conflict mà không cần tham gia Transaction hoặc hàng đợi Advisory Lock.

### 3.5. ADR-012: Sử dụng Truy vấn Aggregate để gộp hai truy vấn thành một
*   **Context (Bối cảnh)**: Hệ thống cần thực hiện hai mục đích: (1) kiểm tra trùng lặp hoạt động trong phòng ban hiện tại, và (2) kiểm tra đường dẫn file vật lý cũ nhất trên toàn hệ thống để tái sử dụng (SIS).
*   **Rationale (Lý do chọn)**:
    *   Sử dụng truy vấn SQL Aggregate lọc theo `hash` trả về đúng 1 dòng phẳng duy nhất chứa 3 cột logic (`has_active_in_dept`, `active_doc_id`, `oldest_file_ref`). Giảm số lượng DB round-trips từ 2 xuống 1 ở cả hai bước Fast-Check và Double-Check.

### 3.6. ADR-013: Ghi tệp tạm ngoài Transaction và đổi tên nguyên tử trong Khóa
*   **Context (Bối cảnh)**: Thao tác ghi tệp nhị phân có dung lượng lớn (lên tới 50MB) tốn nhiều thời gian I/O đĩa.
*   **Rationale (Lý do chọn)**:
    *   Tách biệt hoàn toàn phần I/O ghi đĩa chậm ra ngoài phạm vi giao dịch (ghi file tạm tại `/eap-storage/tmp`).
    *   Trong transaction có giữ khóa cố vấn, hệ thống chỉ thực hiện thao tác di chuyển tệp tạm sang tệp đích bằng phương pháp đổi tên tức thời (`Files.move` - atomic rename ở tầng OS, <1ms), giúp giải phóng khóa cố vấn ngay lập tức.
    *   Khi chỉ tồn tại bản ghi đã xóa mềm, thao tác atomic rename chỉ thực hiện nếu file vật lý chưa tồn tại. Nếu file vật lý đã tồn tại thì bỏ qua bước rename và tái sử dụng đường dẫn hiện có.

### 3.7. ADR-014: Sử dụng đồng thời hai chỉ mục tối ưu hóa cho hai phạm vi nghiệp vụ
*   **Context (Bối cảnh)**: Ràng buộc duy nhất chống trùng lặp áp dụng theo cặp `(phòng ban, mã băm)` và lọc bản ghi hoạt động. Trong khi đó, luồng tìm kiếm file vật lý cũ nhất (SIS) cần quét trên toàn bộ bản ghi (kể cả bản ghi đã xóa mềm).
*   **Rationale (Lý do chọn)**:
    *   Sử dụng **hai chỉ mục**:
        1. `CREATE UNIQUE INDEX uq_documents_hash_dept ON documents(hash, owner_department_id) WHERE deleted_at IS NULL;` (chỉ mục bán phần cho chống trùng).
        2. `CREATE INDEX idx_documents_hash ON documents(hash);` (chỉ mục toàn phần cho Physical file lookup lookup).
    *   Đảm bảo cả hai luồng nghiệp vụ đều đạt hiệu năng tối ưu $O(\log N)$ và triệt tiêu hoàn toàn rủi ro Sequential Scan.

---

## 4. Yêu cầu Triển khai & Hạ tầng (Deployment & Infrastructure Requirements)

Để hệ thống hoạt động ổn định và đáp ứng đúng cam kết SLA, quá trình triển khai thực tế của đội ngũ DevOps phải tuân thủ các chỉ số sau:

### 4.1. Phân vùng lưu trữ dùng chung cho File tạm và File đích (Shared Partition)
*   **Yêu cầu**: Thư mục lưu trữ tạm thời (ví dụ: `/eap-storage/tmp`) và thư mục lưu trữ chính thức (`/eap-storage`) **bắt buộc phải được cấu hình trên cùng một mount point/partition mạng dùng chung** (ví dụ: cùng một ổ AWS EFS hoặc cùng một thư mục NFS).
*   **Lý do**: Trong môi trường phân tán đa node, `/eap-storage` bắt buộc là một Network File System dùng chung, còn `/tmp` cục bộ nằm trên từng node ứng dụng. Nếu lưu tệp tạm ở `/tmp` cục bộ rồi di chuyển tới `/eap-storage`, hệ điều hành sẽ phải chuyển thao tác đổi tên (rename) thành thao tác *copy rồi delete* chéo mount point, làm kéo dài thời gian giữ giao dịch và mất đi tính nguyên tử (atomic rename). Cấu hình tệp tạm nằm tại thư mục con của ổ đĩa mạng dùng chung (như `/eap-storage/tmp`) sẽ đảm bảo thao tác `Files.move` diễn ra tức thời (<1ms) và an toàn tuyệt đối.

### 4.2. Cấu hình Connection Pool & Cơ sở dữ liệu cho Tải cao
Hệ thống được thiết kế để hoạt động tối ưu dưới tải cao bằng cách phân rã các tác vụ CPU-bound và CPU-unbound:

*   **Tác vụ CPU-bound (Tính toán SHA-256 hash)**: Được giới hạn thực hiện bởi một Pool gồm **Worker Threads = 9** (tối ưu cho CPU đa nhân). Việc giới hạn này ngăn chặn tình trạng tranh chấp CPU (CPU thrashing) và đảm bảo luồng xử lý không làm nghẽn hệ thống.
*   **Tác vụ CPU-unbound (Giao dịch Cơ sở dữ liệu và Connection Pool)**: Kích thước kết nối tối đa (`maximum-pool-size` của HikariCP) được thiết lập cố định là **50** kết nối (connection-timeout = 5000ms).
