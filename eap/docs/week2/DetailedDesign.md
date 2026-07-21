# Tài liệu Thiết kế Chi tiết (Detailed Design) - Tuần 2
## Đặc tả Chi tiết Cơ chế Chống trùng lặp & Xử lý Tải đồng thời

---

## 1. Thiết kế Phân tách Component & Các Lớp Hỗ trợ (Component Refactoring Design)

Để tuân thủ nghiêm ngặt **Nguyên tắc Đơn trách nhiệm (Single Responsibility Principle - SRP)** và tránh cho `DocumentServiceImpl` trở thành một God Class phức tạp, hệ thống phân tách logic xử lý tải lên thành **3 Lớp Hỗ trợ độc lập (Helper Components)**:

```text
com.vccorp.eap.service
│
├── storage/
│   ├── FileStorageService.java             # Interface quản lý I/O tệp tin vật lý
│   └── impl/
│       └── FileStorageServiceImpl.java     # Implement 1-pass streaming hash SHA-256 & atomic OS rename
│
├── lock/
│   └── DocumentAdvisoryLockHandler.java   # Quản lý khóa cố vấn pg_try_advisory_lock trên JDBC connection
│
├── helper/
│   └── DocumentDeduplicationHelper.java   # Thực thi các câu SQL gộp Aggregate (Fast-Check/Double-Check)
│
└── impl/
    └── DocumentServiceImpl.java            # Class điều phối chính (Orchestrator): Validate, Try-Catch & TransactionTemplate
```

### 1.1. Lớp Quản lý Tệp tin Vật lý (`FileStorageService`)
* **Gói**: `com.vccorp.eap.service.storage`
* **Trách nhiệm**:
  1. `SinglePassStorageResult storeTempFile(InputStream inputStream)`: Đọc stream 1-pass, vừa ghi file tạm ra đĩa `/eap-storage/tmp/temp_<uuid>` vừa tính toán SHA-256 hex hash. Trả về DTO chứa `hash`, `fileSize`, và đường dẫn `tempFilePath`.
  2. `String moveTempToPermanent(Path tempFilePath, String hash)`: Di chuyển file tạm sang vị trí lưu trữ chính thức `/eap-storage/{hash}` bằng thao tác đổi tên nguyên tử (`Files.move` - atomic rename ở tầng OS). Tự động xử lý ngoại lệ `FileAlreadyExistsException` nếu tệp đã được tạo bởi phòng ban khác.
  3. `void deleteTempFileQuietly(Path tempFilePath)`: Xóa tệp tạm trong khối `finally` khi xảy ra lỗi hoặc phát hiện tệp trùng lặp.

### 1.2. Lớp Quản lý Khóa Cố vấn PostgreSQL (`DocumentAdvisoryLockHandler`)
* **Gói**: `com.vccorp.eap.service.lock`
* **Trách nhiệm**:
  1. `boolean tryAcquireLock(Connection connection, UUID departmentId, String hash)`: Thực thi câu lệnh native SQL `SELECT pg_try_advisory_lock(hashtextextended(concat(departmentId, ':', hash), 0))` trực tiếp trên kết nối JDBC vật lý được ghim trong `TransactionTemplate`. Trả về `true` nếu lấy được khóa.
  2. `void releaseLock(Connection connection, UUID departmentId, String hash)`: Thực thi câu lệnh `SELECT pg_advisory_unlock(...)` trên cùng kết nối JDBC. Bọc trong khối `try-catch(Throwable t)` để ghi log WARN nếu kết nối đã bị đóng bởi CSDL.

### 1.3. Lớp Hỗ trợ Truy vấn Gộp (`DocumentDeduplicationHelper`)
* **Gói**: `com.vccorp.eap.service.helper`
* **Trách nhiệm**:
  1. `DeduplicationQueryResult executeAggregateCheck(JdbcTemplate jdbcTemplate, String hash, UUID departmentId)`: Thực thi câu SQL Query 2 (`SELECT bool_or(...), array_agg(...)`) trên kết nối JDBC, trả về POJO chứa 3 thông số: `hasActiveInDept` (boolean), `activeDocId` (UUID), và `oldestFileRef` (String).

### 1.4. Lớp Điều phối Chính (`DocumentServiceImpl`)
* **Gói**: `com.vccorp.eap.service.impl`
* **Trách nhiệm**:
  * Đóng vai trò **Orchestrator**: Tiêu thụ các bean `FileStorageService`, `DocumentAdvisoryLockHandler`, `DocumentDeduplicationHelper`, và `TransactionTemplate`.
  * Thực hiện kiểm tra quyền (RBAC), kiểm tra magic bytes qua Apache Tika, gọi Pha 1 (Fast-Check) và Pha 2 (`TransactionTemplate.execute`), trả về DTO `DocumentResponse` bọc trong `ApiResponse<T>`.

