# Detailed Design Document
**Dự án:** VCC Enterprise Archive Platform (VCC-EAP)  
**Tài liệu:** Thiết kế Chi tiết Hệ thống (Detailed Solution Design)  
**Giai đoạn:** Giai đoạn 1 (Week 1 Release)  
**Tác giả:** Trưởng nhóm Kiến trúc Giải pháp (Senior Solution Architect)  

---

## 1. Package Structure (Cấu trúc Thư mục Mã nguồn Tinh gọn & Chuẩn hóa)

Hệ thống Spring Boot được tổ chức theo cấu trúc phân tầng thực tế tinh gọn (Lean Layered Architecture), tập trung các lớp dùng chung và cơ cấu xử lý lỗi vào gói `common`, cô lập bảo mật trong tầng hạ tầng và phân tách rõ ràng trách nhiệm giữa các gói:

```
com.vccorp.eap
│
├── common                          # Các thành phần dùng chung toàn hệ thống
│   ├── response
│   │   ├── ApiResponse.java        # Success Response Envelope
│   │   └── ErrorResponse.java      # Error & Validation Response Envelopes
│   ├── exception
│   │   └── BusinessException.java  # Base Exception cho các lỗi nghiệp vụ
│   ├── error
│   │   ├── ErrorCode.java          # Model mã lỗi tập trung (statusCode, code, message)
│   │   └── GlobalExceptionHandler.java # Bộ xử lý ngoại lệ tập trung (@ControllerAdvice)
│   └── util
│       └── HashUtils.java          # Tiện ích mã hóa SHA-256 dùng chung
│
├── enums                           # Các Enum nghiệp vụ tĩnh dùng chung
│   └── Role.java                   # Vai trò người dùng (SYSTEM_ADMIN, BOARD_MEMBER, ...)
│
├── model                           # Tầng Thực thể dữ liệu (Domain Entities)
│   ├── Department.java
│   ├── User.java
│   ├── OriginalDocument.java       # Thực thể tài liệu gốc với logic tự kiểm tra
│   ├── AliasDocument.java          # Thực thể liên kết tài liệu bất biến
│   └── AuditLog.java               # Thực thể nhật ký thao tác bất biến
│
├── controller                      # Tầng Presentation (REST API Controllers)
│   ├── OriginalDocumentController.java
│   ├── AliasDocumentController.java
│   ├── DepartmentController.java
│   ├── UserController.java
│   └── AuditLogController.java
│
├── dto                             # Tầng DTOs (Data Transfer Objects)
│   ├── request
│   │   ├── CreateOriginalDocumentRequest.java
│   │   ├── CreateAliasRequest.java
│   │   ├── CreateDepartmentRequest.java
│   │   └── CreateUserRequest.java
│   └── response
│       ├── DocumentMetadataResponse.java
│       ├── AliasResolutionResponse.java
│       ├── DepartmentResponse.java
│       └── UserResponse.java
│
├── service                         # Tầng Nghiệp vụ (Business Logic)
│   ├── OriginalDocumentService.java
│   ├── AliasDocumentService.java
│   ├── UserService.java
│   ├── DepartmentService.java
│   ├── AuditLogService.java
│   └── ResourceOwnershipValidator.java # Trình xác thực quyền sở hữu phòng ban
│
├── repository                      # Tầng Truy cập Dữ liệu (Spring Data JPA Repositories)
│   ├── OriginalDocumentRepository.java
│   ├── AliasDocumentRepository.java
│   ├── UserRepository.java
│   ├── DepartmentRepository.java
│   └── AuditLogRepository.java
│
└── infrastructure                  # Tầng Hạ tầng kỹ thuật (Framework Configurations & Security Adapters)
    ├── security                    # Cấu hình Spring Security & JWT
    │   ├── SecurityConfig.java
    │   ├── JwtAuthenticationFilter.java
    │   ├── JwtTokenProvider.java
    │   ├── UserPrincipal.java
    │   └── SecurityContextHelper.java  # Tích hợp lấy thông tin User/Department hiện tại
    └── config                      # Cấu hình Hibernate Filter Auto-Injection Interceptor
        └── HibernateFilterInterceptor.java
```

---

## 2. Module Design (Thiết kế các Module)

### 2.1. Module Quản lý Định danh & Vai trò (IAM Module)
*   **Trách nhiệm**: Quản lý thông tin phòng ban, tài khoản và phân quyền vai trò.
*   **Dependencies**: Không phụ thuộc vào module khác.

