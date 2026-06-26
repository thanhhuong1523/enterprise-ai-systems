# Detailed Design Document
**Dự án:** VCC Enterprise Archive Platform (VCC-EAP)  
**Tài liệu:** Thiết kế Chi tiết Hệ thống (Detailed Solution Design)  
**Giai đoạn:** Giai đoạn 1 (Week 1 Release)  
**Tác giả:** Trưởng nhóm Kiến trúc Giải pháp (Senior Solution Architect)  

---

## 1. Thư mục Mã nguồn Tinh gọn (Package Structure)

Hệ thống Spring Boot được tổ chức theo cấu trúc phân tầng thực tế tinh gọn (Lean Layered Architecture), tập trung các lớp dùng chung và cơ cấu xử lý lỗi vào gói `common`, cô lập bảo mật trong tầng hạ tầng và phân tách rõ ràng trách nhiệm giữa các gói:

```
com.vccorp.eap
│
├── common                          # Các thành phần dùng chung toàn hệ thống
│   ├── response                    # Cấu trúc phản hồi chuẩn hóa (Success/Error Envelopes)
│   ├── exception                   # Ngoại lệ nghiệp vụ cơ sở
│   ├── error                       # Quản lý mã lỗi tập trung và xử lý ngoại lệ toàn cục
│   └── util                        # Tiện ích bổ trợ (Xử lý chuỗi, mã hóa)
│
├── enums                           # Các Enum nghiệp vụ tĩnh dùng chung (Vai trò người dùng)
│
├── model                           # Tầng Thực thể dữ liệu (Domain Entities)
│
├── controller                      # Tầng Presentation (REST API Controllers)
│
├── dto                             # Tầng DTOs (Data Transfer Objects - Request/Response DTOs)
│
├── service                         # Tầng Nghiệp vụ (Business Logic & Validators)
│
├── repository                      # Tầng Truy cập Dữ liệu (Spring Data JPA Repositories)
│
└── infrastructure                  # Tầng Hạ tầng kỹ thuật (Security, JWT, Hibernate Filters Config)
```

---

## 2. Thiết kế Module (Module Design)

### 2.1. Module Quản lý Định danh & Vai trò (IAM Module)
*   **Trách nhiệm**: Quản lý thông tin phòng ban và tài khoản người dùng tĩnh khi khởi tạo.
*   **Dependencies**: Không phụ thuộc vào module khác.

### 2.2. Module Quản lý Tài liệu (Document Module)
*   **Trách nhiệm**: Tải lên tài liệu gốc, tạo Alias, giải quyết liên kết và xóa mềm lan truyền.
*   **Dependencies**: Phụ thuộc vào IAM Module để xác định phòng ban của người dùng hiện tại và thực hiện phân quyền dựa trên sở hữu.

---

## 3. Sơ đồ Usecase và Đặc tả Chi tiết (Usecase Model & Specifications)

### 3.1. Sơ đồ Usecase Tổng quan

Sơ đồ mô tả các tác nhân chính và các ca sử dụng tương ứng trong hệ thống EAP:

```mermaid
graph LR
    subgraph Actors [Tác nhân]
        SYSTEM_ADMIN((SYSTEM_ADMIN))
        ROLE_BOARD((ROLE_BOARD))
        ROLE_EMPLOYEE((ROLE_EMPLOYEE))
        ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER))
    end

    subgraph Boundary [Hệ thống VCC-EAP]
        UC_CREATE_DEPT(Tạo phòng ban mới)
        UC_CREATE_USER(Tạo người dùng mới)
        UC_UPLOAD_DOC(Tải lên tài liệu gốc)
        UC_VIEW_DOC(Xem danh sách/chi tiết tài liệu gốc)
        UC_DELETE_DOC(Xóa mềm tài liệu gốc)
        UC_CREATE_ALIAS(Tạo liên kết Alias)
        UC_RESOLVE_ALIAS(Giải quyết Alias xem tài liệu)
        UC_DELETE_ALIAS(Xóa/Thu hồi liên kết Alias)
    end

    SYSTEM_ADMIN --> UC_CREATE_DEPT
    SYSTEM_ADMIN --> UC_CREATE_USER

    ROLE_EMPLOYEE --> UC_UPLOAD_DOC
    ROLE_EMPLOYEE --> UC_VIEW_DOC
    ROLE_EMPLOYEE --> UC_CREATE_ALIAS
    ROLE_EMPLOYEE --> UC_RESOLVE_ALIAS

    ROLE_DEPT_MANAGER --> UC_UPLOAD_DOC
    ROLE_DEPT_MANAGER --> UC_VIEW_DOC
    ROLE_DEPT_MANAGER --> UC_DELETE_DOC
    ROLE_DEPT_MANAGER --> UC_CREATE_ALIAS
    ROLE_DEPT_MANAGER --> UC_RESOLVE_ALIAS
    ROLE_DEPT_MANAGER --> UC_DELETE_ALIAS

    ROLE_BOARD --> UC_UPLOAD_DOC
    ROLE_BOARD --> UC_VIEW_DOC
    ROLE_BOARD --> UC_DELETE_DOC
```

### 3.2. Đặc tả Chi tiết và Sơ đồ từng Usecase (Usecase Specifications & Diagrams)

#### 3.2.1. UC-01: Tạo phòng ban mới (Create Department)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    SYSTEM_ADMIN((SYSTEM_ADMIN)) --> UC(Tạo phòng ban mới)
```

*   **Tác nhân**: `SYSTEM_ADMIN`
*   **Mô tả**: Admin tạo một phòng ban nghiệp vụ mới để phân nhóm người dùng nghiệp vụ.
*   **Tiền điều kiện**: Admin đã đăng nhập hệ thống thành công.
*   **Hậu điều kiện**: Phòng ban mới được tạo và lưu trữ cố định vào CSDL.
*   **Luồng sự kiện chính**:
    1. Admin gửi yêu cầu tạo phòng ban kèm theo mã phòng ban (`code`) và tên phòng ban (`name`).
    2. Hệ thống kiểm tra dữ liệu đầu vào.
    3. Hệ thống đối soát mã phòng ban trong CSDL để đảm bảo không bị trùng lặp.
    4. Hệ thống lưu thực thể phòng ban và trả về mã thành công `201 Created`.
*   **Ngoại lệ (Luồng lỗi)**:
    - Trùng lặp mã phòng ban -> Ném lỗi `VALIDATION_ERROR` (400 Bad Request).
    - Tác nhân không phải Admin -> Ném lỗi `ERR_FORBIDDEN_ROLE` (403 Forbidden).

---

#### 3.2.2. UC-02: Tạo người dùng mới (Create User)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    SYSTEM_ADMIN((SYSTEM_ADMIN)) --> UC(Tạo người dùng mới)
```