---

## 2. Thiết kế Mô hình Dữ liệu (Database Design)

### 2.1. Cấu trúc bảng `documents` (Siêu dữ liệu tài liệu)

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

### 2.2. Thiết kế Chỉ mục (Index Design)
Hệ thống sử dụng **hai chỉ mục** độc lập phục vụ cho các mục đích nghiệp vụ khác nhau:

1.  **Chỉ mục duy nhất bán phần (uq_documents_hash_dept)**:
    *   *Mục đích*: Bảo vệ tính duy nhất, chống ghi trùng lặp tài liệu hoạt động trong phòng ban.
    *   *SQL*:
        ```sql
        CREATE UNIQUE INDEX uq_documents_hash_dept ON documents(hash, owner_department_id) WHERE deleted_at IS NULL;
        ```
2.  **Chỉ mục phụ toàn phần trên mã băm (idx_documents_hash)**:
    *   *Mục đích*: Tối ưu hóa truy vấn tìm kiếm toàn cục theo `hash` để tái sử dụng tệp vật lý (SIS). Vì luồng SIS cần quét trên cả các tài liệu đã bị xóa mềm, chỉ mục này không chứa điều kiện `deleted_at IS NULL`.
    *   *SQL*:
        ```sql
        CREATE INDEX idx_documents_hash ON documents(hash);
        ```

---

## 3. Đặc tả API (API Design)

### 3.1. API POST /api/v1/original-documents
*   **Method**: `POST`
*   **Content-Type**: `multipart/form-data`
*   **Tham số Request**:
    *   `title` (Chuỗi ký tự, bắt buộc, độ dài từ 1 đến 255 ký tự).
    *   `file` (Dữ liệu tệp nhị phân, bắt buộc, dung lượng tối đa 50MB).