### 2.2. Module Quản lý Tài liệu (Document Module)
*   **Trách nhiệm**: Tải lên tài liệu gốc, tạo Alias, giải quyết liên kết và xóa mềm lan truyền.
*   **Dependencies**: Phụ thuộc vào IAM Module để xác định phòng ban của người dùng hiện tại.

### 2.3. Module Nhật ký Giám sát (Audit Module)
*   **Trách nhiệm**: Ghi nhận bất biến lịch sử thao tác của người dùng vào bảng `audit_logs`.
*   **Dependencies**: Phụ thuộc vào IAM Module.

---

## 3. Entity Design & Database Schema (Thiết kế Thực thể và Cơ sở Dữ liệu Chi tiết)

Mọi bảng dữ liệu được cấu hình ràng buộc chặt chẽ, tối ưu hóa bằng chỉ mục và thực thi quy tắc toàn vẹn nghiệp vụ ở mức độ CSDL:

### 3.1. Thực thể `Department` (Bảng `departments`)
*   `id`: UUID (Primary Key, NOT NULL)
*   `code`: String (VARCHAR(50), Unique Index, NOT NULL) - Ví dụ: "BOARD", "HR", "FINANCE"
*   `name`: String (VARCHAR(100), NOT NULL)

### 3.2. Thực thể `User` (Bảng `users`)
*   `id`: UUID (Primary Key, NOT NULL)
*   `username`: String (VARCHAR(50), Unique Index, NOT NULL)
*   `email`: String (VARCHAR(100), Unique Index, NOT NULL)
*   `passwordHash`: String (VARCHAR(255), NOT NULL)
*   `role`: Role (VARCHAR(50), NOT NULL, Enum mapped as String)
*   `departmentId`: UUID (ForeignKey pointing to `departments(id)`, NOT NULL)
*   **Database Constraints:**
    *   `fk_user_department`: Khóa ngoại `department_id` trỏ tới `departments(id)`.
*   **Vòng đời nghiệp vụ:** Khi người dùng bị xóa (Soft Delete hoặc Hard Delete), hệ thống vẫn giữ nguyên các tài liệu gốc và Alias do người dùng này tạo ra. Cột `createdBy` của các tài liệu đó sẽ được đặt thành NULL hoặc giữ nguyên giá trị lịch sử để không ảnh hưởng đến dữ liệu phòng ban.

### 3.3. Thực thể `OriginalDocument` (Bảng `original_documents`)
*   `id`: UUID (Primary Key, NOT NULL)
*   `businessCode`: String (VARCHAR(50), Unique Index, NOT NULL) - Định dạng `ORIG_xxxxxx`
*   `title`: String (VARCHAR(255), NOT NULL)
*   `fileReference`: String (VARCHAR(500), NOT NULL) - Đường dẫn vật lý trên Object Storage
*   `fileSize`: Long (BIGINT, NOT NULL)
*   `hash`: String (VARCHAR(64), NOT NULL) - SHA-256 hash của tệp tin
*   `ownerDepartmentId`: UUID (ForeignKey pointing to `departments(id)`, NOT NULL)
*   `createdBy`: UUID (ForeignKey pointing to `users(id)`, Nullable)
*   `createdAt`: Timestamp (NOT NULL)
*   `updatedAt`: Timestamp (Nullable)
*   `deletedAt`: Timestamp (Nullable)
*   **Database Constraints & Indexes:**
    *   `fk_orig_dept`: Khóa ngoại `owner_department_id` trỏ tới `departments(id)`.
    *   `idx_orig_owner_dept`: Index trên cột `owner_department_id` để tối ưu hóa truy vấn tự động lọc của Hibernate Filter.
*   **Hibernate Filter Annotation:**
    ```java
    @FilterDef(name = "ownerDeptFilter", parameters = @ParamDef(name = "userDeptId", type = UUID.class))
    @Filter(name = "ownerDeptFilter", condition = "owner_department_id = :userDeptId AND deleted_at IS NULL")
    ```
*   **Logic Nghiệp vụ Tự bảo vệ (Domain Validation):**
    ```java
    public boolean isOwnedBy(UUID deptId) {
        return this.ownerDepartmentId.equals(deptId);
    }
    public boolean isBoardDocument(String deptCode) {
        return "BOARD".equals(deptCode);
    }
    ```