*   **Tác nhân**: `SYSTEM_ADMIN`
*   **Mô tả**: Admin khởi tạo tài khoản nhân viên mới và gán cố định vai trò cùng phòng ban.
*   **Tiền điều kiện**: Admin đã đăng nhập hệ thống thành công.
*   **Hậu điều kiện**: Tài khoản người dùng mới được tạo với mật khẩu mã hóa BCrypt.
*   **Luồng sự kiện chính**:
    1. Admin gửi yêu cầu tạo người dùng kèm thông tin: tên đăng nhập (`username`), email, mật khẩu, vai trò (`role`) và ID phòng ban (`departmentId`).
    2. Hệ thống kiểm tra hợp lệ email, mật khẩu và đối soát tính tồn tại của phòng ban được chọn.
    3. Hệ thống kiểm tra trùng lặp `username` hoặc `email` trong CSDL.
    4. Hệ thống thực hiện mã hóa mật khẩu bằng thuật toán BCrypt.
    5. Hệ thống lưu thực thể User mới và trả về mã thành công `201 Created`.
*   **Ngoại lệ (Luồng lỗi)**:
    - Vai trò là `SYSTEM_ADMIN` nhưng gửi `departmentId` khác NULL -> Hệ thống từ chối và ném lỗi `VALIDATION_ERROR` (400) vì Admin hệ thống không thuộc phòng nghiệp vụ nào.
    - Trùng username/email hoặc phòng ban không tồn tại -> Ném lỗi tương ứng.

---

#### 3.2.3. UC-03: Tải lên tài liệu gốc (Upload Original Document)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    ROLE_EMPLOYEE((ROLE_EMPLOYEE)) --> UC(Tải lên tài liệu gốc)
    ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER)) --> UC
    ROLE_BOARD((ROLE_BOARD)) --> UC
```

*   **Tác nhân**: `ROLE_EMPLOYEE`, `ROLE_DEPT_MANAGER`, `ROLE_BOARD`
*   **Mô tả**: Người dùng nghiệp vụ tải lên tài liệu gốc và siêu dữ liệu thuộc phòng ban của mình.
*   **Tiền điều kiện**: Người dùng nghiệp vụ đã đăng nhập thành công với token JWT hợp lệ.
*   **Hậu điều kiện**: Tài liệu gốc được lưu vào CSDL và tệp vật lý được lưu trữ trên Object Storage.
*   **Luồng sự kiện chính**:
    1. Người dùng gửi tiêu đề và tệp tin đính kèm thông qua HTTP Multipart Request.
    2. Hệ thống trích xuất thông tin phòng ban hiện tại (`ownerDepartmentId`) từ token JWT đã xác thực của người dùng (tuyệt đối không nhận tham số phòng ban từ payload).
    3. Hệ thống kiểm tra định dạng file (chỉ cho phép PDF, DOCX, XLSX, PPTX) và dung lượng tối đa (50MB).
    4. Hệ thống đẩy file vật lý lên Object Storage, tính toán mã băm SHA-256 của tệp và nhận về đường dẫn lưu trữ.
    5. Hệ thống khởi tạo ID có bit cuối LSB = `0` (tài liệu gốc) và lưu thông tin tài liệu vào DB.
*   **Ngoại lệ (Luồng lỗi)**:
    - File tải lên sai định dạng hoặc quá dung lượng cho phép -> Trả về lỗi `VALIDATION_ERROR` (400).

---

#### 3.2.4. UC-04: Xem danh sách/chi tiết tài liệu gốc (View Original Documents)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    ROLE_EMPLOYEE((ROLE_EMPLOYEE)) --> UC(Xem danh sách/chi tiết tài liệu gốc)
    ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER)) --> UC
    ROLE_BOARD((ROLE_BOARD)) --> UC
```

*   **Tác nhân**: `ROLE_EMPLOYEE`, `ROLE_DEPT_MANAGER`, `ROLE_BOARD`
*   **Mô tả**: Người dùng nghiệp vụ xem danh sách hoặc nội dung siêu dữ liệu chi tiết của tài liệu gốc.
*   **Tiền điều kiện**: Đăng nhập thành công và có quyền truy cập.
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ có nhân viên thuộc phòng ban sở hữu tài liệu gốc, HOẶC nhân viên thuộc phòng ban nhận được Alias liên kết đang hoạt động mới được gọi API xem tài liệu gốc đó. Người dùng thuộc phòng ban khác gọi API sẽ bị hệ thống ẩn thông tin bằng lỗi `ERR_DOCUMENT_NOT_FOUND` (404).
*   **Luồng sự kiện chính**:
    1. Người dùng gửi yêu cầu truy xuất tài liệu gốc theo ID tài liệu.
    2. Hibernate Filter tự động chèn điều kiện lọc phòng ban:
       `owner_department_id = currentUser.departmentId OR (parent_id IS NULL AND id IN (SELECT parent_id FROM documents WHERE owner_department_id = currentUser.departmentId AND parent_id IS NOT NULL AND deleted_at IS NULL))`
    3. Hệ thống truy vấn CSDL, kiểm tra tính hợp lệ và trả về thông tin tài liệu.

---

#### 3.2.5. UC-05: Xóa mềm tài liệu gốc (Cascade Soft Delete)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER)) --> UC(Xóa mềm tài liệu gốc)
    ROLE_BOARD((ROLE_BOARD)) --> UC
```

*   **Tác nhân**: `ROLE_DEPT_MANAGER` (đối với phòng ban nghiệp vụ thông thường), `ROLE_BOARD` (chỉ đối với phòng ban BOARD)
*   **Mô tả**: Trưởng phòng sở hữu hoặc thành viên BOARD thực hiện xóa mềm tài liệu gốc của bộ phận mình và thu hồi toàn bộ Alias (nếu có).
*   **Tiền điều kiện**: Đăng nhập với vai trò tương ứng và có quyền quản lý tài liệu.
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ Trưởng phòng của phòng ban thực tế sở hữu tài liệu gốc, hoặc thành viên BOARD đối với tài liệu của phòng BOARD, mới có quyền xóa tài liệu đó. Hệ thống chặn và từ chối mọi yêu cầu xóa chéo từ phòng ban khác.
*   **Luồng sự kiện chính**:
    1. Người dùng gửi yêu cầu xóa tài liệu gốc.
    2. Hệ thống khóa bản ghi tài liệu bằng khóa ghi bi quan `PESSIMISTIC_WRITE` (`FOR UPDATE`) dưới DB để chặn race condition tạo Alias song song.
    3. Hệ thống kiểm tra quyền sở hữu phòng ban: `currentUser.departmentId == document.ownerDepartmentId` và tài liệu là bản gốc (LSB = 0).
    4. Hệ thống cập nhật trường `deleted_at` bằng thời điểm hiện tại và lưu tài liệu gốc.
    5. Hệ thống tự động cập nhật trường `deleted_at` cho toàn bộ Alias liên kết trỏ tới tài liệu gốc này (đối với tài liệu gốc nghiệp vụ thông thường) để vô hiệu hóa quyền truy cập của các phòng nhận.

---

#### 3.2.6. UC-06: Tạo liên kết chia sẻ Alias (Create Alias)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    ROLE_EMPLOYEE((ROLE_EMPLOYEE)) --> UC(Tạo liên kết Alias)
    ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER)) --> UC
```