*   **Các quy tắc validation đầu vào**:
    *   Tiêu đề không được để trống hoặc chỉ chứa khoảng trắng.
    *   Dung lượng file tải lên phải lớn hơn 0 và nhỏ hơn hoặc bằng 50MB.
    *   Định dạng tệp thực tế phải thuộc danh sách được hỗ trợ (`application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, v.v.) bằng cách kiểm tra cấu trúc nhị phân của tệp (magic bytes) qua thư viện phân tích cấu trúc nhị phân độc lập.

### 3.2. Đặc tả Phản hồi API (API Response Design - Standardized ApiResponse Envelope)

#### Trường hợp 201 Created (Tải lên thành công tệp mới hoặc SIS)
```json
{
  "success": true,
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a10",
    "businessCode": "ORIG_00100042",
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
  },
  "error": null
}
```

#### Trường hợp 200 OK (Phát hiện tệp trùng lặp trong cùng phòng ban)
```json
{
  "success": true,
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a10",
    "businessCode": "ORIG_00100042",
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
  },
  "error": null
}
```

#### Trường hợp 429 Too Many Requests (Hết thời gian chờ khóa hoặc cạn kết nối DB)
```json
{
  "success": false,
  "data": null,
  "error": {
    "errorCode": "ERR_CONCURRENT_UPLOAD",
    "message": "Yêu cầu tải lên tệp tin đang được xử lý đồng thời. Vui lòng thử lại sau."
  }
}
```

---

## 4. Quy trình Xử lý Nghiệp vụ Chi tiết (Detailed Workflows)

### 4.1. Thuật toán Hashing & Lưu tệp tạm thời 1-pass (Single-Pass Storage & Hashing)
Nhằm tránh việc đọc file hai lần gây lãng phí tài nguyên I/O đĩa cứng, hệ thống áp dụng luồng xử lý như sau:
1.  API tiếp nhận tệp tin dưới dạng luồng dữ liệu (`InputStream`).
2.  Khởi tạo bộ xử lý tính toán mã băm mật mã (SHA-256) và luồng ghi tệp tạm thời.
3.  Vừa đọc luồng dữ liệu đầu vào vừa ghi trực tiếp vào một tệp tạm thời trong thư mục `/eap-storage/tmp` (được cấu hình cùng mount point mạng dùng chung với thư mục đích), đồng thời cập nhật trạng thái của bộ tính toán mã băm cho mỗi khối đệm dữ liệu (kích thước đệm khuyến nghị: 8KB).
4.  Khi luồng dữ liệu đọc hết, hoàn tất quá trình ghi tệp tạm thời và kết xuất chuỗi Hex đại diện cho mã băm SHA-256.
5.  Phương pháp này đảm bảo chỉ đọc dữ liệu nhị phân của tệp tin đúng một lần duy nhất.

### 4.2. Cấu trúc Phân tách Giao dịch & Tối ưu hóa Khóa
Để giải phóng khóa cố vấn PostgreSQL ngay lập tức và tránh chiếm dụng tài nguyên kết nối lâu, logic nghiệp vụ được phân tách thành hai tầng phương thức:

#### Phương thức chính (Ngoài Giao dịch - Non-Transactional)
1.  Nhận tiêu đề, tệp tin và thông tin người dùng.
2.  Thực hiện validation vai trò và định dạng tệp tin.
3.  Gọi bộ xử lý "Hashing & Lưu tệp tạm thời 1-pass" để lấy mã băm `hash` và tệp tạm `tempFile` tại `/eap-storage/tmp/temp_hash_code`.
4.  **Bảo vệ tài nguyên bằng khối Try-Finally**:
    *   Toàn bộ luồng xử lý từ bước này phải nằm trong khối `try-finally` để đảm bảo dọn dẹp file tạm và giải phóng khóa.
5.  **Fast-Check (Ngoài Giao dịch)**: Thực thi **Truy vấn gộp Aggregate (Query 2)**.
    *   Nếu `hasActiveInDept` trả về `true`: Đặt cờ hoàn thành, nhảy đến khối `finally` để xóa tệp tạm `tempFile` và thực thi **Truy vấn lấy thông tin tài liệu (Query 4)** bằng `activeDocId` để trả về phản hồi trùng lặp (HTTP 200 OK + `duplicated: true`).
6.  **Acquire Session-level Advisory Lock (Ngoài Giao dịch)**: Mượn một kết nối riêng biệt từ HikariCP, thực thi `pg_try_advisory_lock` (không chặn) theo cơ chế vòng lặp thử lại (Retry Loop) với giãn cách thời gian tăng dần (Exponential Backoff: 200ms → 400ms → 800ms → 1000ms). Tổng thời gian chờ tối đa là **10 giây** để bảo vệ SLA 15 giây.
    *   Nếu hết 10 giây mà chưa lấy được khóa: Ném ngoại lệ `ConcurrentUploadTimeoutException`, khối `finally` hủy tệp tạm, trả về **HTTP 429 Too Many Requests** với mã lỗi `ERR_CONCURRENT_UPLOAD`.
7.  **Double-Check (Ngoài Giao dịch)**: Thực thi lại **Truy vấn gộp Aggregate (Query 2)**.
    *   Nếu `hasActiveInDept` trả về `true` (do luồng khác vừa ghi thành công trong lúc chờ khóa): Nhảy đến khối `finally` để giải phóng khóa, xóa tệp tạm `tempFile`, thực thi **Truy vấn lấy thông tin tài liệu (Query 4)** bằng `activeDocId` và trả về phản hồi trùng lặp (HTTP 200 OK + `duplicated: true`).
8.  Nếu chưa có bản ghi trùng lặp trong phòng ban, chuyển tiếp yêu cầu đến phương thức giao dịch để thực hiện ghi nhận.

#### Phương thức Giao dịch (Trong Giao dịch - Transactional)
1.  Mở giao dịch (Transaction) cơ sở dữ liệu mới (Spring Boot mượn kết nối giao dịch chính từ pool).
2.  **Xử lý lưu trữ vật lý**:
    *   Nếu `oldestFileRef` không rỗng: Tái sử dụng liên kết tệp vật lý cũ nhất này, đồng thời báo hiệu để khối `finally` xóa tệp tạm `tempFile`.
    *   Nếu `oldestFileRef` rỗng (tệp vật lý chưa từng tồn tại): Di chuyển tệp tạm `tempFile` vào đường dẫn lưu trữ chính thức đặt tên theo mã băm (`/eap-storage/{hash}`) bằng thao tác đổi tên tệp tức thời (atomic rename ở tầng hệ điều hành).
        *   *Lưu ý xử lý xung đột liên phòng ban*: Nếu phát hiện lỗi tệp đích đã tồn tại (do phòng ban khác chạy song song vừa thực hiện rename thành công), tiến hành bắt ngoại lệ `FileAlreadyExistsException`, báo hiệu để khối `finally` tự động hủy tệp tạm `tempFile` và tái sử dụng đường dẫn tệp đích đã có.
3.  Thực hiện **Truy vấn thêm bản ghi mới (Query 3)** để lưu siêu dữ liệu tài liệu mới vào cơ sở dữ liệu.
4.  Kết thúc giao dịch (Commit). Trả về siêu dữ liệu vừa tạo (HTTP 201 Created + `duplicated: false`).
5.  *Xử lý lỗi vi phạm UNIQUE*: Nếu xảy ra lỗi vi phạm UNIQUE constraint (lá chắn cuối cùng) do các vấn đề bất thường, ném ngoại lệ `DataIntegrityViolationException` để tầng ngoài rollback transaction, tự động hủy file tạm và trả về HTTP 200 OK + `duplicated: true`.

#### Khối Dọn dẹp Cuối cùng (Khối Finally của Phương thức chính)
*   **Giải phóng Khóa**: Thực hiện gọi `pg_advisory_unlock` để giải phóng khóa cố vấn PostgreSQL session-level, trả kết nối giữ khóa về pool.
*   **Xóa tệp tạm**: Nếu tệp tạm `tempFile` vẫn tồn tại trên đĩa và luồng xử lý chưa đánh dấu di chuyển tệp thành công -> Thực hiện xóa tệp tạm `tempFile` ngay lập tức để giải phóng tài nguyên đĩa.

### 4.3. Sơ đồ Tuần tự Xử lý Đồng thời (Mermaid Sequence Diagram)
Dưới đây là sơ đồ mô tả chi tiết luồng xử lý khi có hai yêu cầu tải trùng tệp được gửi lên đồng thời bởi hai người dùng trong cùng một phòng ban:

```mermaid
sequenceDiagram
    autonumber
    actor UserA as Người dùng A (Luồng 1)
    actor UserB as Người dùng B (Luồng 2)
    participant API as API Controller
    participant Service as Document Service
    participant DB as PostgreSQL DB
    participant OS as Hệ điều hành (Disk)

    Note over UserA, UserB: Cùng tải lên tệp tin có cùng nội dung (hash X) trong cùng Phòng ban A
    
    par Luồng 1 (Yêu cầu 1)
        UserA->>API: POST /api/v1/original-documents (File X)
        API->>Service: handleUpload(File X)
        Note over Service: Pha 1: Ghi file tạm tại /eap-storage/tmp/temp_X<br/>Tính toán SHA-256 (hash X)
        Service->>DB: Query 2: Fast-Check (Kiểm tra xem file hoạt động đã tồn tại chưa)
        DB-->>Service: hasActiveInDept = false, oldestFileRef = null
    and Luồng 2 (Yêu cầu 2)
        UserB->>API: POST /api/v1/original-documents (File X)
        API->>Service: handleUpload(File X)
        Note over Service: Pha 1: Ghi file tạm tại /eap-storage/tmp/temp_Y<br/>Tính toán SHA-256 (hash X)
        Service->>DB: Query 2: Fast-Check (Kiểm tra xem file hoạt động đã tồn tại chưa)
        DB-->>Service: hasActiveInDept = false, oldestFileRef = null
    end

    Note over Service, DB: Pha 2: Khóa cố vấn ngoài Transaction, Giao dịch & Đảm bảo tính nhất quán
    
    critical Luồng 1 lấy được khóa trước ngoài Transaction
        Service->>DB: Query 1: Gọi pg_try_advisory_lock(lock_id_X)
        activate DB
        DB-->>Service: Khóa thành công (Lock Acquired)
        Service->>DB: Query 2: Double-Check (Ngoài Transaction)
        DB-->>Service: hasActiveInDept = false, oldestFileRef = null
        
        Service->>DB: BEGIN Transaction (Mở kết nối giao dịch mới)
        Note over Service, OS: Thao tác file vật lý (rename)
        Service->>OS: Atomic rename (/eap-storage/tmp/temp_X -> /eap-storage/X)
        OS-->>Service: Thành công
        
        Service->>DB: Query 3: INSERT bản ghi metadata (id=doc_1, hash=X, file_ref=/eap-storage/X)
        DB-->>Service: Thành công
        Service->>DB: COMMIT Transaction (Giải phóng kết nối giao dịch)
        
        Service->>DB: Gọi pg_advisory_unlock(lock_id_X) (Ngoài Transaction)
        deactivate DB
        Service-->>API: Trả về Metadata tài liệu vừa tạo
        API-->>UserA: 201 Created (duplicated: false)
    option Luồng 2 chờ khóa ngoài Transaction
        Service->>DB: Query 1: Gọi pg_try_advisory_lock(lock_id_X)
        activate DB
        Note over DB: Bị chặn hoặc chờ thử lại do Luồng 1 đang giữ khóa
        Note over DB: Sau khi Luồng 1 Unlock -> Luồng 2 lấy được khóa thành công
        DB-->>Service: Khóa thành công (Lock Acquired)
        Service->>DB: Query 2: Double-Check (Ngoài Transaction)
        DB-->>Service: hasActiveInDept = true, activeDocId = doc_1, oldestFileRef = /eap-storage/X
        
        Note over Service: Phát hiện trùng lặp nhờ Double-Check ngoài Transaction
        Service->>OS: Xóa file tạm /eap-storage/tmp/temp_Y (Giải phóng đĩa)
        OS-->>Service: Thành công
        
        Service->>DB: Gọi pg_advisory_unlock(lock_id_X) (Ngoài Transaction)
        deactivate DB
        Service->>DB: Query 4: SELECT metadata của doc_1 (lấy thông tin chi tiết tài liệu trùng lặp)
        DB-->>Service: Trả về Metadata gốc
        Service-->>API: Trả về Metadata cũ + cờ duplicated=true
        API-->>UserB: 200 OK (duplicated: true)
    end