### 3.4. Thực thể `AliasDocument` (Bảng `alias_documents`)
*   `id`: UUID (Primary Key, NOT NULL)
*   `businessCode`: String (VARCHAR(50), Unique Index, NOT NULL) - Định dạng `ALIA_xxxxxx`
*   `title`: String (VARCHAR(255), NOT NULL)
*   `originalDocumentId`: UUID (ForeignKey pointing to `original_documents(id)`, NOT NULL, updatable = false)
*   `aliasDepartmentId`: UUID (ForeignKey pointing to `departments(id)`, NOT NULL, updatable = false)
*   `createdBy`: UUID (ForeignKey pointing to `users(id)`, Nullable)
*   `createdAt`: Timestamp (NOT NULL)
*   `updatedAt`: Timestamp (Nullable)
*   `deletedAt`: Timestamp (Nullable)
*   **Database Constraints & Indexes:**
    *   `fk_alias_orig`: Khóa ngoại `original_document_id` trỏ tới `original_documents(id)`.
    *   `fk_alias_dept`: Khóa ngoại `alias_department_id` trỏ tới `departments(id)`.
    *   `idx_alias_dept`: Index trên cột `alias_department_id` để tối ưu hóa Hibernate Filter.
    *   `uq_active_alias_per_dept`: Unique Index ngăn chặn trùng lặp liên kết Alias hoạt động cho cùng một phòng ban nhận:
        ```sql
        CREATE UNIQUE INDEX uq_active_alias_per_dept 
        ON alias_documents(original_document_id, alias_department_id) 
        WHERE deleted_at IS NULL;
        ```
*   **Hibernate Filter Annotation:**
    ```java
    @FilterDef(name = "aliasDeptFilter", parameters = @ParamDef(name = "userDeptId", type = UUID.class))
    @Filter(name = "aliasDeptFilter", condition = "alias_department_id = :userDeptId AND deleted_at IS NULL")
    ```

### 3.5. Thực thể `AuditLog` (Bảng `audit_logs`)
Bảng nhật ký bất biến, chỉ hỗ trợ ghi mới (INSERT) và đọc (SELECT). Nghiêm cấm các truy vấn UPDATE hoặc DELETE.
*   `id`: UUID (Primary Key, NOT NULL)
*   `actor`: String (VARCHAR(100), NOT NULL) - Username của người thực hiện
*   `actorDepartment`: String (VARCHAR(100), NOT NULL) - Phòng ban người thực hiện
*   `action`: String (VARCHAR(50), NOT NULL) - Ví dụ: UPLOAD_DOCUMENT, CREATE_ALIAS, RESOLVE_ALIAS...
*   `targetType`: String (VARCHAR(50), NOT NULL) - Ví dụ: ORIGINAL_DOCUMENT, ALIAS_DOCUMENT
*   `targetIdentifier`: String (VARCHAR(100), NOT NULL) - Business code hoặc ID của đối tượng bị tác động
*   `outcome`: String (VARCHAR(50), NOT NULL) - SUCCESS hoặc FAILURE
*   `timestamp`: Timestamp (NOT NULL)
*   **Indexes:**
    *   `idx_audit_timestamp`: Index trên cột `timestamp` để tối ưu hóa việc xem và kiểm toán log theo thời gian.
    *   `idx_audit_actor`: Index trên cột `actor` để lọc vết theo nhân sự.

---

## 4. API Design (Thiết kế Giao diện API Chuẩn hóa)

Mọi API phản hồi trong hệ thống đều tuân thủ các hợp đồng dữ liệu thống nhất dưới đây:

### 4.1. Định dạng Phản hồi Hệ thống (Unified Response Formats)

#### 4.1.1. Phản hồi Thành công (Success Response)
```json
{
  "success": true,
  "code": "UPLOAD_SUCCESS",
  "message": "Tải lên tài liệu thành công.",
  "data": {
    "id": "03b827e8-1111-4444-8888-000000000001",
    "business_code": "ORIG_000001",
    "title": "Quy chế lương HR 2026.pdf"
  },
  "timestamp": "2026-06-25T17:30:00Z"
}
```

#### 4.1.2. Phản hồi Lỗi Nghiệp vụ (Business Error Response)
```json
{
  "success": false,
  "code": "ERR_OWNERSHIP_VIOLATION",
  "message": "Bạn không có quyền truy cập tài liệu thuộc phòng ban khác.",
  "timestamp": "2026-06-25T17:31:00Z"
}
```