*   **Tác nhân**: `ROLE_EMPLOYEE`, `ROLE_DEPT_MANAGER`
*   **Mô tả**: Người dùng tạo liên kết động (Alias) để chia sẻ tài liệu gốc của bộ phận mình sang phòng ban khác.
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ người dùng thuộc phòng ban thực sự sở hữu tài liệu gốc mới được phép chia sẻ tài liệu đó. BOARD hoàn toàn bị cấm tạo hoặc nhận Alias dưới mọi hình thức.
*   **Luồng sự kiện chính**:
    1. Người dùng gửi yêu cầu chia sẻ kèm theo ID tài liệu gốc và ID phòng ban nhận.
    2. Hệ thống khóa tài liệu gốc `FOR UPDATE`, kiểm tra người dùng thuộc phòng ban sở hữu tài liệu gốc và tài liệu gốc không thuộc phòng ban BOARD.
    3. Hệ thống kiểm tra xem phòng ban nhận đã có Alias hoạt động từ tài liệu gốc này hay chưa để tránh trùng lặp.
    4. Hệ thống sinh UUID cho Alias mới có bit cuối LSB = `1`, thiết lập `parent_id` trỏ đến tài liệu gốc, `creator_department_id` bằng phòng ban người tạo và `owner_department_id` bằng phòng ban nhận. Lưu thực thể Alias xuống DB.

---

#### 3.2.7. UC-07: Giải quyết Alias để xem tài liệu (Resolve Alias)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    ROLE_EMPLOYEE((ROLE_EMPLOYEE)) --> UC(Giải quyết Alias xem tài liệu)
    ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER)) --> UC
```

*   **Tác nhân**: `ROLE_EMPLOYEE`, `ROLE_DEPT_MANAGER`
*   **Mô tả**: Người dùng phòng nhận giải quyết Alias để xem nội dung tài liệu gốc chéo phòng ban.
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ người dùng thuộc phòng ban nhận của Alias (`alias.ownerDepartmentId == currentUser.departmentId`) mới được gọi API này. Bất kỳ phòng ban nào khác gọi API này đều bị chặn và trả về lỗi 404 để bảo mật dữ liệu tuyệt đối.
*   **Luồng sự kiện chính**:
    1. Người dùng gửi yêu cầu GET kèm ID của Alias.
    2. Bộ lọc tự động Hibernate Filter giới hạn truy vấn chỉ cho phép xem bản ghi Alias nếu phòng ban trùng khớp với phòng ban nhận (`owner_department_id`) hoặc phòng ban tạo (`creator_department_id`).
    3. Hệ thống thực hiện Join Query để truy xuất siêu dữ liệu và đường dẫn tệp tin gốc tương ứng, sau đó trả về cho người dùng.

---

#### 3.2.8. UC-08: Xóa liên kết Alias (Delete/Revoke Alias)

**Sơ đồ Usecase Chi tiết:**
```mermaid
graph LR
    ROLE_DEPT_MANAGER((ROLE_DEPT_MANAGER)) --> UC(Xóa/Thu hồi liên kết Alias)