### 4.4. Sơ đồ Hoạt động Chi tiết (Mermaid Activity Diagram)

Below is the formal Activity Diagram describing the control flow of the two-phase upload process:

```mermaid
flowchart TD
    Start([Bắt đầu: Tải lên tài liệu]) --> ValInput{Kiểm tra định dạng & dung lượng}
    ValInput -- Không hợp lệ --> Err400[Trả về Lỗi 400 Bad Request]
    ValInput -- Hợp lệ --> Hash1Pass[Ghi file tạm & Tính băm SHA-256 1-Pass]
    
    Hash1Pass --> FastCheck{Fast-Check ngoài Transaction: Tệp đã có trong Dept?}
    FastCheck -- Có --> CleanTemp1[Xóa tệp tạm] --> ReturnDup200[Trả về 200 OK + duplicated: true]
    
    FastCheck -- Chưa --> InitTx[Mở Single-Connection Transaction Block]
    InitTx --> TryLock{Thực thi pg_try_advisory_lock}
    
    TryLock -- Thất bại --> RetryCount{Đã retry quá 5 lần?}
    RetryCount -- Chưa --> SleepBackoff[Chờ 50ms-200ms] --> TryLock
    RetryCount -- Quá 5 lần --> RollbackTimeout[Rollback & Xóa file tạm] --> Return429[Trả về 429 Too Many Requests]
    
    TryLock -- Thành công --> DoubleCheck{Double-Check: Tệp đã có trong Dept?}
    DoubleCheck -- Có --> Unlock1[Call pg_advisory_unlock] --> CleanTemp2[Xóa tệp tạm] --> Commit1[Commit Transaction] --> ReturnDup200
    
    DoubleCheck -- Chưa --> CheckSIS{Check oldest_file_ref trên toàn CSDL}
    CheckSIS -- Có sẵn --> ReuseSIS[Tái sử dụng đường dẫn tệp vật lý cũ] --> CleanTemp3[Đánh dấu xóa file tạm]
    CheckSIS -- Chưa có --> AtomicMove[Atomic rename file tạm -> /eap-storage/hash]
    
    ReuseSIS --> InsertMetadata[INSERT metadata với nextval sequence doc_business_code_seq]
    AtomicMove --> InsertMetadata
    
    InsertMetadata --> Unlock2[Call pg_advisory_unlock]
    Unlock2 --> CleanTemp4[Xóa file tạm nếu chưa di chuyển]
    CleanTemp4 --> Commit2[Commit Transaction]
    Commit2 --> Return201[Trả về 201 Created + duplicated: false]
```