#### 4.1.3. Phản hồi Lỗi Kiểm tra Dữ liệu (Validation Error Response)
```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Dữ liệu yêu cầu không hợp lệ.",
  "errors": {
    "title": "Tiêu đề tài liệu không được để trống.",
    "fileReference": "Đường dẫn file không hợp lệ."
  },
  "timestamp": "2026-06-25T17:32:00Z"
}
```

---

### 4.2. Danh sách các API Endpoints Nghiệp vụ

#### 4.2.1. Tải lên tài liệu gốc (Upload Original Document)
*   **URI**: `/api/v1/original-documents`
*   **Method**: `POST`
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER`
*   **Multipart Request Payload**: `title` (String), `file` (MultipartFile)
*   **Lưu ý**: `ownerDepartmentId` tự động trích xuất từ Security Context của phiên đăng nhập của người dùng hiện tại; API từ chối tiếp nhận tham số phòng ban từ payload.

#### 4.2.2. Danh sách tài liệu gốc (List Original Documents)
*   **URI**: `/api/v1/original-documents`
*   **Method**: `GET`
*   **Query Parameters**: `page` (Integer), `size` (Integer)
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER` / `ROLE_BOARD_MEMBER`
*   **Lưu ý**: Hibernate Filter sẽ tự động chèn điều kiện lọc `owner_department_id = currentUser.departmentId` để giới hạn dữ liệu trả về đối với nhân viên nghiệp vụ thường. Thành viên BOARD sẽ xem được toàn bộ.

#### 4.2.3. Xem chi tiết tài liệu gốc (Get Original Document Detail)
*   **URI**: `/api/v1/original-documents/{id}`
*   **Method**: `GET`
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER` / `ROLE_BOARD_MEMBER`

#### 4.2.4. Xóa tài liệu gốc (Delete Original Document)
*   **URI**: `/api/v1/original-documents/{id}`
*   **Method**: `DELETE`
*   **Authorization**: `ROLE_DEPT_MANAGER`
*   **Lưu ý**: Thực hiện xóa mềm tài liệu gốc và tự động kích hoạt xóa mềm lan truyền đến toàn bộ các Alias liên kết trỏ tới tài liệu này.

#### 4.2.5. Tạo liên kết chia sẻ Alias (Create Alias Document)
*   **URI**: `/api/v1/alias-documents`
*   **Method**: `POST`
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER` (Bất kỳ người dùng hoạt động nào thuộc phòng sở hữu tài liệu gốc đều có quyền tạo Alias chéo phòng ban).
*   **Request Payload**:
    ```json
    {
      "title": "Tài liệu chia sẻ quy chế tài chính",
      "original_document_id": "03b827e8-1111-4444-8888-000000000001",
      "alias_department_id": "890f68bd-2222-3333-4444-555555555555"
    }
    ```

#### 4.2.6. Giải quyết Alias để xem tài liệu (Resolve Alias Document)
*   **URI**: `/api/v1/alias-documents/{id}`
*   **Method**: `GET`
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER` / `ROLE_BOARD_MEMBER`
*   **Lưu ý**: Trả về siêu dữ liệu của tài liệu gốc tương ứng. Trình ghi log sẽ ghi nhận sự kiện `RESOLVE_ALIAS` thay vì truy cập tài liệu trực tiếp.

#### 4.2.7. Xóa liên kết Alias (Delete Alias Document)
*   **URI**: `/api/v1/alias-documents/{id}`
*   **Method**: `DELETE`
*   **Authorization**: `ROLE_DEPT_MANAGER` / `ROLE_EMPLOYEE` (Cả phòng gửi để thu hồi chia sẻ, hoặc phòng nhận để ẩn tài liệu).

#### 4.2.8. Danh sách Alias đã nhận (List Received Aliases)
*   **URI**: `/api/v1/alias-documents/received`
*   **Method**: `GET`
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER`
*   **Lưu ý**: Chỉ trả về các Alias được chia sẻ cho phòng ban của người dùng hiện tại (`alias_department_id = currentUser.departmentId`).