```

*   **Tác nhân**: `ROLE_DEPT_MANAGER`
*   **Mô tả**: Trưởng phòng ban sở hữu tài liệu gốc (phòng tạo Alias) tiến hành xóa mềm để thu hồi Alias đã chia sẻ.
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ Trưởng phòng của phòng ban tạo Alias (sở hữu tài liệu gốc) mới có quyền xóa mềm liên kết này. Phòng ban nhận Alias tuyệt đối không được phép tự ý xóa liên kết đã chia sẻ cho họ.
*   **Luồng sự kiện chính**:
    1. Trưởng phòng gửi yêu cầu xóa liên kết Alias.
    2. Hệ thống tìm bản ghi Alias và truy xuất tài liệu gốc liên quan, thực hiện khóa bi quan tài liệu gốc `FOR UPDATE`.
    3. Hệ thống kiểm tra quyền sở hữu của phòng ban hiện tại đối với tài liệu gốc (`original.ownerDepartmentId == currentUser.departmentId`). Nếu không khớp, từ chối và trả về lỗi `ERR_OWNERSHIP_VIOLATION` (404).
    4. Hệ thống gán trường `deleted_at` của Alias bằng thời điểm hiện tại và lưu trữ.

---

## 4. Sơ đồ CSDL và Sơ đồ Lớp Hệ thống (Database Schema & System Class Diagrams)

### 4.1. Sơ đồ CSDL Chi tiết (Database Schema Diagram - ERD)

Sơ đồ mô tả chi tiết các bảng, các trường dữ liệu, kiểu dữ liệu, các ràng buộc khóa ngoại (Foreign Keys) và khóa chính (Primary Keys):

```mermaid
erDiagram
    DEPARTMENTS {
        uuid id PK
        varchar code UK "NOT NULL"
        varchar name "NOT NULL"
    }

    USERS {
        uuid id PK
        varchar username UK "NOT NULL"
        varchar email UK "NOT NULL"
        varchar password_hash "NOT NULL"
        varchar role "NOT NULL"
        uuid department_id FK "NOT NULL"
    }

    DOCUMENTS {
        uuid id PK "LSB=0:Original, LSB=1:Alias"
        varchar business_code UK "NOT NULL"
        varchar title "NOT NULL"
        varchar file_reference "Nullable"
        bigint file_size "Nullable"
        varchar hash "Nullable"
        uuid owner_department_id FK "NOT NULL"
        uuid parent_id FK "Nullable"
        uuid creator_department_id FK "Nullable"
        uuid created_by FK "Nullable"
        timestamp created_at "NOT NULL"
        timestamp updated_at "Nullable"
        timestamp deleted_at "Nullable"
    }

    DEPARTMENTS ||--o{ USERS : "has (department_id -> id)"
    DEPARTMENTS ||--o{ DOCUMENTS : "owns (owner_department_id -> id)"
    DEPARTMENTS ||--o{ DOCUMENTS : "created (creator_department_id -> id)"
    USERS ||--o{ DOCUMENTS : "creates (created_by -> id)"
    DOCUMENTS ||--o{ DOCUMENTS : "references (parent_id -> id)"
```

### 4.2. Sơ đồ Lớp Hệ thống Chi tiết (Detailed System Class Diagram)

Sơ đồ thể hiện toàn bộ các lớp thiết kế trong hệ thống bao gồm các lớp Thực thể (Entities), Controller tiếp nhận API, Service thực thi logic nghiệp vụ, Validator kiểm tra quyền hạn, các interface Repository tương tác dữ liệu và mối liên kết phụ thuộc giữa chúng:

```mermaid
classDiagram
    class Department {
        +UUID id
        +String code
        +String name
    }
    class User {
        +UUID id
        +String username
        +String email
        +String passwordHash
        +Role role
        +UUID departmentId
    }
    class Document {
        +UUID id
        +String businessCode
        +String title
        +String fileReference
        +Long fileSize
        +String hash
        +UUID ownerDepartmentId
        +UUID parentId
        +UUID creatorDepartmentId
        +UUID createdBy
        +Timestamp createdAt
        +Timestamp updatedAt
        +Timestamp deletedAt
        +isAlias() Boolean
        +isOriginal() Boolean
        +isOwnedBy(UUID deptId) Boolean
    }
    class Role {
        <<enumeration>>
        SYSTEM_ADMIN
        ROLE_BOARD
        ROLE_EMPLOYEE
        ROLE_DEPT_MANAGER
    }
    
    class DocumentController {
        -DocumentService documentService
        +uploadOriginalDocument(title, file) ApiResponse
        +listOriginalDocuments(page, size) ApiResponse
        +getOriginalDocumentDetail(id) ApiResponse
        +deleteOriginalDocument(id) ApiResponse
        +createAlias(CreateAliasRequest) ApiResponse
        +resolveAlias(id) ApiResponse
        +deleteAliasDocument(id) ApiResponse
        +listReceivedAliases() ApiResponse
        +listSharedAliases() ApiResponse
    }
    class UserController {
        -UserService userService
        +createUser(CreateUserRequest) ApiResponse
        +listUsers() ApiResponse
    }
    class DepartmentController {
        -DepartmentService departmentService
        +createDepartment(CreateDepartmentRequest) ApiResponse
        +listDepartments() ApiResponse
    }
    class DocumentService {
        -DocumentRepository documentRepository
        -ResourceOwnershipValidator validator
        +uploadOriginalDocument(title, file, currentUser) Document
        +listOriginalDocuments(page, size, currentUser) Page
        +getOriginalDocumentDetail(id, currentUser) Document
        +deleteOriginalDocument(id, currentUser) void
        +createAlias(CreateAliasRequest, currentUser) Document
        +resolveAlias(id, currentUser) Document
        +deleteAliasDocument(id, currentUser) void
    }
    class UserService {
        -UserRepository userRepository
        -DepartmentRepository departmentRepository
        +createUser(CreateUserRequest) User
        +listUsers() List
    }
    class DepartmentService {
        -DepartmentRepository departmentRepository
        +createDepartment(CreateDepartmentRequest) Department
        +listDepartments() List
    }
    class ResourceOwnershipValidator {
        +validateAliasCreation(originalDoc, currentUser) void
        +validateAliasResolution(aliasDoc, currentUser) void
    }
    class DocumentRepository {
        <<interface>>
        +save(Document) Document
        +findById(UUID) Optional
        +findByIdForUpdate(UUID) Optional
        +existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(parentId, ownerDeptId) boolean
        +resolveAlias(aliasId, userDeptId) Optional
        +softDeleteAliasesByOriginalId(originalId, deletedAt) void
    }
    class UserRepository {
        <<interface>>
        +save(User) User
        +findById(UUID) Optional
        +existsByUsernameOrEmail(username, email) boolean
    }
    class DepartmentRepository {
        <<interface>>
        +save(Department) Department
        +findById(UUID) Optional
        +existsByCode(code) boolean
    }
    
    User --> Role : has
    User --> Department : belongs to
    Document --> Department : owned by (ownerDepartmentId)
    Document --> Department : created by department (creatorDepartmentId)
    Document --> Document : references (parentId)
    Document --> User : created by user (createdBy)
    
    DocumentController --> DocumentService
    UserController --> UserService
    DepartmentController --> DepartmentService
    DocumentService --> DocumentRepository
    DocumentService --> ResourceOwnershipValidator
    UserService --> UserRepository
    UserService --> DepartmentRepository
    DepartmentService --> DepartmentRepository
```

---

## 5. Thiết kế Thực thể chi tiết (Entity Specifications)

### 5.1. Bảng `departments`
*   `id`: UUID (Khóa chính, tự động sinh).
*   `code`: VARCHAR(50) (Unique Index, NOT NULL) - Mã phòng ban viết hoa (HR, FINANCE, R&D, BOARD).
*   `name`: VARCHAR(100) (NOT NULL) - Tên đầy đủ của phòng ban.

### 5.2. Bảng `users`
*   `id`: UUID (Khóa chính, tự động sinh).
*   `username`: VARCHAR(50) (Unique Index, NOT NULL).
*   `email`: VARCHAR(100) (Unique Index, NOT NULL).
*   `password_hash`: VARCHAR(255) (NOT NULL).
*   `role`: VARCHAR(50) (NOT NULL) - Lưu tên Vai trò người dùng dưới dạng chuỗi (Enum).
*   `department_id`: UUID (Khóa ngoại trỏ đến `departments.id`, NOT NULL).

### 5.3. Bảng `documents`
*   `id`: UUID (Khóa chính) - Nhúng loại tài liệu vào Least Significant Bit (LSB) của UUID:
    - Tài liệu gốc (Original): LSB = `0`.
    - Tài liệu liên kết (Alias): LSB = `1`.
    - Nhận dạng loại tài liệu nhanh chóng ở RAM bằng phép toán dịch/kiểm tra bitwise trên LSB của UUID.
*   `business_code`: VARCHAR(50) (Unique Index, NOT NULL) - Mã nghiệp vụ duy nhất.
*   `title`: VARCHAR(255) (NOT NULL) - Tiêu đề hiển thị của tài liệu.
*   `file_reference`: VARCHAR(500) (Nullable) - Đường dẫn vật lý trên Object Storage (chỉ lưu ở tài liệu gốc).
*   `file_size`: BIGINT (Nullable) - Dung lượng file (chỉ lưu ở tài liệu gốc).
*   `hash`: VARCHAR(64) (Nullable) - SHA-256 hash của tệp tin (chỉ lưu ở tài liệu gốc).
*   `owner_department_id`: UUID (Khóa ngoại trỏ đến `departments.id`, NOT NULL)
    - Đối với tài liệu gốc: Phòng ban tải lên và sở hữu file.
    - Đối với Alias: Phòng ban nhận được chia sẻ.
*   `parent_id`: UUID (Khóa ngoại tự liên kết đến `documents.id`, Nullable, updatable = false) - ID tài liệu gốc tương ứng. Khóa cứng bất biến khi tạo.
*   `creator_department_id`: UUID (Khóa ngoại trỏ đến `departments.id`, Nullable, updatable = false) - ID của phòng tạo ra Alias (phòng sở hữu tài liệu gốc).
*   `created_by`: UUID (Khóa ngoại trỏ đến `users.id`, Nullable).
*   `created_at`: TIMESTAMP (NOT NULL).
*   `updated_at`: TIMESTAMP (Nullable).
*   `deleted_at`: TIMESTAMP (Nullable) - Thời điểm xóa mềm.

### 5.4. Ràng buộc và Chỉ mục CSDL (Database Constraints & Indexes)
1.  **Kiểm soát loại ID ở tầng Ứng dụng**: Khi sinh ID, hệ thống tự động thiết lập bit cuối của UUID LSB bằng 0 cho tài liệu gốc và bằng 1 cho Alias. Việc xác định loại tài liệu được thực hiện trên RAM bằng hàm dịch/kiểm tra bit (`(id.getLeastSignificantBits() & 1L) == 1L`). Không áp dụng các ràng buộc kiểm tra chuỗi hex chẵn lẻ phức tạp ở cấp CSDL.
2.  **Chỉ mục duy nhất Alias hoạt động (`uq_active_alias_per_dept`)**: Unique index trên bộ ba `(parent_id, owner_department_id)` với điều kiện `parent_id IS NOT NULL AND deleted_at IS NULL` để đảm bảo mỗi phòng ban chỉ nhận tối đa 1 Alias từ cùng một tài liệu gốc đang hoạt động.
3.  **Chỉ mục tối ưu hóa Hibernate Filter (`idx_doc_owner_dept` & `idx_doc_creator_dept`)**: Index thường trên cột `owner_department_id` và `creator_department_id` để tăng tốc độ truy vấn lọc tự động chéo phòng ban.

### 5.5. Định nghĩa Bộ lọc Tự động (Hibernate Filter)
Hệ thống kích hoạt tự động bộ lọc `deptIsolationFilter` trên thực thể `Document` đối với mọi truy vấn JPA. Bộ lọc này tự động chèn thêm điều kiện logic dưới đây vào câu lệnh SQL được sinh ra:
*   **Logic Điều kiện:**
    `owner_department_id = :userDeptId OR creator_department_id = :userDeptId OR (parent_id IS NULL AND id IN (SELECT parent_id FROM documents WHERE owner_department_id = :userDeptId AND parent_id IS NOT NULL AND deleted_at IS NULL))`

---

## 6. Thiết kế REST API (REST API Design)

Mọi REST API phản hồi trong hệ thống đều sử dụng cấu trúc đóng gói dữ liệu thống nhất (Success Envelope / Error Envelope) và trả về các mã lỗi nghiệp vụ chuẩn hóa tại gói `common.error`.

### 6.1. Danh sách các API Endpoints Nghiệp vụ

#### 6.1.1. Tải lên tài liệu gốc (Upload Original Document)
*   **URI**: `/api/v1/original-documents` | **Method**: `POST`
*   **Quyền truy cập**: Người dùng có vai trò `ROLE_EMPLOYEE`, `ROLE_DEPT_MANAGER` hoặc `ROLE_BOARD`.
*   **Dữ liệu gửi**: Tiêu đề (`title`), Tệp tin tải lên (`file`).
*   **Lưu ý bảo mật**: `ownerDepartmentId` tự động trích xuất từ token xác thực, tuyệt đối không nhận tham số từ payload.

#### 6.1.2. Danh sách tài liệu gốc (List Original Documents)
*   **URI**: `/api/v1/original-documents` | **Method**: `GET`
*   **Quyền truy cập**: Người dùng có vai trò `ROLE_EMPLOYEE`, `ROLE_DEPT_MANAGER` hoặc `ROLE_BOARD`.
*   **Cơ chế xử lý**: Bộ lọc Hibernate Filter tự động giới hạn kết quả trả về, chỉ hiển thị tài liệu gốc thuộc sở hữu của phòng ban người dùng hiện tại (`parent_id IS NULL`).

#### 6.1.3. Xem chi tiết tài liệu gốc (Get Original Document Detail)
*   **URI**: `/api/v1/original-documents/{id}` | **Method**: `GET`
*   **Quyền truy cập**: Người dùng nghiệp vụ thuộc phòng ban sở hữu hoặc phòng ban nhận Alias.
*   **Kiểm soát quyền nghiêm ngặt**: Nếu người dùng thuộc phòng ban khác cố truy cập, hệ thống lập tức chặn bằng lỗi `ERR_DOCUMENT_NOT_FOUND` (404) để che giấu tài nguyên.

#### 6.1.4. Xóa tài liệu gốc (Delete Original Document)
*   **URI**: `/api/v1/original-documents/{id}` | **Method**: `DELETE`
*   **Quyền truy cập**: Chỉ vai trò `ROLE_DEPT_MANAGER` (đối với phòng nghiệp vụ) hoặc `ROLE_BOARD` (đối với phòng BOARD) sở hữu tài liệu gốc tương ứng.
*   **Lưu ý**: Chỉ thực hiện xóa mềm (soft delete), tự động lan truyền xóa mềm đến toàn bộ Alias liên quan.

#### 6.1.5. Tạo liên kết chia sẻ Alias (Create Alias Document)
*   **URI**: `/api/v1/alias-documents` | **Method**: `POST`
*   **Quyền truy cập**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER` của phòng sở hữu tài liệu gốc.
*   **Request Payload**:
    ```json
    {
      "title": "Tài liệu chia sẻ quy chế tài chính",
      "original_document_id": "03b827e8-1111-4444-8888-000000000001",
      "alias_department_id": "890f68bd-2222-3333-4444-555555555555"
    }
    ```
*   **Lưu ý bảo mật**: Hệ thống nghiêm cấm tạo Alias cho tài liệu thuộc sở hữu của phòng ban khác (nếu vi phạm sẽ trả về lỗi `ERR_OWNERSHIP_VIOLATION` / 404 để bảo mật).

#### 6.1.6. Giải quyết Alias để xem tài liệu (Resolve Alias Document)
*   **URI**: `/api/v1/alias-documents/{id}` | **Method**: `GET`
*   **Quyền truy cập**: `ROLE_EMPLOYEE` / `ROLE_DEPT_MANAGER` thuộc phòng ban nhận Alias.
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ phòng ban nhận được chia sẻ Alias mới có quyền giải quyết Alias để xem/tải xuống tài liệu gốc liên kết. Bất kỳ tài khoản không thuộc phòng ban nhận Alias nào gọi API này đều bị chặn và trả về lỗi `ERR_DOCUMENT_NOT_FOUND` (404) để đảm bảo cô lập dữ liệu tuyệt đối.

#### 5.1.7. Xóa liên kết Alias (Delete Alias Document)
*   **URI**: `/api/v1/alias-documents/{id}` | **Method**: `DELETE`
*   **Quyền truy cập**: `ROLE_DEPT_MANAGER` của phòng ban sở hữu tài liệu gốc (phòng ban tạo Alias).
*   **Kiểm soát quyền nghiêm ngặt**: Chỉ phòng ban tạo Alias (phòng ban sở hữu tài liệu gốc) mới có quyền xóa mềm (thu hồi) Alias này. Phòng ban nhận Alias tuyệt đối không có quyền tự xóa Alias được chia sẻ sang phòng ban của mình (nếu cố tình gọi API sẽ nhận lỗi `ERR_OWNERSHIP_VIOLATION` / 404).

#### 6.1.8. Quản lý Người dùng (User Management - Admin)
*   `POST /api/v1/users` (Tạo tài khoản người dùng mới).
*   `GET /api/v1/users` (Lấy danh sách người dùng).
*   **Lưu ý bảo mật**: Vai trò và phòng ban được gán cố định khi tạo mới, hệ thống không cung cấp API cập nhật thông tin này sau khi đã tạo để tránh lỗ hổng leo thang đặc quyền.

#### 6.1.9. Quản lý Phòng ban (Department Management - Admin)
*   `POST /api/v1/departments` (Tạo phòng ban mới).
*   `GET /api/v1/departments` (Lấy danh sách phòng ban).

---

## 7. Biểu đồ Tuần tự (Sequence Diagrams)

### 7.1. Tạo Phòng ban mới (Create Department)
```mermaid
sequenceDiagram
    actor Admin as Quản trị viên (SYSTEM_ADMIN)
    participant Controller as DepartmentController
    participant Service as DepartmentService
    participant Repo as DepartmentRepository
    participant DB as PostgreSQL

    Admin->>Controller: POST /api/v1/departments (CreateDepartmentRequest)
    Controller->>Service: createDepartment(dto)
    Note over Service: Kiểm tra xem mã phòng ban (code)<br/>đã tồn tại hay chưa
    Service->>Repo: existsByCode(dto.code)
    Repo->>DB: SELECT COUNT(*) FROM departments WHERE code = ?
    DB-->>Repo: true / false
    Note over Service: Nếu tồn tại, throw VALIDATION_ERROR
    Note over Service: Tạo thực thể Department mới<br/>Sinh UUID ngẫu nhiên
    Service->>Repo: save(department)
    Repo->>DB: INSERT INTO departments
    DB-->>Repo: Thành công
    Service-->>Controller: Trả về chi tiết phòng ban mới
    Controller-->>Admin: 201 Created (Success Response DTO)
```

### 7.2. Tạo Người dùng mới (Create User)
```mermaid
sequenceDiagram
    actor Admin as Quản trị viên (SYSTEM_ADMIN)
    participant Controller as UserController
    participant Service as UserService
    participant Repo as UserRepository
    participant DB as PostgreSQL
    participant DeptRepo as DepartmentRepository

    Admin->>Controller: POST /api/v1/users (CreateUserRequest)
    Controller->>Service: createUser(dto)
    Note over Service: Kiểm tra trùng lặp username/email
    Service->>Repo: existsByUsernameOrEmail(dto.username, dto.email)
    Repo->>DB: SELECT COUNT(*) FROM users WHERE username = ? OR email = ?
    DB-->>Repo: true / false
    Note over Service: Nếu trùng, throw VALIDATION_ERROR
    Service->>DeptRepo: findById(dto.departmentId)
    DeptRepo->>DB: SELECT * FROM departments WHERE id = ?
    DB-->>DeptRepo: Phòng ban (hoặc 404 nếu không tồn tại)
    Note over Service: Nếu vai trò là SYSTEM_ADMIN,<br/> departmentId bắt buộc phải là NULL
    Note over Service: Mã hóa mật khẩu người dùng (BCrypt)
    Note over Service: Tạo thực thể User mới
    Service->>Repo: save(user)
    Repo->>DB: INSERT INTO users
    DB-->>Repo: Thành công
    Service-->>Controller: Trả về thông tin User mới
    Controller-->>Admin: 201 Created (Success Response DTO)
```

### 7.3. Tải lên tài liệu gốc (Upload Original Document)
```mermaid
sequenceDiagram
    actor User as Nhân viên nghiệp vụ
    participant Controller as DocumentController
    participant Service as DocumentService
    participant Storage as Object Storage
    participant Repo as DocumentRepository
    participant DB as PostgreSQL

    User->>Controller: POST /api/v1/original-documents (title, file)
    Controller->>Service: uploadOriginalDocument(title, file, currentUser)
    Note over Service: Trích xuất ownerDepartmentId từ currentUser
    Service->>Storage: upload(file)
    Storage-->>Service: Trả về fileReference & hash
    Note over Service: Tạo thực thể Document mới<br/>Gán UUID có LSB = 0 (tài liệu gốc)<br/>Gán ownerDepartmentId = currentUser.departmentId
    Service->>Repo: save(document)
    Repo->>DB: INSERT INTO documents
    DB-->>Repo: Thành công
    Service-->>Controller: Trả về metadata tài liệu gốc
    Controller-->>User: 201 Created (Success Response DTO)
```

### 7.4. Xóa mềm tài liệu gốc (Cascade Soft Delete)
```mermaid
sequenceDiagram
    actor User as Quản lý / BOARD sở hữu
    participant Controller as DocumentController
    participant Service as DocumentService
    participant Repo as DocumentRepository
    participant DB as PostgreSQL

    User->>Controller: DELETE /api/v1/original-documents/{id}
    Controller->>Service: deleteOriginalDocument(id, currentUser)
    Service->>Repo: findByIdForUpdate(id) (Pessimistic write lock)
    Repo->>DB: SELECT * FROM documents WHERE id = ? FOR UPDATE
    DB-->>Repo: Bản ghi tài liệu gốc
    Note over Service: Kiểm tra: isOriginal() == true<br/>Kiểm tra: ownerDepartmentId == currentUser.departmentId
    Note over Service: Cập nhật deletedAt = currentTimestamp
    Service->>Repo: save(originalDoc)
    Repo->>DB: UPDATE documents SET deleted_at = ? WHERE id = ?
    DB-->>Repo: Thành công
    Service->>Repo: softDeleteAliasesByOriginalId(id, deletedAt)
    Repo->>DB: UPDATE documents SET deleted_at = ? WHERE parent_id = ? AND deleted_at IS NULL
    DB-->>Repo: Thành công
    Service-->>Controller: Phản hồi thành công
    Controller-->>User: 200 OK
```

### 7.5. Tạo liên kết chia sẻ Alias (Create Alias)
```mermaid
sequenceDiagram
    actor User as Nhân viên / Quản lý
    participant Controller as DocumentController
    participant Service as DocumentService
    participant Repo as DocumentRepository
    participant DB as PostgreSQL
    participant Validator as ResourceOwnershipValidator

    User->>Controller: POST /api/v1/alias-documents (dto)
    Controller->>Service: createAlias(dto, currentUser)
    Service->>Repo: findByIdForUpdate(dto.originalDocumentId) (Pessimistic write lock)
    Repo->>DB: SELECT * FROM documents WHERE id = ? FOR UPDATE
    DB-->>Repo: Bản ghi tài liệu gốc
    Service->>Validator: validateAliasCreation(originalDoc, currentUser)
    Note over Validator: Kiểm tra: originalDoc.isOriginal()<br/>Kiểm tra: originalDoc.ownerDepartmentId == currentUser.departmentId<br/>Kiểm tra: originalDoc.ownerDepartmentId != dto.aliasDepartmentId<br/>Kiểm tra: dto.aliasDepartmentId != BOARD
    Validator-->>Service: Hợp lệ
    Service->>Repo: existsByParentIdAndOwnerDepartmentIdAndDeletedAtIsNull(...)
    Repo->>DB: SELECT COUNT(*) FROM documents WHERE parent_id = ? AND owner_department_id = ? AND deleted_at IS NULL
    DB-->>Repo: true / false
    Note over Service: Nếu trùng lặp, throw ERR_DUPLICATE_ALIAS
    Note over Service: Sinh UUID cho Alias mới có LSB = 1<br/>Gán parentId = originalDoc.id<br/>Gán ownerDepartmentId = dto.aliasDepartmentId<br/>Gán creatorDepartmentId = currentUser.departmentId
    Service->>Repo: save(aliasDoc)
    Repo->>DB: INSERT INTO documents
    DB-->>Repo: Thành công
    Service-->>Controller: Phản hồi thành công
    Controller-->>User: 201 Created
```

### 7.6. Giải quyết Alias để xem tài liệu (Resolve Alias)
```mermaid
sequenceDiagram
    actor User as Nhân viên phòng nhận Alias
    participant Controller as DocumentController
    participant Service as DocumentService
    participant Repo as DocumentRepository
    participant DB as PostgreSQL

    User->>Controller: GET /api/v1/alias-documents/{id}
    Controller->>Service: resolveAlias(id, currentUser)
    Note over Service: JPA Join Query tự động áp dụng Hibernate Filter<br/>để cô lập dữ liệu theo phòng ban
    Service->>Repo: resolveAlias(id, currentUser.departmentId)
    Repo->>DB: SELECT od.* FROM documents ad JOIN documents od ON ad.parent_id = od.id WHERE ad.id = ? AND ad.owner_department_id = ? AND ad.deleted_at IS NULL AND od.deleted_at IS NULL
    DB-->>Repo: Bản ghi tài liệu gốc
    Note over Service: Trả về siêu dữ liệu và link file vật lý
    Service-->>Controller: Trả về chi tiết tài liệu gốc
    Controller-->>User: 200 OK
```

### 7.7. Xóa liên kết Alias (Delete Alias)
```mermaid
sequenceDiagram
    actor User as Quản lý phòng ban sở hữu
    participant Controller as DocumentController
    participant Service as DocumentService
    participant Repo as DocumentRepository
    participant DB as PostgreSQL

    User->>Controller: DELETE /api/v1/alias-documents/{id}
    Controller->>Service: deleteAliasDocument(id, currentUser)
    Service->>Repo: findById(id)
    Repo->>DB: SELECT * FROM documents WHERE id = ?
    DB-->>Repo: Bản ghi Alias
    Note over Service: Kiểm tra: isAlias() == true
    Service->>Repo: findByIdForUpdate(alias.parentId)
    Repo->>DB: SELECT * FROM documents WHERE id = ? FOR UPDATE
    DB-->>Repo: Bản ghi tài liệu gốc
    Note over Service: Kiểm tra: originalDoc.ownerDepartmentId == currentUser.departmentId
    Note over Service: Cập nhật alias.deletedAt = currentTimestamp
    Service->>Repo: save(alias)
    Repo->>DB: UPDATE documents SET deleted_at = ? WHERE id = ?
    DB-->>Repo: Thành công
    Service-->>Controller: Phản hồi thành công
    Controller-->>User: 200 OK
```

---

## 8. Quy tắc Xác thực Nghiệp vụ (Validation Rules)

1.  **Ranh giới BOARD (BOARD Boundary Rule):** Hệ thống từ chối mọi yêu cầu tạo Alias đối với tài liệu gốc thuộc sở hữu của phòng ban `BOARD`. Thành viên phòng `BOARD` cũng bị cấm tạo hoặc nhận Alias chéo phòng ban dưới mọi hình thức.
2.  **Xác thực Quyền sở hữu (Ownership Validation Rule):** Khi tạo Alias, hệ thống bắt buộc kiểm tra phòng ban của người dùng hiện tại phải trùng khớp với phòng ban sở hữu tài liệu gốc (`currentUser.departmentId == originalDocument.ownerDepartmentId`). Nếu không khớp, hệ thống ném ra ngoại lệ vi phạm quyền sở hữu.
3.  **Cấm Alias nối tiếp (Anti-Chaining Rule):** Khóa chính UUID của tài liệu gốc được truyền vào để tạo Alias bắt buộc phải có bit cuối của LSB = `0` (là Original Document). Nếu bit cuối LSB = `1` (Alias Document), hệ thống lập tức từ chối vì đó là một Alias.
4.  **Cấm tự chia sẻ (No Self-Sharing Rule):** Phòng ban nhận Alias phải khác với phòng ban sở hữu tài liệu gốc hiện tại.
5.  **Giới hạn Định dạng File (File Whitelist Rule):** Chỉ chấp nhận các tệp tải lên có phần mở rộng và MIME type hợp lệ: `.pdf`, `.docx`, `.xlsx`, `.pptx`. Dung lượng tối đa được cấu hình cứng là **50MB**.
6.  **Quy tắc Điều hướng Gọi thẳng Tối ưu hóa (Fast-Path Routing Rule):** Dựa trên đặc thù phân bổ tài nguyên của hệ thống (99% tài nguyên là tài liệu gốc, chỉ 1% là Alias), hệ thống tối ưu hóa luồng xử lý bằng cách kiểm tra nhanh bit cuối của ID tài liệu:
    - Nếu bit cuối (LSB) = `0` (Original - chiếm 99% trường hợp): Điều hướng và gọi trực tiếp vào bộ xử lý tài liệu gốc chuyên biệt (`OriginalDocumentProcessor`) mà không chạy qua các logic phân giải Alias hay kiểm tra DB chéo phòng ban.
    - Nếu bit cuối (LSB) = `1` (Alias - chiếm 1% trường hợp): Điều hướng và gọi trực tiếp vào bộ xử lý phân giải liên kết (`AliasDocumentProcessor`).

---

## 9. Xử lý Lỗi và Mã lỗi Nghiệp vụ (Error Handling & ErrorCodes)

Hệ thống quản lý lỗi tập trung thông qua bộ xử lý lỗi toàn cục (`GlobalExceptionHandler`). Khi xảy ra lỗi nghiệp vụ, hệ thống trả về mã trạng thái HTTP thích hợp kèm mã lỗi nghiệp vụ cụ thể:

| Mã lỗi Nghiệp vụ (`code`) | HTTP Status | Thông điệp hiển thị | Nguyên nhân |
| :--- | :--- | :--- | :--- |
| `ERR_UNAUTHENTICATED` | 401 Unauthorized | Phiên đăng nhập hết hạn hoặc không hợp lệ. | JWT token bị sai, hết hạn, hoặc thiếu cấu trúc. |
| `ERR_OWNERSHIP_VIOLATION`| 404 Not Found | Tài liệu yêu cầu không tồn tại. | Người dùng truy cập tài liệu chéo phòng ban không thuộc sở hữu (che giấu sự tồn tại để bảo mật). |
| `ERR_FORBIDDEN_ROLE`  | 403 Forbidden | Bạn không có quyền thực hiện hành động này. | Người dùng gọi API không đúng vai trò cho phép. |
| `ERR_BOARD_PROTECTION` | 400 Bad Request| Cấm tạo liên kết Alias đối với tài liệu của phòng BOARD. | Cố tình tạo Alias cho tài liệu bảo mật của BOARD. |
| `ERR_DUPLICATE_ALIAS` | 400 Bad Request| Phòng ban nhận đã nhận một liên kết đang hoạt động từ tài liệu này. | Vi phạm ràng buộc duy nhất 1 Alias hoạt động của phòng ban nhận. |
| `ERR_DOCUMENT_NOT_FOUND` | 404 Not Found | Tài liệu yêu cầu không tồn tại. | Không tìm thấy tài liệu gốc hoặc Alias hoạt động. |
| `VALIDATION_ERROR` | 400 Bad Request| Dữ liệu không hợp lệ. | Vi phạm kiểm tra dữ liệu đầu vào DTO. |
| `ERR_SYSTEM_ERROR` | 500 Internal | Lỗi hệ thống bất ngờ. | Lỗi kết nối DB, lỗi runtime không mong muốn. |

---

## 10. Logic Nghiệp vụ Chi tiết (Detailed Business Logic)

### 10.1. Luồng Lan truyền xóa mềm (Cascade Soft Delete)
1.  **Bước 1**: Nhận yêu cầu xóa tài liệu gốc từ Quản lý phòng ban sở hữu hoặc thành viên BOARD sở hữu.
2.  **Bước 2**: Thực hiện tìm kiếm tài liệu gốc và khóa bản ghi bằng khóa bi quan `PESSIMISTIC_WRITE` (`FOR UPDATE`) dưới cơ sở dữ liệu để chặn mọi yêu cầu tạo Alias chéo phòng ban song song.
3.  **Bước 3**: Xác thực thực thể tìm được có phải tài liệu gốc hay không (LSB = 0) và thuộc quyền sở hữu của phòng ban người dùng hiện tại không (`currentUser.departmentId == original.ownerDepartmentId`). Nếu không thỏa mãn, ném lỗi `ERR_DOCUMENT_NOT_FOUND` (404) để bảo mật.
4.  **Bước 4**: Cập nhật thuộc tính thời gian xóa mềm (`deleted_at`) bằng thời gian hệ thống hiện tại và thực hiện lưu bản ghi tài liệu gốc.
5.  **Bước 5**: Gọi phương thức Repository cập nhật đồng loạt trạng thái xóa mềm (`deleted_at`) cho toàn bộ Alias trỏ tới tài liệu gốc này (`parent_id` bằng ID tài liệu gốc và `deleted_at` đang NULL).

### 10.2. Luồng Xóa liên kết Alias
1.  **Bước 1**: Nhận yêu cầu xóa Alias từ Quản lý phòng ban sở hữu tài liệu gốc (phòng ban tạo Alias).
2.  **Bước 2**: Tìm bản ghi Alias nghiệp vụ theo ID. Xác thực thực thể đó là Alias (LSB = 1), nếu không ném lỗi không tìm thấy tài liệu.
3.  **Bước 3**: Tìm bản ghi tài liệu gốc liên quan (`parent_id`) và khóa bản ghi bằng khóa bi quan `PESSIMISTIC_WRITE` để tránh xung đột dữ liệu.
4.  **Bước 4**: Kiểm tra phòng ban người dùng hiện tại có trùng với phòng ban sở hữu tài liệu gốc đó không (`original.ownerDepartmentId == currentUser.departmentId`). Chỉ cho phép phòng ban sở hữu tài liệu gốc thực hiện xóa Alias đã tạo. Nếu không khớp, ném lỗi `ERR_OWNERSHIP_VIOLATION` (404).
5.  **Bước 5**: Gán thuộc tính xóa mềm (`deleted_at`) cho bản ghi Alias bằng thời gian hiện tại và thực hiện lưu trữ.

### 10.3. Luồng Tạo liên kết chia sẻ Alias
1.  **Bước 1**: Nhận yêu cầu tạo Alias từ nhân viên/quản lý phòng ban sở hữu tài liệu gốc.
2.  **Bước 2**: Khóa tài liệu gốc bằng khóa bi quan ghi `PESSIMISTIC_WRITE` để ngăn chặn tranh chấp dữ liệu khi tài liệu gốc bị xóa đồng thời từ luồng khác.
3.  **Bước 3**: Xác thực tài liệu gốc thuộc sở hữu của phòng ban người dùng nghiệp vụ hiện tại. Kiểm tra tài liệu gốc có phải Original không (LSB = 0), cấm tạo Alias cho Alias.
4.  **Bước 4**: Kiểm tra phòng ban nhận không được trùng với phòng ban sở hữu tài liệu gốc hiện tại (không tự chia sẻ cho chính mình). Kiểm tra phòng ban nhận không được là phòng ban `BOARD`.
5.  **Bước 5**: Thực hiện kiểm tra xem phòng ban nhận đã có Alias hoạt động trỏ tới tài liệu gốc này hay chưa bằng cách kiểm tra sự tồn tại của Alias hoạt động dưới DB. Nếu có, ném lỗi trùng lặp Alias (`ERR_DUPLICATE_ALIAS`).
6.  **Bước 6**: Sinh mã UUID mới cho Alias và thiết lập bit cuối của LSB bằng 1. Gán các trường liên kết tương ứng: `parent_id` trỏ đến ID tài liệu gốc, `creator_department_id` bằng phòng ban người dùng tạo Alias, và `owner_department_id` bằng ID phòng ban nhận. Thiết lập các siêu dữ liệu và lưu thực thể Alias mới xuống DB.