---

## 5. Các câu lệnh SQL Tường minh (Raw SQL Queries)

Dưới đây là đặc tả chi tiết của toàn bộ các câu lệnh SQL được sử dụng trong hệ thống:

### Query 1: Yêu cầu khóa cố vấn 64-bit không chặn (pg_try_advisory_lock)
Sử dụng hàm `hashtextextended` của PostgreSQL để tạo giá trị băm 64-bit từ chuỗi khóa kết hợp giữa mã phòng ban và mã băm tệp, giảm thiểu tỷ lệ va chạm xuống tối đa. Hàm `pg_try_advisory_lock` thực thi không chặn trên kết nối JDBC vật lý được ghim trong `TransactionTemplate`.
```sql
SELECT pg_try_advisory_lock(hashtextextended(concat(:ownerDepartmentId, ':', :hash), 0));
```

### Query 2: Truy vấn gộp Aggregate (Fast-Check và Double-Check)
Truy vấn này quét trên chỉ mục `idx_documents_hash` theo mã băm tệp để lấy ra trạng thái trùng lặp và đường dẫn file vật lý cũ nhất.
```sql
SELECT 
    bool_or(owner_department_id = :ownerDepartmentId AND deleted_at IS NULL) AS has_active_in_dept,
    (array_agg(id ORDER BY created_at ASC) FILTER (WHERE owner_department_id = :ownerDepartmentId AND deleted_at IS NULL))[1] AS active_doc_id,
    (array_agg(file_reference ORDER BY created_at ASC) FILTER (WHERE file_reference IS NOT NULL))[1] AS oldest_file_ref
FROM documents
WHERE hash = :hash;
```
*   `has_active_in_dept`: Trả về `true` nếu phòng ban đã có bản ghi hoạt động trùng khớp.
*   `active_doc_id`: UUID của bản ghi hoạt động đó (lấy bản ghi tạo sớm nhất nếu có nhiều hơn một).
*   `oldest_file_ref`: Đường dẫn tệp vật lý cũ nhất từng tồn tại trên đĩa của mã băm này để phục vụ tái sử dụng (không lọc theo `deleted_at`).