#### 4.2.9. Danh sách Alias đã chia sẻ (List Shared Aliases)
*   **URI**: `/api/v1/alias-documents/shared`
*   **Method**: `GET`
*   **Authorization**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER`
*   **Lưu ý**: Trả về danh sách Alias do phòng ban của người dùng tạo ra để chia sẻ sang phòng ban khác.

#### 4.2.10. Quản lý Người dùng (User Management - Admin)
*   `POST /api/v1/users` (Tạo tài khoản người dùng mới).
*   `GET /api/v1/users` (Lấy danh sách người dùng).
*   `PUT /api/v1/users/{id}/role-department` (Cập nhật phòng ban hoặc vai trò người dùng).
    *   **Lưu ý**: Hệ thống áp dụng token JWT ngắn hạn (15 phút). Phiên làm việc cũ của người dùng sẽ tự động hết hạn và yêu cầu đăng nhập lại sau tối đa 15 phút để cập nhật phòng ban/vai trò mới.

#### 4.2.11. Quản lý Phòng ban (Department Management - Admin)
*   `POST /api/v1/departments` (Tạo phòng ban mới).
*   `GET /api/v1/departments` (Lấy danh sách phòng ban).

---

## 5. Sequence Diagrams (Sơ đồ Tuần tự Nghiệp vụ)

### 5.1. Tạo Alias chia sẻ (Push Model)
```
User -> Controller: POST /api/v1/alias-documents (Payload)
Controller -> Service: createAlias(dto, userContext)
Service -> Validator: validateAliasCreation(dto.originalId, userContext)
Note over Validator: 1. Đọc tài liệu gốc bỏ qua bộ lọc phòng ban.<br/>2. Kiểm tra: original.isOwnedBy(userContext.deptId).<br/>3. Kiểm tra: !original.isBoardDocument().<br/>4. Kiểm tra: !aliasRepo.existsActive(originalId, targetDeptId).<br/>5. Kiểm tra: targetDeptId != userContext.deptId.
Validator --> Service: Quyền hợp lệ
Service -> Repository: Save AliasDocument Entity (bất biến originalId & aliasDeptId)
Repository -> DB: INSERT INTO alias_documents
DB --> Repository: Thành công
Service -> AuditService: log("CREATE_ALIAS", userContext, aliasId, "SUCCESS")
Service --> Controller: Success Response DTO
Controller --> User: 201 Created
```

### 5.2. Giải quyết Alias (Resolve Alias)
```
User -> Controller: GET /api/v1/alias-documents/{id}
Controller -> Service: resolveAlias(id, userContext)
Note over Service: JPA Join Query tự động đính kèm điều kiện lọc<br/>aliasDepartmentId = userContext.deptId qua Hibernate Filter.
Service -> Repository: resolveJoinOriginal(id)
Repository -> DB: SELECT * FROM alias_documents ad JOIN original_documents od...
DB --> Repository: Trả về tài liệu gốc (Hoặc lỗi 404 nếu không khớp phòng ban nhận)
Service -> AuditService: log("RESOLVE_ALIAS", userContext, id, "SUCCESS")
Service --> Controller: Consolidated Metadata Response
Controller --> User: 200 OK
```

---

## 6. Repository Design (Thiết kế Repository)

### 6.1. Câu truy vấn giải quyết Alias (JPA Join Query)
Trong `AliasDocumentRepository.java`:
```java
@Query("SELECT od FROM AliasDocument ad JOIN OriginalDocument od ON ad.originalDocumentId = od.id " +
       "WHERE ad.id = :aliasId AND ad.aliasDepartmentId = :userDeptId " +
       "AND ad.deletedAt IS NULL AND od.deletedAt IS NULL")
Optional<OriginalDocument> resolveAlias(@Param("aliasId") UUID aliasId, @Param("userDeptId") UUID userDeptId);
```

### 6.2. Bộ lọc Xóa mềm lan truyền
Trong `AliasDocumentRepository.java`:
```java
@Modifying
@Query("UPDATE AliasDocument ad SET ad.deletedAt = :deletedAt WHERE ad.originalDocumentId = :originalId " +
       "AND ad.deletedAt IS NULL")