### Kết quả `EXPLAIN ANALYZE INDEX`

```text
Aggregate  (cost=8.59..8.60 rows=1 width=49)
(actual time=0.196..0.197 rows=1 loops=1)

-> Index Scan using idx_documents_hash on documents
   (cost=0.55..8.57 rows=1 width=94)
   (actual time=0.058..0.060 rows=1 loops=1)

   Index Cond: ((hash)::text = '3a69ce08cd836063712a0aa02c6206e22d678a9543c4f74fcec7c6e873574deb'::text)

Planning Time: 0.407 ms
Execution Time: 0.339 ms
```

**Nhận xét:**
- PostgreSQL sử dụng **Index Scan** trên chỉ mục `idx_documents_hash`.
- Điều kiện lọc theo `hash` được thực hiện trực tiếp trên chỉ mục (`Index Cond`).
- Chỉ có **1 bản ghi** được tìm thấy (`rows=1`).
- Thời gian lập kế hoạch (**Planning Time**) là **0.407 ms**.
- Thời gian thực thi (**Execution Time**) là **0.339 ms**, cho thấy truy vấn có hiệu năng rất cao.

### Kết quả `EXPLAIN ANALYZE SEQ`

```text
Aggregate  (cost=73574.50..73574.51 rows=1 width=49)
(actual time=277.041..279.416 rows=1 loops=1)

-> Gather
   (cost=1000.00..73574.49 rows=1 width=94)
   (actual time=276.788..279.166 rows=1 loops=1)

   Workers Planned: 2
   Workers Launched: 2

   -> Parallel Seq Scan on documents
      (cost=0.00..72574.39 rows=1 width=94)
      (actual time=261.060..270.595 rows=0 loops=3)

      Filter: ((hash)::text = '3a69ce08cd836063712a0aa02c6206e22d678a9543c4f74fcec7c6e873574deb'::text)

      Rows Removed by Filter: 666679

Planning Time: 0.314 ms
Execution Time: 279.650 ms
```

**Nhận xét:**

- PostgreSQL **không sử dụng chỉ mục** mà thực hiện **Parallel Sequential Scan** trên toàn bộ bảng `documents`.
- Truy vấn được thực thi song song với **2 worker**, tổng cộng **3 tiến trình** (1 tiến trình chính + 2 worker).
- Mỗi worker quét một phần dữ liệu và áp dụng điều kiện lọc theo `hash`.
- Mỗi worker phải loại bỏ khoảng **666.679 bản ghi**, tương đương gần **2 triệu bản ghi** được quét trên toàn bảng.
- Mặc dù có sử dụng cơ chế quét song song (`Gather`), thời gian thực thi vẫn lên tới **279.650 ms**, lớn hơn rất nhiều so với trường hợp sử dụng `Index Scan`.
- Kết quả này cho thấy khi không có chỉ mục phù hợp trên cột `hash`, PostgreSQL buộc phải quét toàn bộ bảng, làm tăng đáng kể chi phí I/O và thời gian thực thi.

### Query 3: Truy vấn thêm bản ghi tài liệu mới
```sql
INSERT INTO documents (
    id, 
    business_code, 
    title, 
    file_reference, 
    file_size, 
    hash, 
    owner_department_id, 
    parent_id, 
    creator_department_id, 
    created_by, 
    created_at
) VALUES (
    :id, 
    'ORIG_' || lpad(nextval('doc_business_code_seq')::text, 8, '0'), 
    :title, 
    :fileReference, 
    :fileSize, 
    :hash, 
    :ownerDepartmentId, 
    :parentId, 
    :creatorDepartmentId, 
    :createdBy, 
    NOW()
);
```

### Query 4: Truy vấn lấy siêu dữ liệu tài liệu
Sử dụng để lấy thông tin tài liệu trùng lặp trả về cho client.
```sql
SELECT 
    id, 
    business_code, 
    title, 
    file_reference, 
    file_size, 
    hash, 
    owner_department_id, 
    parent_id, 
    creator_department_id, 
    created_by, 
    created_at, 
    updated_at
FROM documents
WHERE id = :id;
```

---

## 6. Quản lý Exception & Timeout (Exception & Timeout Management)

### 6.1. Lỗi Hết hạn kết nối DB (Connection Pending Timeout)
*   **Kịch bản**: stress test 150 requests đồng thời trong khi pool size là 110. 40 requests xếp hàng sau sẽ bị chặn quá 5 giây tại pool chờ kết nối DB.
*   **Xử lý**: Hệ thống bắt ngoại lệ chờ kết nối (`SQLTransientConnectionException` hoặc tương đương) tại Handler toàn cục, ghi log cảnh báo và trả về HTTP 429 Too Many Requests (JSON lỗi `ERR_CONCURRENT_UPLOAD`) thay vì ném lỗi HTTP 500.

### 6.2. Lỗi vi phạm UNIQUE constraint (Lá chắn cuối cùng)
*   **Kịch bản**: Sự cố bất ngờ khiến Double-Check bị vượt qua và xảy ra lỗi vi phạm ràng buộc unique `uq_documents_hash_dept` ở database.
*   **Xử lý**: Tầng Handler toàn cục bắt ngoại lệ `DataIntegrityViolationException`, ghi nhận cảnh báo (Mức độ WARN) kèm mã băm tệp, tự động truy vấn lại thông tin bản ghi cũ theo mã băm để phản hồi thành công (HTTP 200 OK + `duplicated: true`) cho người dùng một cách âm thầm.

### 6.3. Lỗi Hết thời gian chờ Khóa cố vấn (Lock Acquisition Timeout)
*   **Kịch bản**: Dưới tải cực cao (ví dụ: 150 request tải cùng 1 file), các request xếp hàng chờ `pg_try_advisory_lock` vượt quá tổng thời gian retry 10 giây mà vẫn chưa lấy được khóa.
*   **Phân biệt với 6.1**: Lỗi này xảy ra ở tầng **khóa cố vấn** (chờ lock), không phải tầng **kết nối DB** (chờ pool). Request vẫn có kết nối DB nhưng không thể giành được khóa nghiệp vụ.
*   **Xử lý**: Ném ngoại lệ `ConcurrentUploadTimeoutException` tại tầng Service. Handler toàn cục bắt ngoại lệ này, hủy tệp tạm và trả về **HTTP 429 Too Many Requests** với body JSON `{ "errorCode": "ERR_CONCURRENT_UPLOAD", "message": "Hệ thống đang xử lý quá nhiều yêu cầu tải lên cùng tệp. Vui lòng thử lại sau." }`.

### 6.4. Lỗi Kết nối DB bị thu hồi (DB Connection Recycle / Network Timeout)
*   **Kịch bản**: Tường lửa hoặc PostgreSQL tự động thu hồi kết nối đang giữ khóa cố vấn session-level do kết nối bị idle quá lâu trong khi khóa đang được giữ.
*   **Cơ chế tự phục hồi của PostgreSQL**: Khi kết nối TCP bị đứt, PostgreSQL **tự động giải phóng** toàn bộ khóa cố vấn session-level liên kết với session đó – không để lại rò rỉ.
*   **Xử lý tại ứng dụng**: Khi luồng Java cố gắng thực thi `pg_advisory_unlock` trong khối `finally`, nó sẽ nhận được lỗi kết nối. Khối `finally` **bắt buộc phải bắt `Throwable`** (không chỉ `Exception`) để đảm bảo lỗi unlock không đè nát kết quả trả về của request (transaction đã commit thành công từ trước). Mẫu code bắt buộc:
    ```java
    } finally {
        try {
            advisoryLockConnection.execute("SELECT pg_advisory_unlock(...)");
        } catch (Throwable t) {
            log.warn("Không thể giải phóng advisory lock, PostgreSQL tự dọn khi mất kết nối", t);
        }
        // Tiếp tục dọn tệp tạm...
    }
    ```
*   **Phòng ngừa rò rỉ khóa tích lũy**: Cấu hình HikariCP `max-lifetime = 10–15 phút` để định kỳ làm mới kết nối vật lý, giải phóng các khóa bị rò rỉ ngầm (nếu có).