void softDeleteAliasesByOriginalId(@Param("originalId") UUID originalId, @Param("deletedAt") Timestamp deletedAt);
```

---

## 7. Validation Rules (Quy tắc Xác thực Nghiệp vụ)

1.  **BOARD Protection Rule:** Từ chối tuyệt đối việc tạo Alias đối với bất kỳ tài liệu gốc nào thuộc phòng ban sở hữu là `BOARD`.
2.  **Ownership-based Alias Rule:** Bất kỳ nhân sự hoạt động nào thuộc phòng ban sở hữu tài liệu gốc (`currentUser.departmentId == originalDocument.ownerDepartmentId`) đều được tạo Alias.
3.  **Anti-Chaining Rule:** Cấm Alias nối tiếp. ID tài liệu gốc truyền vào tạo Alias bắt buộc phải tồn tại trong bảng `original_documents`, không được trỏ vào bảng `alias_documents`.
4.  **No Self-Sharing Rule:** Phòng ban nhận Alias bắt buộc phải khác với phòng ban sở hữu tài liệu gốc.
5.  **Unique Alias Limit Rule:** Mỗi phòng ban nhận chỉ được phép nhận duy nhất 1 Alias từ cùng một tài liệu gốc đang hoạt động (Được kiểm tra bởi mã logic và thực thi cứng bởi Unique Index ở cấp DB).
6.  **File Upload Whitelist Rule:** Hệ thống chỉ cho phép tải lên các tệp tin có định dạng: `.pdf`, `.docx`, `.xlsx`, `.pptx`. Bất kỳ định dạng nào khác sẽ bị từ chối với mã lỗi `VALIDATION_ERROR`.
7.  **File MIME Type Validation Rule:** Hệ thống thực hiện đối soát tính chính xác của file bằng cách kết hợp kiểm tra phần mở rộng (extension) của tệp tin và thuộc tính Content-Type (MIME) được gửi lên.
8.  **File Size Limit Rule:** Giới hạn dung lượng tệp tin tải lên tối đa là **50MB** (thực thi thông qua cấu hình Spring Boot Multipart và kiểm tra kích thước file tại tầng Service).

---

## 8. Error Handling (Xử lý Ngoại lệ tập trung & ErrorCode Mappings)

Hệ thống quản lý lỗi tập trung thông qua lớp `GlobalExceptionHandler` đặt tại gói `common.error`. Mã lỗi nghiệp vụ được định nghĩa tĩnh trong Enum `ErrorCode` thuộc gói `common.error`.

### 8.1. Mô hình `ErrorCode` Tập trung
Mỗi lỗi được định nghĩa rõ: `statusCode` (HTTP Status Code), `code` (Mã lỗi nghiệp vụ), và `message` (Thông điệp hiển thị).

### 8.2. Mẫu Ánh xạ Lỗi (Error Exception Mapping Table)

| Mã lỗi Nghiệp vụ (`code`) | HTTP Status | Mẫu Phản hồi lỗi API | Nguyên nhân kích hoạt |
| :--- | :--- | :--- | :--- |
| `ERR_UNAUTHENTICATED` | 401 Unauthorized | `{"success": false, "code": "ERR_UNAUTHENTICATED", "message": "Phiên đăng nhập hết hạn hoặc không hợp lệ."}` | JWT token không hợp lệ, hết hạn, hoặc đã bị vô hiệu hóa do thay đổi thông tin User. |
| `ERR_OWNERSHIP_VIOLATION`| 404 Not Found | `{"success": false, "code": "ERR_DOCUMENT_NOT_FOUND", "message": "Tài liệu yêu cầu không tồn tại."}` | Người dùng truy cập tài liệu chéo phòng ban không thuộc sở hữu (che giấu sự tồn tại của tài liệu). |
| `ERR_FORBIDDEN_ROLE`  | 403 Forbidden | `{"success": false, "code": "ERR_FORBIDDEN_ROLE", "message": "Bạn không có quyền thực hiện hành động này."}` | Người dùng cố gắng gọi API vượt cấp vai trò chức năng (ví dụ: Employee gọi API của Admin). |
| `ERR_BOARD_PROTECTION` | 400 Bad Request| `{"success": false, "code": "ERR_BOARD_PROTECTION", "message": "Cấm tạo liên kết Alias đối với tài liệu của phòng BOARD."}` | Người dùng cố gắng tạo Alias cho tài liệu thuộc phòng BOARD. |
| `ERR_DUPLICATE_ALIAS` | 400 Bad Request| `{"success": false, "code": "ERR_DUPLICATE_ALIAS", "message": "Phòng ban nhận đã nhận một liên kết đang hoạt động từ tài liệu này."}` | Tạo trùng lặp Alias (vi phạm Unique Index). |
| `ERR_DOCUMENT_NOT_FOUND` | 404 Not Found | `{"success": false, "code": "ERR_DOCUMENT_NOT_FOUND", "message": "Tài liệu yêu cầu không tồn tại."}` | Không tìm thấy ID tài liệu gốc hoặc Alias hoạt động. |
| `VALIDATION_ERROR` | 400 Bad Request| `{"success": false, "code": "VALIDATION_ERROR", "message": "Dữ liệu không hợp lệ.", "errors": {...}}` | Vi phạm Bean Validation ở DTO đầu vào API. |
| `ERR_SYSTEM_ERROR` | 500 Internal | `{"success": false, "code": "ERR_SYSTEM_ERROR", "message": "Lỗi hệ thống bất ngờ."}` | Các lỗi kết nối cơ sở dữ liệu, tràn bộ nhớ, hoặc null pointer. |

---

## 9. Audit Design (Thiết kế Nhật ký Giám sát Bất biến)

*   **Tính bất biến và an toàn**: Tầng Repository và Service của AuditLog chỉ hỗ trợ thao tác `save()` (INSERT) và `findAll()` (SELECT). Mọi phương thức cập nhật hoặc xóa đều bị chặn từ thiết kế (không định nghĩa). Không cung cấp bất kỳ REST API nào cho phép chỉnh sửa hoặc xóa log.
*   **Cơ chế ghi log đồng bộ**: Tầng Service nghiệp vụ sau khi thực hiện giao dịch thành công sẽ gọi trực tiếp `auditService.log(...)` để ghi nhận nhật ký hoạt động của người dùng trong cùng một luồng transaction. Thiết kế này giúp tối giản hóa luồng xử lý và phù hợp với phạm vi Week 1.
*   **Hành động Audit được chuẩn hóa**:
    *   `CREATE_USER`, `UPDATE_USER` (Khi Admin cập nhật thông tin).
    *   `CREATE_DEPARTMENT`, `UPDATE_DEPARTMENT`.
    *   `UPLOAD_DOCUMENT` (Khi tải lên tài liệu gốc).
    *   `UPDATE_DOCUMENT`, `DELETE_DOCUMENT` (Xóa mềm tài liệu gốc).
    *   `CREATE_ALIAS`, `DELETE_ALIAS` (Xóa/thu hồi liên kết Alias).
    *   `RESOLVE_ALIAS` (Ghi nhận khi người dùng xem tài liệu thông qua liên kết Alias, phân biệt với việc xem tài liệu gốc trực tiếp).
    *   `LOGIN`, `LOGOUT`.

---

## 10. Detailed Business Logic (Logic Nghiệp vụ Chi tiết)

### 10.1. Lan truyền xóa mềm (Cascade Soft Delete)
Trong `OriginalDocumentService.java`:
```java
@Transactional
public void deleteOriginalDocument(UUID documentId, User currentUser) {
    // 1. Tìm tài liệu gốc (Hibernate Filter tự động chặn nếu không thuộc phòng ban)
    OriginalDocument original = originalRepo.findById(documentId)
        .orElseThrow(() -> new EntityNotFoundException("Tài liệu không tồn tại hoặc bạn không có quyền xóa."));
        
    // 2. Xác thực quyền xóa của Manager
    if (!original.isOwnedBy(currentUser.getDepartmentId())) {
        throw new BusinessException(ErrorCode.ERR_OWNERSHIP_VIOLATION);
    }
        
    Timestamp now = new Timestamp(System.currentTimeMillis());
    original.setDeletedAt(now);
    originalRepo.save(original);
    
    // 3. Lan truyền xóa mềm sang tất cả Alias liên quan
    aliasRepo.softDeleteAliasesByOriginalId(documentId, now);
}
```

### 10.2. Cập nhật quyền hạn người dùng (JWT ngắn hạn 15 phút)
Trong `UserService.java`:
```java
@Transactional
public void updateUserRoleAndDepartment(UUID userId, Role newRole, UUID newDeptId) {
    User user = userRepo.findById(userId)
        .orElseThrow(() -> new EntityNotFoundException("Người dùng không tồn tại."));
        
    user.setRole(newRole);
    user.setDepartmentId(newDeptId);
    userRepo.save(user);
    
    // Hệ thống áp dụng token JWT ngắn hạn (15 phút). Phiên làm việc cũ của người dùng
    // sẽ tự động hết hạn và yêu cầu đăng nhập lại để cập nhật quyền mới.
}
```