### 6.5. Sự cố Sập Ứng dụng (Application Crash / Node Restart)
*   **Kịch bản**: Node Spring Boot bị sập nguồn đột ngột (kill -9, mất điện) khi đang xử lý và giữ khóa cố vấn session-level.
*   **Cơ chế tự phục hồi của PostgreSQL**: Khi kết nối TCP vật lý đến DB bị đứt đột ngột, PostgreSQL phát hiện qua cơ chế **TCP keepalive** và tự động đóng session. Toàn bộ khóa cố vấn session-level gắn với session đó được **giải phóng tức thì** – các request đang chờ sẽ lần lượt giành được khóa và tiếp tục xử lý bình thường.
*   **Không cần xử lý tại ứng dụng**: Đây là đảm bảo cứng ở tầng PostgreSQL, ứng dụng không cần thêm logic phục hồi đặc biệt. Tuy nhiên, cần đảm bảo **không tắt TCP keepalive** trong cấu hình JDBC connection pool.

---

## 7. Phân tích & Đánh giá Giải pháp Khóa (Locking Mechanism Technical Evaluation)

Đối với bài toán kiểm soát tải đồng thời và chống trùng lặp dữ liệu, hệ thống tiến hành phân tích so sánh 3 phương án kỹ thuật:

### 7.1. So sánh các Phương án Khóa

| Tiêu chí | Option 1: Native SQL via `JdbcTemplate` trong `TransactionTemplate` (Đề xuất) | Option 2: Spring Data JPA `@Query(nativeQuery = true)` | Option 3: JPA `@Lock(PESSIMISTIC_WRITE)` |
| :--- | :--- | :--- | :--- |
| **Cơ chế hoạt động** | Gọi `pg_try_advisory_lock` trực tiếp trên kết nối JDBC được ghim bởi `TransactionTemplate`. | Gọi native query qua Proxy đại lý của Spring Data JPA. | Gọi `SELECT ... FOR UPDATE` trên thực thể JPA. |
| **Khóa dòng chưa tồn tại** | **TỐT**: Khóa trên giá trị băm logic (64-bit BigInt), không phụ thuộc bản ghi DB. | **TỐT**: Khóa trên giá trị băm logic. | **KHÔNG THỂ**: `FOR UPDATE` đòi hỏi bản ghi phải tồn tại trước dưới DB. Không tác dụng với file mới. |
| **Đảm bảo Pinned Connection** | **TUYỆT ĐỐI 100%**: `TransactionTemplate` giữ chặt đúng 1 kết nối JDBC cho toàn chuỗi thao tác. | **RỦI RO**: Rò rỉ kết nối nếu không quản lý chặt chẽ transaction boundary của Proxy. | **TRUNG BÌNH**: Phụ thuộc vào transaction quản lý bởi Hibernate. |
| **Hiệu năng & Overhead** | **TỐI ƯU**: Trực thi SQL phẳng, 0ms proxy overhead. | **TRUNG BÌNH**: Phải đi qua proxy layer của Spring Data. | **THẤP**: Phải load entity state vào Hibernate Persistence Context. |
| **Khả năng giải phóng sớm** | **TỐT**: Trả về boolean ngay (`pg_try_advisory_lock`), fail-fast HTTP 429 nếu hết retry. | **TRUNG BÌNH**: Khó kiểm soát retry loop ở tầng DAO. | **KÉM**: Giữ lock cho đến tận khi kết thúc transaction commit. |

### 7.2. Kết luận Giải pháp Đề xuất

1. **Từ bỏ Option 3 (JPA `@Lock(PESSIMISTIC_WRITE)`):** Do `PESSIMISTIC_WRITE` dựa trên `FOR UPDATE` cấp dòng (row-level lock), nó hoàn toàn không thể khóa được các yêu cầu tải lên tệp tin mới (chưa có bản ghi nào trong database).
2. **Lựa chọn Option 1 (`JdbcTemplate` + `TransactionTemplate` Pinned Connection):**
   * Đây là phương án tối ưu nhất về mặt kiến trúc.
   * `JdbcTemplate` kết hợp `TransactionTemplate` đảm bảo chuỗi thao tác `pg_try_advisory_lock` -> `Double-Check` -> `Atomic Rename` -> `INSERT` -> `pg_advisory_unlock` được chạy nguyên tử trên đúng **1 kết nối JDBC vật lý duy nhất**.
   * Đảm bảo tính Idempotency, không gây connection pool starvation, và bảo vệ an toàn cho SLA phản hồi của hệ thống.
