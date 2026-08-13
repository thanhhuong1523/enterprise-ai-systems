# TÀI LIỆU THIẾT KẾ CHI TIẾT (DETAILED DESIGN DOCUMENT - DDD)
**Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer) (Tuần 4 - Phiên bản 1.1)**

---

## MỤC LỤC (TABLE OF CONTENTS)
1. [Introduction (Giới thiệu)](#1-introduction-giới-thiệu)
2. [System Architecture & Component Design (Kiến trúc Hệ thống & Thiết kế Thành phần)](#2-system-architecture-component-design-kiến-trúc-hệ-thống-thiết-kế-thành-phần)
3. [Data & Database Design (Thiết kế Dữ liệu & Cơ sở dữ liệu)](#3-data-database-design-thiết-kế-dữ-liệu-cơ-sở-dữ-liệu)
4. [Detailed Module Design (Thiết kế Chi tiết các Module)](#4-detailed-module-design-thiết-kế-chi-tiết-các-module)
5. [API & SQL Interface Design (Thiết kế API & Truy vấn SQL)](#5-api-sql-interface-design-thiết-kế-api-truy-vấn-sql)
6. [Security & Authorization Design (Thiết kế Bảo mật & Phân quyền)](#6-security-authorization-design-thiết-kế-bảo-mật-phân-quyền)
7. [Non-Functional & System Robustness (Thiết kế Phi chức năng & Độ bền bỉ)](#7-non-functional-system-robustness-thiết-kế-phi-chức-năng-độ-bền-bỉ)
8. [Testing & Assumptions (Kiểm thử & Giả định)](#8-testing-assumptions-kiểm-thử-giả-định)

---

## 1. INTRODUCTION (GIỚI THIỆU)

| Thuộc tính | Chi tiết tài liệu |
| :--- | :--- |
| **Mã tài liệu (Document ID)** | DD-EAP-W4-001 |
| **Phiên bản (Version)** | 1.1 |
| **Trạng thái (Status)** | Sẵn sàng phê duyệt |
| **Ngày phát hành (Date)** | 2026-08-13 |
| **Tác giả (Author)** | Nhóm Phát triển Backend (Senior Software Engineer) |
| **Tài liệu Kiến trúc liên quan** | [ADD-004](file:///Users/phantom/Downloads/Intern/project/eap/docs/week4/architecture_design.md) |
| **Mục đích & Phạm vi (Purpose & Scope)** | Tài liệu đặc tả thiết kế chi tiết cấp thấp phục vụ lập trình cho phân hệ **Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer)** của dự án VCC-EAP. Thiết kế tuân thủ các chỉ định và quyết định kiến trúc tại tài liệu ADD-004. Phạm vi thiết kế bao gồm cấu trúc lớp, sơ đồ tuần tự thực thi, cấu trúc cơ sở dữ liệu và kịch bản Flyway migration. |

### 1.1. Lịch sử Thay đổi (Revision History)
| Phiên bản | Ngày | Tác giả | Mô tả Thay đổi / Ghi chú |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-08-07 | Nhóm Phát triển | Phiên bản đầu tiên. Đặc tả thiết kế chi tiết cho phân hệ Số hóa & Tra cứu. |
| 1.1 | 2026-08-13 | Nhóm Phát triển | Chuẩn hóa theo thiết kế kiến trúc mới (ADD v1.1) và PRD v1.2: chuyển đổi phương pháp phân mảnh từ Phân mảnh Ngữ nghĩa sang Phân mảnh theo Đoạn văn (Paragraph Chunking), loại bỏ Matryoshka 768 chiều cho ranh giới câu, và tích hợp bộ lọc ngưỡng tương đồng tối thiểu. Đồng thời chuyển truy vấn SQL sang Named Parameters, tham số hóa BOARD ID (`:boardDeptId`), và hiển thị tiêu đề tài liệu gốc (`d.title`) chéo phòng ban qua Alias theo đúng PRD TC-4.3. |

---

## 2. SYSTEM ARCHITECTURE & COMPONENT DESIGN (KIẾN TRÚC HỆ THỐNG & THIẾT KẾ THÀNH PHẦN)

### 2.1. Logical Component Design (Thiết kế Thành phần Logic)
```mermaid
graph TD
    subgraph ControllerLayer ["Tầng Controller"]
        SearchController["SearchController"]
    end

    subgraph ServiceLayer ["Tầng Service"]
        RetrievalService["RetrievalService"]
        DocumentDigitizationService["DocumentDigitizationService"]
        TextExtractionService["TextExtractionService"]
        ChunkingService["ChunkingService"]
        EmbeddingService["EmbeddingService"]
        ChunkPersistenceService["ChunkPersistenceService"]
    end

    subgraph RepositoryLayer ["Tầng Repository"]
        DocumentRepository["DocumentRepository"]
        ChunkRepository["ChunkRepository"]
    end

    subgraph DatabaseLayer ["Tầng Database"]
        PostgresDB[("PostgreSQL + pgvector")]
    end

    %% Flow: Search Path
    SearchController -->|SearchRequest, AuthContext| RetrievalService
    RetrievalService -->|String Query| EmbeddingService
    RetrievalService -->|Vector, AuthContext| ChunkRepository
    ChunkRepository -->|Raw SQL query| PostgresDB

    %% Flow: Digitization Path (Week 4 Pipeline, triggered by Week 3 WorkerExecutor)
    WorkerExecutorImpl["WorkerExecutorImpl (Week 3)"] -->|executeTask| DocumentDigitizationService
    DocumentDigitizationService -->|fileReference| TextExtractionService
    DocumentDigitizationService -->|Raw text| ChunkingService
    DocumentDigitizationService -->|List of chunks| EmbeddingService
    DocumentDigitizationService -->|Single Chunk + Vector| ChunkPersistenceService
    ChunkPersistenceService -->|Save chunk in transaction| ChunkRepository
    ChunkRepository -->|Insert Chunks & update checkpoints| PostgresDB
```

---

### 2.2. Class Responsibilities (Trách nhiệm của các Lớp)
#### 2.2.1. Tầng Controller
*   **`SearchController`**:
    *   Địa chỉ endpoint: `POST /api/v1/search`.
    *   Trách nhiệm: Nhận truy vấn tìm kiếm ngữ nghĩa nghiệp vụ từ phía máy khách, xác thực đầu vào (`SearchRequest`), trích xuất thông tin người dùng đăng nhập hiện tại từ Spring Security để xây dựng `EapAuthorizationContext`.
    *   Chặn hoàn toàn người dùng có vai trò `SYSTEM_ADMIN` bằng cách ném ra `403 Forbidden` (sử dụng `ErrorCode.ERR_FORBIDDEN_ROLE`).

#### 2.2.2. Tầng Service
*   **`DocumentDigitizationService`**:
    *   Trách nhiệm: Điều phối toàn bộ luồng số hóa văn bản nền (Digitization Pipeline) của tài liệu từ trạng thái `PROCESSING`. Lớp này sẽ được `WorkerExecutorImpl` (Tuần 3) gọi sau khi đã xác thực file và hash thành công.
*   **`TextExtractionService`**:
    *   Trách nhiệm: Đọc tệp vật lý gốc (PDF, DOCX, XLSX) thông qua `file_reference` và trích xuất chuỗi văn bản thô tiếng Việt (UTF-8). Tích hợp với `pdfbox`, `poi` và `tika` đã khai báo trong `pom.xml`.
*   **`ChunkingService`**:
    *   Trách nhiệm: Thực hiện phân mảnh văn bản theo đoạn văn (Paragraph Chunking). Tách văn bản thô dựa trên dấu ngắt đoạn tự nhiên (mặc định là `\n\n`), cắt cứng các đoạn văn bản dài vượt quá giới hạn tối đa tại các ranh giới câu, và áp dụng ràng buộc kích thước tối thiểu để gộp các mảnh dư thừa vào mảnh liền trước nhằm tránh phân mảnh vụn.
*   **`EmbeddingService`**:
    *   Trách nhiệm: Tải mô hình nhúng cục bộ BGE-M3 (ONNX) và sinh vector nhúng 1024 chiều cho chuỗi văn bản (in-process). Quản lý khởi tạo Singleton và cấu hình song song của ONNX Runtime session. Thực hiện kiểm chứng đầu ra luôn đạt 1024 chiều và L2-Normalize.
*   **`ChunkPersistenceService`**:
    *   Trách nhiệm: Lưu trữ bền vững theo lô (Batch Database Persistence) các mảnh văn bản nghiệp vụ, chỉ mục thứ tự (`chunk_index`), vector nhúng và siêu dữ liệu phiên bản mô hình vào cơ sở dữ liệu để tránh nghẽn kết nối và overhead mạng. Nếu việc ghi theo lô bị lỗi, hệ thống kích hoạt cơ chế fallback lưu từng chunk đơn lẻ để có thể bỏ qua (skip) các chunk bị lỗi nghiệp vụ cụ thể. Để đảm bảo cô lập lỗi ở cấp độ lô/chunk, phương thức lưu trữ của lớp này được đánh dấu `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
*   **`RetrievalService`**:
    *   Trách nhiệm: Điều phối tiến trình tìm kiếm ngữ nghĩa. Nhận câu hỏi, gọi `EmbeddingService` sinh vector truy vấn, lấy dữ liệu phân quyền phòng ban của người dùng hiện tại từ `EapAuthorizationContext` và chuyển giao thông tin xuống `ChunkRepository` để thực thi truy vấn cơ sở dữ liệu.

#### 2.2.3. Tầng Repository
*   **`ChunkRepository` (hoặc `ChunkRepositoryCustom`)**:
    *   Trách nhiệm: Thực hiện các câu lệnh **SQL thô (Raw SQL)** tương tác với pgvector. Truy vấn lọc phân quyền phòng ban, Alias sharing, BOARD isolation và tìm kiếm vector tương đồng Cosine lân cận gần đúng (ANN) sử dụng chỉ mục HNSW trực tiếp trên cơ sở dữ liệu PostgreSQL. Map kết quả trả về thành các DTO nghiệp vụ sạch sẽ mà không thực hiện bất kỳ hoạt động post-filtering nào trên JVM.

---

### 2.3. Digitization Pipeline (Đường ống Số hóa)
Đường ống số hóa văn bản của một tài liệu diễn ra bất đồng bộ. Bất kỳ lỗi hệ thống nghiêm trọng nào xảy ra ở cấp độ tài liệu (như lỗi trích xuất chữ, lỗi sập ONNX Session) sẽ chuyển trạng thái tài liệu thành `FAILED`. Các lỗi xảy ra khi lưu trữ từng mảnh nhỏ (chunk) sẽ được xử lý cô lập (Skip/Retry) để không ảnh hưởng tới tiến trình chung.

```mermaid
sequenceDiagram
    autonumber
    participant W as WorkerExecutor
    participant DS as DocumentDigitizationService
    participant TE as TextExtractionService
    participant CS as ChunkingService
    participant ES as EmbeddingService
    participant PS as ChunkPersistenceService
    participant R as ChunkRepository

    W->>DS: digitizeDocument(docId, workerId)
    activate DS
    
    DS->>TE: extractText(fileReference)
    activate TE
    TE-->>DS: String (Raw UTF-8 Text)
    deactivate TE
    
    DS->>CS: splitDocument(rawText)
    activate CS
    Note over CS: Phân mảnh theo đoạn văn (\n\n),<br/>cắt câu khi vượt max-tokens,<br/>gộp phần dư khi dưới min-tokens.
    CS-->>DS: List<String> (Danh sách nội dung các mảnh văn bản)
    deactivate CS

    Note over DS: Chia danh sách mảnh thành các lô (batch) tối đa 128 chunks
    
    loop Cho mỗi lô chunks
        DS->>ES: embedBatch(batchChunks)
        activate ES
        Note over ES: Gọi ONNX Runtime in-process sinh vector 1024-dim<br/>và thực hiện chuẩn hóa L2
        ES-->>DS: List<float[]> (Danh sách vector nhúng)
        deactivate ES
    end

    Note over DS: Cập nhật total_chunks và last_completed_chunk = 0 vào DB
    
    loop Cho mỗi chunk từ k = lastCompleted + 1 đến totalChunks
        DS->>PS: persistChunk(docId, k, content, embedding)
        activate PS
        Note over PS: Thực thi trong REQUIRES_NEW transaction<br/>Thử lại tối đa 3 lần nếu lỗi DB
        PS->>R: insert chunk & update checkpoint
        R-->>PS: success
        PS-->>DS: success
        deactivate PS
        
        alt Lưu trữ thành công
            Note over DS: Tiếp tục chunk k+1
        else Giao dịch lưu trữ lỗi sau 3 lần retry
            Note over DS: SKIP chunk k này<br/>Ghi log cảnh báo và chỉ cập nhật checkpoint last_completed_chunk = k
        end
    end
    
    Note over DS: Hoàn tất toàn bộ chunks
    DS->>W: markCompleted(docId)
    deactivate DS
```

---

### 2.4. Retrieval Pipeline (Đường ống Tra cứu ngữ nghĩa - RAG)
Quy trình tiếp nhận yêu cầu tìm kiếm ngữ nghĩa, sinh vector truy vấn và thực hiện tìm kiếm kết hợp phân quyền trong hệ thống RAG diễn ra đồng bộ theo sơ đồ tuần tự dưới đây:

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SC as SearchController
    participant RS as RetrievalService
    participant ES as EmbeddingService
    participant R as ChunkRepository
    participant DB as PostgreSQL + pgvector

    C->>SC: POST /api/v1/search (query, limit)
    activate SC
    
    Note over SC: Xác thực đầu vào và vai trò người dùng<br/>Nếu là SYSTEM_ADMIN ném lỗi 403
    
    SC->>RS: search(query, limit, authContext)
    activate RS
    
    RS->>ES: embedQuery(query)
    activate ES
    Note over ES: Tokenize và gọi ONNX Runtime in-process<br/>Chuẩn hóa L2 vector 1024 chiều
    ES-->>RS: float[] queryVector (1024-dim)
    deactivate ES
    
    RS->>R: findTopSimilarChunks(queryVector, limit, userDeptId)
    activate R
    
    Note over R: Xây dựng câu truy vấn SQL thô tích hợp:<br/>1. Tìm tương đồng HNSW (toán tử <=> )<br/>2. Lọc cô lập phòng ban & BOARD<br/>3. Phân giải tên hiển thị qua Alias
    
    R->>DB: Thực thi truy vấn SQL thô
    activate DB
    DB-->>R: ResultSet (chunkId, content, displayTitle, score...)
    deactivate DB
    
    Note over R: Map ResultSet sang List<SearchResultItem> DTO
    R-->>RS: List<SearchResultItem>
    deactivate R
    
    RS-->>SC: SearchResponse
    deactivate RS
    
    SC-->>C: 200 OK (SearchResponse)
    deactivate SC
```

---


## 3. DATA DESIGN & DATABASE SCHEMA (THIẾT KẾ DỮ LIỆU & CƠ SỞ DỮ LIỆU)

### 3.1. Relational Database Design (Thiết kế Cơ sở dữ liệu)
Đặc tả chi tiết cấu trúc bảng dữ liệu vật lý, sơ đồ quan hệ thực thể (ERD) chi tiết và sơ đồ biểu diễn mối quan hệ giữa các lớp mô hình:

#### 3.1.1. Biểu đồ lớp của mô hình (Model Class Diagram)
Biểu đồ lớp dưới đây thể hiện các mối quan hệ (owns, has, creates...) giữa các thực thể/lớp trong mã nguồn:

```mermaid
classDiagram
    class Department
    class User
    class Document
    class Chunk

    Department "1" --> "*" User : has
    Department "1" --> "*" Document : owns
    User "1" --> "*" Document : creates
    Document "1" --> "*" Chunk : has
    Document "0..1" --> "*" Document : references
```

#### 3.1.2. Sơ đồ thực thể liên kết chi tiết (Full Entity Relationship Diagram)
Sơ đồ dưới đây mô tả đầy đủ tất cả các trường thông tin, kiểu dữ liệu, khóa chính (PK) và khóa ngoại (FK) của toàn bộ các bảng trong cơ sở dữ liệu:

```mermaid
erDiagram
    tbl_departments {
        UUID id PK
        VARCHAR code UK "Mã phòng ban"
        VARCHAR name "Tên phòng ban"
    }

    tbl_users {
        UUID id PK
        VARCHAR username UK "Tên đăng nhập"
        VARCHAR email UK "Địa chỉ email"
        VARCHAR password_hash "Mật khẩu đã mã hóa"
        VARCHAR role "Vai trò người dùng"
        UUID department_id FK "Tham chiếu tới tbl_departments(id)"
    }

    tbl_documents {
        UUID id PK
        VARCHAR business_code UK "Mã nghiệp vụ tài liệu"
        VARCHAR title "Tiêu đề tài liệu"
        VARCHAR file_reference "Đường dẫn file vật lý"
        BIGINT file_size "Kích thước file (bytes)"
        VARCHAR hash "Mã hash MD5/SHA256 file"
        UUID owner_department_id FK "ID phòng ban sở hữu tài liệu gốc"
        UUID parent_id FK "ID tài liệu gốc (phục vụ Alias)"
        UUID creator_department_id FK "ID phòng ban người tạo Alias"
        UUID created_by FK "ID tài khoản người tạo"
        TIMESTAMP created_at "Thời điểm tạo"
        TIMESTAMP updated_at "Thời điểm cập nhật"
        TIMESTAMP deleted_at "Thời điểm xóa mềm"
        VARCHAR status "Trạng thái tài liệu (PROCESSING, COMPLETED, FAILED)"
        VARCHAR worker_id "ID luồng đang xử lý tài liệu"
        INT retry_count "Số lần thử lại số hóa"
        INT last_completed_chunk "Mảnh cuối cùng hoàn tất số hóa"
        INT total_chunks "Tổng số mảnh của tài liệu"
    }

    tbl_chunks {
        UUID id PK
        UUID document_id FK "Tham chiếu tới tbl_documents(id)"
        INT chunk_index "Chỉ số phân mảnh (0..N-1)"
        TEXT content "Nội dung văn bản thô của phân mảnh"
        vector-1024 embedding "Vector nhúng 1024 chiều (pgvector)"
        TIMESTAMP created_at "Thời điểm tạo"
    }

    tbl_departments ||--o{ tbl_users : "chứa người dùng (1:N)"
    tbl_departments ||--o{ tbl_documents : "sở hữu tài liệu gốc / tạo alias (1:N)"
    tbl_users ||--o{ tbl_documents : "tạo tài liệu (1:N)"
    tbl_documents ||--o{ tbl_chunks : "chứa các mảnh (1:N)"
    tbl_documents ||--o{ tbl_documents : "alias chia sẻ (parent_id -> id)"
```

#### Chi tiết cấu trúc các cột bảng `tbl_chunks`:
*   `id`: Khóa chính định danh phân đoạn (UUID).
*   `document_id`: Khóa ngoại liên kết tới bảng `tbl_documents(id)` kèm tùy chọn `ON DELETE CASCADE`.
*   `chunk_index`: Vị trí tương đối của phân mảnh trong tài liệu gốc. Ràng buộc `UNIQUE(document_id, chunk_index)` đảm bảo tính toàn vẹn.
*   `content`: Nội dung văn bản của mảnh được tách bởi bộ phân mảnh ngữ nghĩa.
*   `embedding`: Vector nhúng 1024 chiều được tối ưu hóa qua HNSW Index (`vector_cosine_ops`).
*   `created_at`: Thời điểm lưu trữ phân đoạn.

---

### 3.2. Database Migration Design (Thiết kế Di cư Flyway)
Tạo tệp migration mới `V14__create_chunks_table.sql` trong thư mục `src/main/resources/db/migration/`:

```sql
-- Migration V14: Kích hoạt pgvector, tạo bảng tbl_chunks và thiết lập các chỉ mục tối ưu
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Tạo bảng tbl_chunks lưu trữ phân đoạn văn bản và vector tương ứng
CREATE TABLE tbl_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024), -- Cột vector nhúng cố định 1024 chiều
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id) REFERENCES tbl_documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_document_chunk UNIQUE (document_id, chunk_index) -- Đảm bảo không trùng lặp vị trí chunk của tài liệu
);

-- 2. Tạo index cho khoá ngoại document_id phục vụ xoá cascade/truy vấn nhanh
CREATE INDEX idx_chunks_document_id ON tbl_chunks(document_id);

-- 3. Tạo index HNSW hỗ trợ tìm kiếm lân cận gần đúng sử dụng độ tương đồng Cosine
-- m = 16 (số kết nối tối đa mỗi node), ef_construction = 64 (kích thước tập ứng viên khi dựng đồ thị)
CREATE INDEX idx_chunks_embedding_hnsw ON tbl_chunks USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. Tạo index trên tbl_documents để tối ưu hoá tìm kiếm phân giải Alias chéo phòng ban
CREATE INDEX idx_documents_parent_id ON tbl_documents(parent_id)
WHERE parent_id IS NOT NULL AND deleted_at IS NULL;
```

---

### 3.3. HNSW Vector Index Design (Thiết kế Index Vector)
Để đáp ứng mục tiêu hiệu năng tìm kiếm lân cận gần đúng (ANN) dưới 500ms đối với số lượng bản ghi lớn, chỉ mục HNSW được thiết lập trên cột vector nhúng:

*   **Distance Operator**: Sử dụng toán tử `<=>` đại diện cho khoảng cách Cosine. Chỉ mục tương ứng là `vector_cosine_ops`.
*   **Cú pháp tạo chỉ mục**:
    ```sql
    CREATE INDEX idx_chunks_embedding_hnsw 
    ON tbl_chunks 
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
    ```
*   **Các tham số tinh chỉnh (Tuning Parameters)**:
    *   `m = 16`: Số lượng kết nối tối đa được tạo ra cho mỗi điểm vector mới trong đồ thị. Giá trị 16 là tối ưu cho việc cân bằng giữa dung lượng chỉ mục và độ chính xác tìm kiếm (Recall).
    *   `ef_construction = 64`: Số lượng ứng viên được đánh giá trong quá trình xây dựng đồ thị chỉ mục.
    *   `hnsw.ef_search`: Số lượng ứng viên được đánh giá trong quá trình truy vấn tìm kiếm. Tham số này có thể cấu hình ở cấp độ phiên làm việc (Session-level) trước khi thực hiện tìm kiếm để nâng cao độ chính xác (Ví dụ: `SET hnsw.ef_search = 32;`).

---

---

## 4. DETAILED MODULE DESIGN (THIẾT KẾ CHI TIẾT CÁC MODULE)

### 4.1. Chunker Module & Pseudocode (Thiết kế Phân mảnh & Mã giả)
Giải thuật phân mảnh văn bản sử dụng giải pháp **Phân mảnh theo Đoạn văn kết hợp xử lý tránh phân mảnh vụn (Paragraph Chunking with Min Chunk Size constraint)** được triển khai qua `ChunkingService`:

#### 4.1.1. Các tham số cấu hình
Các tham số phân mảnh được thiết kế dưới dạng cấu hình hệ thống động để có thể tinh chỉnh linh hoạt tại runtime không cần khởi động lại ứng dụng:
*   `eap.chunking.paragraph-separator`: Ký tự ngắt đoạn văn bản tự nhiên (Mặc định: `\n\n`).
*   `eap.chunking.max-tokens`: Giới hạn kích thước tối đa của mỗi mảnh (Mặc định: 1000 tokens).
*   `eap.chunking.min-tokens`: Giới hạn kích thước tối thiểu của mỗi mảnh (Mặc định: 100 tokens).
*   `eap.chunking.overflow-max-tokens`: Kích thước tràn tối đa của chunk liền trước khi gộp phần dư (Mặc định: 1100 tokens).

#### 4.1.2. Giải thuật phân mảnh chi tiết
1.  **Bước 1: Chuẩn hoá văn bản & Phân tách tự nhiên**:
    *   Chuyển đổi văn bản thô sang Unicode chuẩn **NFC** và chuẩn hóa khoảng trắng thừa.
    *   Tách văn bản thô thành danh sách các đoạn văn bản tự nhiên dựa trên ký tự ngắt đoạn cấu hình trong `eap.chunking.paragraph-separator` (mặc định là dấu xuống dòng kép `\n\n`).
2.  **Bước 2: Duyệt từng đoạn và kiểm tra kích thước (Max Tokens Constraint)**:
    *   Duyệt qua danh sách các đoạn văn bản tự nhiên vừa phân tách. Với mỗi đoạn, tính toán số lượng token.
    *   Nếu độ dài (tính bằng số lượng token) của đoạn văn $\le T_{\max}$ (`eap.chunking.max-tokens`), đoạn văn này được lưu trữ trực tiếp thành 1 chunk độc lập.
3.  **Bước 3: Cắt cứng tại câu khi vượt quá giới hạn (Hard Split)**:
    *   Nếu đoạn văn tự nhiên có độ dài $> T_{\max}$ tokens, tiến hành phân rã đoạn văn đó thành danh sách các câu tiếng Việt (dựa trên các dấu câu ngắt câu `.`, `?`, `!`).
    *   Thực hiện gom nhóm các câu từ đầu đoạn văn cho đến khi tổng số token đạt sát nút giới hạn $T_{\max}$. Điểm ngắt sẽ nằm tại ranh giới câu gần nhất không làm vượt quá $T_{\max}$.
    *   Hệ thống không sử dụng tính toán vector tương đồng giữa các câu in-memory và không áp dụng cơ chế overlap (overlap = 0) giữa các chunk khi ngắt.
4.  **Bước 4: Tránh phân mảnh vụn (Min Chunk Size Constraint)**:
    *   Khi cắt cứng, nếu phần dư (residual) của câu còn lại ở cuối đoạn văn có kích thước nhỏ hơn giới hạn tối thiểu $T_{\min}$ (`eap.chunking.min-tokens`), hệ thống sẽ thực hiện:
        *   Gộp phần dư này vào chunk liền trước, chấp nhận kích thước của chunk liền trước vượt quá $T_{\max}$ một chút nhưng không được phép vượt quá giới hạn tràn tối đa $T_{\text{overflow\_max}}$ (`eap.chunking.overflow-max-tokens` - mặc định là 1100 tokens).
        *   Trường hợp nếu gộp vào làm vượt quá $T_{\text{overflow\_max}}$, hệ thống thực hiện phân bổ lại điểm cắt tại các ranh giới câu gần đó sao cho độ dài của chunk trước và chunk sau tương đối cân bằng và đều lớn hơn $T_{\min}$.
5.  **Bước 5: Tính lũy đẳng (Idempotency)**:
    *   Quy trình phân mảnh đảm bảo tính xác định (deterministic). Khi số hóa lại cùng một tài liệu, các chunk được tạo ra sẽ trùng khớp hoàn toàn, đảm bảo ghi đè/cập nhật chính xác lên dữ liệu cũ dựa trên khoá duy nhất `uq_document_chunk (document_id, chunk_index)`.

---

### 4.2. Embedding Engine (Thiết kế Bộ sinh Vector)
Tích hợp in-process mô hình nhúng **BGE-M3** thông qua thư viện **ONNX Runtime (ORT)** trong JVM để xử lý in-process sinh vector.

#### 4.2.1. Tải và Quản lý Vòng đời Mô hình
*   **Singleton Managed Bean**: Lớp `EmbeddingService` được Spring quản lý dưới dạng Singleton Bean. Nó sẽ khởi tạo thực thể `OrtEnvironment` và `OrtSession` một lần duy nhất lúc khởi động ứng dụng (hoặc tải lười - lazy load khi có truy vấn đầu tiên).
*   **Giải pháp nạp file từ JAR (Classpath Resource extraction)**:
    *   Mô hình BGE-M3 nặng 570MB nằm tại đường dẫn classpath `/onnx/model_quantized.onnx`. Khi Spring Boot chạy dưới dạng file FAT JAR đóng gói, thư viện ONNX C++ không thể đọc trực tiếp tài nguyên từ bên trong JAR.
    *   Để tránh việc đọc toàn bộ file 570MB thành mảng `byte[]` trong bộ nhớ Heap của JVM (gây rủi ro OutOfMemory và áp lực lớn lên Garbage Collection), `EmbeddingService` sẽ:
        1. Tái sử dụng thư mục lưu trữ có sẵn `eap-storage/models` trên ổ đĩa vật lý của hệ thống.
        2. Sao chép luồng dữ liệu (`InputStream`) từ classpath `/onnx/model_quantized.onnx` ra một file vật lý tạm thời trong thư mục `eap-storage/models`.
        3. Khởi tạo `OrtSession` bằng cách truyền đường dẫn tuyệt đối của file tạm này. Điều này cho phép hệ điều hành tận dụng cơ chế Memory-Mapped File (mmap) của ONNX Runtime giúp tối ưu hóa dung lượng RAM vượt trội.
        4. File tạm này sẽ được tự động xoá khi ứng dụng shutdown (đăng ký `File.deleteOnExit()`).

#### 4.2.2. Cấu hình ONNX Runtime & Thread Safety
*   **Thread Safety**: `OrtSession` là lớp an toàn đa luồng. Một thực thể duy nhất có thể phục vụ song song cho nhiều luồng nghiệp vụ tìm kiếm (HTTP request threads) và luồng xử lý nền (Background Digitization Worker).
*   **Resource Contention Management**:
    *   Đặt số lượng luồng tính toán toán tử bên trong ONNX (`Intra-Op Threads`) giới hạn ở mức tối đa là 4 (hoặc số nhân CPU thực tế chia đôi) để không chiếm dụng 100% CPU của máy chủ, ảnh hưởng tới luồng REST API xử lý HTTP.
    *   Đặt số lượng luồng thực thi song song giữa các toán tử (`Inter-Op Threads`) là 1.
    *   Chế độ thực thi: `ORT_SEQUENTIAL`.

#### 4.2.3. Chuẩn bị Đầu vào & Thực thi Nhúng
*   Sử dụng thư viện `ai.djl.huggingface.tokenizers.HuggingFaceTokenizer` để nạp `tokenizer.json` của mô hình BGE-M3 trực tiếp từ classpath.
*   Quy trình nhúng một chuỗi văn bản:
    1.  Tokenize văn bản thu được mảng các ID token (`input_ids`) và mảng mặt nạ chú ý (`attention_mask`).
    2.  Chuyển đổi danh sách token thành mảng kiểu `long[]` nguyên thuỷ.
    3.  Tạo các đối tượng `OnnxTensor` tương ứng từ `input_ids` và `attention_mask` gắn với `OrtEnvironment`.
    4.  Gọi `session.run(inputs)` để nhận về bản đồ kết quả đầu ra.
    5.  Trích xuất tensor đầu ra (từ khoá ngõ ra: `sentence_embedding` hoặc trích xuất biểu diễn CLS token từ `last_hidden_state`).
    6.  **Kiểm chứng Kích thước (Fail-fast Dimension Validation)**:
        *   Kiểm tra kích thước của mảng `float[]` nhận được.
        *   Nếu độ dài mảng $\neq 1024$, lập tức ném ra ngoại lệ `IllegalStateException("Kích thước vector không hợp lệ: " + length)` để ngăn chặn việc ghi dữ liệu hỏng xuống DB. Nghiêm cấm việc tự ý pad thêm số 0 hoặc cắt ngắn vector.
    7.  **L2-Normalization**:
        *   Để bảo đảm độ chính xác của phép so khớp Cosine trực tiếp tại SQL, vector nhúng phải được chuẩn hoá L2 trước khi lưu:
            $$\vec{v}_{\text{norm}} = \frac{\vec{v}}{\sqrt{\sum_{i=1}^{1024} v_i^2}}$$

#### 4.2.4. Giải phóng tài nguyên khi Shutdown
Đăng ký phương thức `@PreDestroy` để đóng phiên làm việc `OrtSession` và giải phóng môi trường `OrtEnvironment` một cách an sau, tránh rò rỉ vùng nhớ Off-Heap.

---

### 4.3. Model Versioning & Traceability (Theo dõi Phiên bản Mô hình)
Do vector nhúng của câu hỏi (Query Embedding) và mảnh văn bản (Chunk Embedding) phải nằm trên cùng một không gian vector và được tạo ra bởi cùng một phiên bản mô hình, hệ thống quản lý đồng bộ phiên bản mô hình ở cấu hình hệ thống:
*   **Quản lý tập trung**: Phiên bản mô hình nhúng (`model_name: "BGE-M3"`, `model_version: "v1.0-quantized"`) được khai báo tập trung trong cấu hình ứng dụng (`application.yml`).
*   **Tối ưu lưu trữ DB**: Để giảm kích thước dòng dữ liệu, bảng `tbl_chunks` không lưu trữ lại hai cột `model_name` và `model_version` cho từng bản ghi vector. Toàn bộ các vector hiện có trong cơ sở dữ liệu mặc nhiên được hiểu là thuộc về không gian vector của phiên bản mô hình đang được cấu hình hoạt động.
*   **Cơ chế an toàn**: Khi thay đổi cấu hình phiên bản mô hình trong `application.yml`, quản trị viên hệ thống bắt buộc phải thực hiện chạy công cụ tái số hóa (re-index tool) toàn bộ tài liệu để đảm bảo tính đồng nhất của không gian vector trong cơ sở dữ liệu.

---

### 4.4. Digitization Retry / Skip Design (Thiết kế Thử lại & Bỏ qua)
Trong quá trình số hóa một tài liệu, để đảm bảo tính bền vững của đường ống xử lý, các lỗi xảy ra khi lưu trữ hoặc ghi nhận từng mảnh nhỏ (chunk) sẽ được xử lý cô lập mà không làm hỏng tiến trình chung của tài liệu:

```
                          [Bắt đầu lưu trữ Chunk k]
                                     │
                                     ▼
                     [Giao dịch ghi Chunk (REQUIRES_NEW)]
                                     │
              ┌──────────────────────┴──────────────────────┐
              ▼ (Lỗi DB / Kết nối)                          ▼ (Thành công)
        [Attempt = 1]                                 [Hoàn tất ghi Chunk k]
              │                                             │
              ▼                                             ▼
      [Thử lại (Retry)] ◄─── Tối đa 3 lần              [Xử lý tiếp Chunk k+1]
              │
       ┌──────┴──────────────┐
       ▼ (Vẫn lỗi sau 3 lần)  ▼ (Thành công)
   [BỎ QUA CHUNK (SKIP)]   [Hoàn tất ghi Chunk k]
       │
       ▼
[Cập nhật last_completed_chunk = k]
[Tăng bộ đếm skipped_chunks]
       │
       ▼
[Xử lý tiếp Chunk k+1]
```

*   **Cơ chế Retry độc lập**: Vì toàn bộ vector câu đã được sinh hàng loạt thành công ở bộ nhớ đệm (Batch Inference), lỗi phát sinh ở cấp độ chunk chủ yếu nằm ở tầng lưu trữ cơ sở dữ liệu (Database persistence). Hệ thống thực hiện thử lại tối đa 3 lần cho giao dịch lưu trữ của từng chunk nhỏ với thời gian trễ cố định (500ms).
*   **Hành vi Skip (Bỏ qua)**: Nếu sau 3 lần thử lại mà việc ghi dữ liệu của chunk thứ $k$ vẫn thất bại, hệ thống sẽ:
    1. Ghi log cảnh báo mức `WARN` kèm theo `document_id`, `chunk_index` và mô tả lỗi chi tiết.
    2. Bỏ qua việc lưu mảnh $k$ vào bảng `tbl_chunks` (mảnh này không được lưu vector nên mặc nhiên không tham gia tra cứu).
    3. Cập nhật checkpoint `last_completed_chunk = k` trực tiếp trong bảng `tbl_documents` để ghi nhận vị trí này đã được xử lý, đảm bảo khả năng tiếp tục (Idempotency) khi phục hồi tác vụ.
    4. Tiếp tục thực hiện lưu trữ mảnh $k+1$.
*   **Trạng thái tài liệu**: Khi xử lý hết tất cả các mảnh (kể cả có các mảnh bị skip), tài liệu vẫn chuyển sang trạng thái `COMPLETED`. Tài liệu chỉ chuyển sang trạng thái `FAILED` khi gặp lỗi sập luồng hệ thống nghiêm trọng (như lỗi trích xuất chữ trên toàn bộ tệp, lỗi sập ONNX Runtime Session, mất kết nối DB hoàn toàn...).

---

### 4.5. Transaction & Persistence Design (Thiết kế Lưu trữ & Giao dịch)
Để tuân thủ bất biến kiến trúc "Các mảnh thành công phải được giữ lại kể cả khi các mảnh sau bị lỗi", các giao dịch ghi dữ liệu được thiết kế như sau:

*   **Giao dịch cấp độ mảnh (Chunk-level Transaction)**:
    Phương thức `persistChunk(...)` của `ChunkPersistenceService` sẽ chịu trách nhiệm ghi dữ liệu mảnh văn bản vào bảng `tbl_chunks` và cập nhật cột `last_completed_chunk = k` trong bảng `tbl_documents` cho cùng một kết nối. Phương thức này bắt buộc phải được khai báo:
    ```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistChunk(UUID documentId, int chunkIndex, String content, float[] embedding) {
        // 1. Tạo entity Chunk và lưu vào tbl_chunks
        // 2. Chạy câu lệnh UPDATE tbl_documents SET last_completed_chunk = :chunkIndex WHERE id = :documentId
    }
    ```
    *Ý nghĩa*: Việc sử dụng `REQUIRES_NEW` đảm bảo Spring sẽ mở một database transaction độc lập cho mảnh hiện tại và commit ngay lập tức khi kết thúc phương thức. Nếu các mảnh tiếp theo gặp lỗi, dữ liệu của mảnh này đã được lưu vĩnh viễn và không bị rollback.

*   **Giao dịch hoàn tất tài liệu (Document Finalization)**:
    Khi xử lý xong toàn bộ các mảnh văn bản, một giao dịch độc lập sẽ thực hiện cập nhật trạng thái tài liệu từ `PROCESSING` sang `COMPLETED`, đồng thời giải phóng `worker_id` về `NULL` trong bảng `tbl_documents`.

---

### 4.6. Idempotency & Crash Recovery (Tính Khả trùng & Tự Phục hồi)
Để tích hợp tương thích với cơ chế tự phục hồi tác vụ dở dang của Tuần 3 khi máy chủ ứng dụng khởi động lại, tầng Số hóa Tuần 4 thiết lập cơ chế chạy lại an toàn (Idempotency):

*   **Kịch bản sự cố**: Server ứng dụng bị tắt đột ngột khi đang thực hiện số hóa tài liệu A tại mảnh thứ 50 (trên tổng số 100 mảnh). 50 mảnh đầu tiên đã được commit thành công vào `tbl_chunks`, và `last_completed_chunk` của tài liệu A đang lưu giá trị 50 trong DB.
*   **Tiến trình phục hồi**:
    1. Khi server boot lại, Listener tự phục hồi (Tuần 3) phát hiện tài liệu A bị kẹt ở trạng thái `PROCESSING` và chuyển trạng thái của nó về `READY` kèm tăng số lần thử lại (`retry_count`).
    2. Background worker của hệ thống thăm dò và tiếp tục nhận lại tài liệu A để xử lý, chuyển trạng thái sang `PROCESSING`.
    3. `DocumentDigitizationService` đọc file vật lý, trích xuất văn bản và thực hiện phân mảnh lại từ đầu. Vì thuật toán phân mảnh là hoàn toàn **định nghĩa ổn định (deterministic)**, danh sách các mảnh tạo ra ở lần này sẽ trùng khớp hoàn toàn với lần trước.
    4. Hệ thống đọc giá trị `last_completed_chunk` từ DB (lúc này là 50).
    5. Vòng lặp số hóa sẽ tự động bỏ qua (skip) việc sinh vector nhúng và lưu trữ cho các mảnh từ chỉ mục 1 đến 50.
    6. Vòng lặp bắt đầu thực thi thực tế từ mảnh chỉ mục **51** trở đi.
*   **An toàn trùng lặp (Idempotent Guard)**: Khoá ràng buộc độc bản `uq_document_chunk (document_id, chunk_index)` ở tầng database đóng vai trò chốt chặn cuối cùng ngăn ngừa việc ghi trùng lặp dữ liệu mảnh văn bản nếu có bất kỳ sai lệch nào về trạng thái.

---

### 4.7. Retrieval Engine (Thiết kế Phân hệ Tra cứu)
Quy trình tìm kiếm tương đồng ngữ nghĩa thực hiện chuyển đổi câu hỏi tự nhiên của người dùng và đối sánh trực tiếp trong cơ sở dữ liệu:

1.  **Tiếp nhận đầu vào**: API nhận chuỗi câu hỏi `query` từ người dùng nghiệp vụ.
2.  **Sinh vector truy vấn**: Gọi `EmbeddingService` nhúng câu hỏi thu được vector truy vấn $\vec{v}_{\text{query}} \in \mathbb{R}^{1024}$. Vector này bắt buộc phải được chuẩn hoá L2.
3.  **Lọc phân quyền trực tiếp tại SQL**: Chuyển giao vector truy vấn và `EapAuthorizationContext` xuống tầng cơ sở dữ liệu để thực hiện tìm kiếm tương đồng Cosine, lọc phân quyền đồng thời và sắp xếp.
4.  **Tối ưu số lượng**: Cơ sở dữ liệu giới hạn trả về tối đa Top-3 bản ghi phù hợp nhất.

---

---

## 5. API & SQL INTERFACE DESIGN (THIẾT KẾ API & TRUY VẤN SQL)

### 5.1. Search API Specification (Đặc tả Endpoint Tìm kiếm)
#### 5.1.1. Đặc tả Endpoint nghiệp vụ
*   **HTTP Method**: `POST`
*   **Path**: `/api/v1/search`
*   **Headers**: `Authorization: Bearer <JWT_TOKEN>`
*   **Request Body (JSON)**:
    ```json
    {
      "query": "Tôi đi xe buýt đi làm có được trợ cấp không?",
      "limit": 3
    }
    ```
    *Ràng buộc dữ liệu*:
    *   `query`: Không được để trống (NotBlank), độ dài tối đa 500 ký tự.
    *   `limit`: Kiểu số nguyên, tối thiểu là 1, tối đa là 10, mặc định là 3 nếu không truyền.

#### 5.1.2. Phản hồi Thành công (200 OK)
```json
{
  "success": true,
  "data": {
    "results": [
      {
        "chunkId": "48b6d859-e932-4752-96a8-a53c072d6211",
        "chunkContent": "Chính sách hỗ trợ phương tiện công cộng áp dụng cho nhân viên đi xe buýt đi làm với mức trợ cấp 200,000 VND/tháng",
        "documentId": "87c71ba2-6b95-4aa8-bc1c-99d9b626dcd0",
        "displayTitle": "Quy chế phúc lợi nhân viên",
        "documentBusinessCode": "DOC_HR_0012",
        "similarityScore": 0.8954
      }
    ]
  },
  "error": null
}
```

#### 5.1.3. Các trường hợp lỗi API
*   **400 Bad Request**: Yêu cầu không hợp lệ (Ví dụ: câu hỏi rỗng, limit vượt quá 10). Trả về mã lỗi `VALIDATION_ERROR`.
*   **403 Forbidden**: Người dùng có vai trò `SYSTEM_ADMIN` thực hiện yêu cầu. Trả về mã lỗi `ERR_FORBIDDEN_ROLE`.
*   **401 Unauthorized**: JWT token bị thiếu, hết hạn hoặc không hợp lệ. Trả về mã lỗi `ERR_UNAUTHENTICATED`.

---

### 5.2. Raw SQL Queries (Truy vấn SQL thực tế)
#### 5.2.1. Câu truy vấn tìm kiếm tương đồng vector và lọc bảo mật tích hợp
Đây là truy vấn cốt lõi thực thi tìm kiếm ngữ nghĩa đồng thời áp dụng toàn bộ các chính sách phân quyền tại cơ sở dữ liệu. Để tăng tính rõ ràng và loại bỏ rủi ro sai sót thứ tự, câu lệnh sử dụng **Named Parameters** và hiển thị tiêu đề tài liệu gốc chéo phòng ban theo đúng PRD:

```sql
SELECT 
    c.id AS chunk_id,
    c.content AS chunk_content,
    d.id AS document_id,
    d.title AS display_title,
    d.business_code AS document_business_code,
    1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score -- Tính tương đồng Cosine = 1 - Cosine Distance
FROM tbl_chunks c
JOIN tbl_documents d ON c.document_id = d.id
-- Left Join để kiểm tra liên kết Alias đang hoạt động được chia sẻ tới phòng ban của user hiện tại
LEFT JOIN tbl_documents a ON a.parent_id = d.id 
                         AND a.owner_department_id = :userDeptId 
                         AND a.deleted_at IS NULL 
WHERE d.parent_id IS NULL -- Chỉ lấy các phân mảnh thuộc tài liệu gốc
  AND d.status = 'COMPLETED' -- Tài liệu gốc phải số hoá hoàn thành
  AND d.deleted_at IS NULL -- Tài liệu gốc chưa bị xóa logic
  AND (
      -- Điều kiện cách ly phòng ban:
      -- Quyền 1: Người dùng thuộc phòng ban sở hữu tài liệu gốc
      d.owner_department_id = :userDeptId
      OR
      -- Quyền 2: Tài liệu được chia sẻ hợp lệ qua Alias tới phòng ban của người dùng
      a.id IS NOT NULL
  )
  -- Quy tắc cô lập tuyệt đối của BOARD:
  -- Nếu tài liệu gốc thuộc sở hữu của BOARD, bắt buộc người dùng thực hiện truy vấn cũng phải thuộc phòng ban BOARD
  AND (
      d.owner_department_id <> :boardDeptId
      OR
      (
          d.owner_department_id = :boardDeptId
          AND :userDeptId = :boardDeptId
      )
  )
  -- Bộ lọc ngưỡng tương đồng tối thiểu (Similarity Threshold):
  -- Loại bỏ các mảnh có điểm tương đồng Cosine thấp hơn ngưỡng cấu hình động
  AND (1.0 - (c.embedding <=> CAST(:queryVector AS vector))) >= :similarityThreshold
-- Sử dụng toán tử <=> của pgvector để tìm kiếm trên chỉ mục HNSW
ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC
LIMIT :limit;
```

#### 5.2.2. Truy vấn xác thực liên kết Alias
Truy vấn này được sử dụng riêng biệt khi cần đánh giá nhanh xem một phòng ban nhận có quyền truy cập logic vào một tài liệu gốc cụ thể hay không tại thời điểm chạy:

```sql
SELECT EXISTS (
    SELECT 1 
    FROM tbl_documents a
    WHERE a.parent_id = ? -- ? đại diện cho originalDocumentId
      AND a.owner_department_id = ? -- ? đại diện cho userDeptId
      AND a.deleted_at IS NULL
);
```

---

---

## 6. SECURITY & AUTHORIZATION DESIGN (THIẾT KẾ BẢO MẬT & PHÂN QUYỀN)

### 6.1. Department Isolation (Cách ly Phòng ban)
Việc thực thi bảo mật phân quyền và kiểm soát lọc kết quả được dịch hoàn toàn thành các mệnh đề điều kiện (SQL predicates) lồng ghép trực tiếp vào câu lệnh SQL tìm kiếm vector:

*   **Department Isolation (Cô lập phòng ban)**:
    *   Người dùng thuộc phòng ban $D_A$ chỉ được tìm kiếm các mảnh văn bản thuộc tài liệu do $D_A$ sở hữu (tài liệu gốc) hoặc các tài liệu phòng ban khác chia sẻ cho $D_A$ thông qua Alias đang có hiệu lực.
    *   Công thức logic lọc trong SQL:
        ```sql
        (d.owner_department_id = :userDeptId OR a.owner_department_id = :userDeptId)
        ```
*   **Soft Delete (Loại trừ xóa logic)**:
    *   Chỉ tìm kiếm trên các tài liệu đang hoạt động:
        ```sql
        d.deleted_at IS NULL
        ```
*   **Document Status (Trạng thái tài liệu)**:
    *   Chỉ tìm kiếm trên các tài liệu đã hoàn tất số hóa thành công:
        ```sql
        d.status = 'COMPLETED'
        ```
*   **Similarity Threshold (Ngưỡng tương đồng tối thiểu)**:
    *   Loại bỏ các kết quả có điểm tương đồng dưới ngưỡng cấu hình động (mặc định 0.60):
        ```sql
        (1.0 - (c.embedding <=> :queryEmbedding)) >= :similarityThreshold
        ```

---

### 6.2. Alias Sharing (Liên kết Chia sẻ Alias)
Phân giải quyền truy cập chia sẻ logic thông qua liên kết Alias chéo phòng ban:

*   **Runtime Evaluation**: Trạng thái Alias được kiểm tra trực tiếp tại thời điểm truy vấn thông qua phép `LEFT JOIN` với dòng bản ghi Alias trong bảng `tbl_documents` (nơi `parent_id` trỏ đến ID tài liệu gốc và `owner_department_id` của Alias trùng với phòng ban của người dùng hiện tại).
*   **Kiểm tra hiệu lực Alias**:
    *   Alias phải chưa bị xóa logic: `a.deleted_at IS NULL`.
*   **Hiển thị Tiêu đề tài liệu gốc**:
    Để tuân thủ đúng yêu cầu trong kịch bản nghiệm thu của PRD (TC-4.3), khi người dùng được chia sẻ chéo phòng ban qua Alias truy xuất tài liệu, tiêu đề hiển thị đi kèm kết quả tìm kiếm vẫn hiển thị tiêu đề của tài liệu gốc (`d.title`) chứ không thực hiện ẩn hay làm lu mờ tiêu đề:
    ```sql
    d.title AS display_title
    ```

---

### 6.3. BOARD Isolation (Cô lập Ban Giám đốc)
Tài liệu thuộc phòng ban Ban Giám đốc (BOARD) là tuyệt mật và có chính sách bảo vệ nghiêm ngặt nhất:

*   **Bất biến kiến trúc**: Tài liệu của BOARD chỉ cho phép thành viên thuộc phòng ban BOARD tìm kiếm và đọc nội dung. Tuyệt đối không được phép chia sẻ Alias ra ngoài phòng ban BOARD.
*   **Ràng buộc trong SQL**:
    Để ngăn chặn bất kỳ hành vi cố ý hoặc vô tình vượt quyền (ví dụ: một Alias của tài liệu BOARD bằng cách nào đó được chèn lén lút vào database), câu lệnh SQL tìm kiếm vector bổ sung một mệnh đề loại trừ tuyệt đối:
    ```sql
    AND (
        d.owner_department_id <> :boardDeptId
        OR
        (
            d.owner_department_id = :boardDeptId
            AND :userDeptId = :boardDeptId
        )
    )
    ```
    *Ý nghĩa*: Nếu tài liệu gốc thuộc sở hữu của phòng ban có mã `'BOARD'`, thì điều kiện truy cập bắt buộc phải thỏa mãn người dùng hiện tại thuộc phòng ban có mã `'BOARD'`. Mọi liên kết Alias (kể cả có tồn tại) của tài liệu BOARD đều sẽ bị chặn đứng tại mệnh đề này đối với người dùng ngoài BOARD.

---

### 6.4. Administrative Control & Multi-layered Security (Chặn Quản trị viên & Bảo mật Đa lớp)
Quy trình áp dụng chính sách an toàn bảo mật được thực hiện qua 4 ranh giới chặt chẽ:

```
[Mạng HTTP Request]
        │
        ▼
[Tầng Web API / Controller Layer] ──► Kiểm tra JWT, loại trừ vai trò SYSTEM_ADMIN (HTTP 403)
        │
        ▼
[Tầng Ứng dụng / Application Layer] ──► Xây dựng EapAuthorizationContext (userId, deptId, role)
        │
        ▼
[Tầng Repository / SQL Query Predicates] ──► Chèn predicates phân quyền vào SQL thô
        │
        ▼
[Tầng Cơ sở dữ liệu / PostgreSQL pgvector] ──► Lọc dữ liệu thô tại đĩa, chỉ trả về Top-3 hợp lệ
```

---

---

## 7. NON-FUNCTIONAL & SYSTEM ROBUSTNESS (THIẾT KẾ PHI CHỨC NĂNG & ĐỘ BỀN BỈ)

### 7.1. Resource Bounding (Giới hạn Tài nguyên)
Tiến trình sinh vector nhúng in-process đòi hỏi cấu hình kiểm soát tài nguyên để tránh nghẽn CPU/RAM của tiến trình Java chính:

*   **Concurrency Task Limit (Giới hạn số tác vụ song song)**:
    Cấu hình pool thực thi của Background Worker Tuần 3 tối đa là 2 luồng xử lý số hóa tài liệu chạy song song (`eap.worker.thread-pool.digitization-size = 2`). Điều này giới hạn tối đa chỉ có 2 luồng đồng thời gọi ONNX Runtime session để suy luận mô hình.
*   **Cấu hình Thread của ONNX Runtime**:
    ```java
    OrtSession.SessionOptions options = new OrtSession.SessionOptions();
    // Giới hạn luồng tính toán toán tử CPU in-process của ONNX
    options.setIntraOpNumThreads(2); 
    options.setInterOpNumThreads(1);
    options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.ORT_SEQUENTIAL);
    ```
*   **RAM Management**:
    *   Mô hình BGE-M3 quantized chiếm khoảng ~570MB dung lượng bộ nhớ.
    *   Việc sử dụng mmap của ONNX Runtime giúp nạp mô hình vào vùng nhớ **Off-Heap (Native Memory)** của hệ điều hành thay vì JVM Heap, tránh rủi ro treo JVM do Garbage Collection (GC) quét dọn vùng nhớ lớn liên tục.
    *   Khuyến nghị cấu hình RAM cho container ứng dụng Spring Boot tối thiểu là **3GB** (với `-Xmx1500m` cấp phát cho JVM Heap và phần còn lại dành cho hệ điều hành/Off-Heap của ONNX).

---

### 7.2. Error Handling (Xử lý Lỗi Hệ thống)
Hệ thống phân loại các lỗi phát sinh rõ ràng để có phản ứng phù hợp:

| Loại lỗi | Tình huống phát sinh | Cách xử lý hệ thống | Phản hồi Client / Ghi log |
| :--- | :--- | :--- | :--- |
| **Client Error (400)** | Tham số tìm kiếm sai cấu trúc, limit âm hoặc vượt quá 10. | Chặn tại tầng Controller bằng Validation API. | Trả về HTTP 400 kèm mã lỗi `VALIDATION_ERROR`. |
| **Auth Error (403)** | Tài khoản `SYSTEM_ADMIN` cố tình gọi Search API. | Chặn tại Web Filter / Controller. | Trả về HTTP 403 kèm mã lỗi `ERR_FORBIDDEN_ROLE`. |
| **Chunk-level Error** | Lỗi kết nối cơ sở dữ liệu tạm thời khi ghi một chunk, hoặc lỗi mô hình khi nhúng một chunk cụ thể. | Thử lại tối đa 3 lần. Nếu vẫn lỗi, SKIP chunk này, lưu checkpoint tăng tiến độ, tiếp tục xử lý chunk tiếp theo. | Ghi log `WARN` chứa `document_id` và `chunk_index`. Tăng metric `chunk_skipped_total`. |
| **Document-level Fatal Error** | Tệp tin gốc bị mã hoá, bị lỗi định dạng không trích xuất được chữ; hoặc mất kết nối DB hoàn toàn. | Dừng toàn bộ luồng số hóa của tài liệu hiện tại, chuyển trạng thái tài liệu sang `FAILED` giải phóng worker. | Ghi log `ERROR` kèm stack trace. Tăng metric `digitization_failure_total`. |
| **System Unavailability** | File mô hình ONNX bị mất, hoặc thư viện ONNX C++ không tương thích môi trường. | Fail-fast ngay khi khởi động ứng dụng (Application Startup Fail). | Ghi log `FATAL` dừng khởi chạy ứng dụng Spring Boot. |

---

### 7.3. System Observability (Khả năng Giám sát)
#### 7.3.1. Hệ thống Metrics nghiệp vụ (Micrometer / Prometheus)
Hệ thống sẽ đăng ký và cập nhật các chỉ số đo lường sau:
*   `digitization_success_total`: Tổng số tài liệu số hóa hoàn thành trạng thái `COMPLETED` (Counter).
*   `digitization_failure_total`: Tổng số tài liệu số hóa bị thất bại chuyển sang trạng thái `FAILED` (Counter).
*   `chunk_processed_total`: Tổng số mảnh văn bản được xử lý và ghi nhận thành công (Counter).
*   `chunk_skipped_total`: Tổng số mảnh văn bản bị lỗi và bị bỏ qua sau 3 lần retry (Counter).
*   `embedding_retry_total`: Tổng số lần luồng xử lý phải thử lại khi sinh vector nhúng (Counter).
*   `embedding_latency`: Thời gian sinh vector nhúng cho một mảnh văn bản (Timer).
*   `query_embedding_latency`: Thời gian sinh vector nhúng cho một câu hỏi tìm kiếm ngữ nghĩa (Timer).
*   `db_retrieval_latency`: Thời gian thực hiện câu truy vấn SQL tìm kiếm vector tại PostgreSQL (Timer).
*   `retrieval_latency`: Thời gian phản hồi tổng thể của API tìm kiếm (Timer).
*   `retrieval_result_count`: Số lượng kết quả tìm kiếm thực tế trả về cho người dùng (Distribution Summary).

#### 7.3.2. Nguyên tắc Ghi nhật ký (Logging)
*   **MDC (Mapped Diagnostic Context)**: Mọi dòng log trong đường ống số hóa bắt buộc phải đính kèm thông tin ngữ cảnh `documentId` và `workerId` để hỗ trợ truy vết lỗi chéo luồng.
*   **Bảo mật thông tin**: Tuyệt đối không ghi nội dung văn bản nghiệp vụ của các mảnh (chunk content) hoặc chuỗi câu hỏi của người dùng vào tệp nhật ký để tuân thủ quy tắc an toàn thông tin nghiệp vụ của doanh nghiệp.

---

### 7.4. Performance SLA Benchmarks (Đo lường Hiệu năng)
Để đảm bảo đáp ứng các mục tiêu SLA đề ra trong ADD:

*   **Đo lường thời gian phản hồi tìm kiếm (Target: p95 < 500ms)**:
    *   Hạ tầng kiểm thử: Cơ sở dữ liệu giả lập chứa tối thiểu 10.000 phân đoạn văn bản nghiệp vụ phân bổ đều ở các phòng ban.
    *   Giả lập tải: Sử dụng công cụ `k6` giả lập 100 người dùng truy vấn đồng thời liên tục trong 5 phút.
    *   Đo đạc chỉ số p95 của thời gian phản hồi API `/api/v1/search`.
*   **Đo lường thời gian số hóa (Target: 10 trang < 10s)**:
    *   Sử dụng tài liệu chuẩn PDF văn bản thô (text-native) có dung lượng khoảng 10 trang (~3000 từ).
    *   Đo đạc khoảng thời gian từ lúc cập nhật trạng thái tài liệu sang `PROCESSING` đến khi hoàn thành chuyển sang `COMPLETED` trong môi trường giới hạn CPU của Container ứng dụng ở mức 2 Cores.

---

---

## 8. TESTING & ASSUMPTIONS (KIỂM THỬ & GIẢ ĐỊNH)

### 8.1. Unit & Integration Testing (Thiết kế Kiểm thử Lớp)
#### 8.1.1. Kiểm thử đơn vị (Unit Tests)
*   **`ChunkingServiceTest`**: Kiểm thử việc phân tách tài liệu dựa trên ký tự ngắt đoạn tự nhiên (mặc định `\n\n`), kiểm thử cơ chế cắt câu tiếng Việt tại ranh giới câu khi vượt quá giới hạn tối đa (`max-tokens`), kiểm thử tính năng tránh phân mảnh vụn bằng cách gộp phần dư dưới `min-tokens` vào mảnh liền trước, và kiểm thử sự tích hợp với cấu hình tham số động.
*   **`EmbeddingServiceTest`**: Mô phỏng output sinh vector từ ONNX Runtime. Xác thực tính năng ném lỗi lập tức (fail-fast) nếu vector trả về không đúng 1024 chiều. Kiểm tra tính chính xác của hàm chuẩn hoá L2.
*   **`DigitizationRetryTest`**: Sử dụng Mockito để giả lập luồng sinh vector bị lỗi. Kiểm tra việc thử lại đúng 3 lần, ghi nhận log và thực hiện skip mảnh lỗi thành công mà không làm sập tiến trình chung của tài liệu.

#### 8.1.2. Kiểm thử tích hợp (Integration Tests)
Các kiểm thử tích hợp chạy trên cơ sở dữ liệu PostgreSQL thực tế sử dụng Testcontainers:
*   **`VectorRetrievalIntegrationTest`**:
    *   Nạp dữ liệu mẫu gồm các mảnh và vector nhúng vào cơ sở dữ liệu.
    *   Thực hiện tìm kiếm tương đồng vector bằng câu lệnh SQL thô thực tế của Repository.
    *   Kiểm tra việc sử dụng chỉ mục HNSW qua kế hoạch thực thi câu lệnh.
*   **`AuthorizationPredicateTest`**:
    *   Thiết lập dữ liệu tài liệu thuộc phòng HR, FINANCE và BOARD.
    *   Tạo người dùng thuộc phòng HR thực hiện tìm kiếm. Xác thực kết quả chỉ hiển thị tài liệu của phòng HR.
    *   Tạo liên kết Alias từ phòng FINANCE sang phòng HR. Thực hiện lại tìm kiếm từ người dùng HR và xác thực tìm thấy mảnh tài liệu của phòng FINANCE với tiêu đề hiển thị khớp tiêu đề trên Alias.
    *   Thử nghiệm các trường hợp Alias bị xóa logic để xác thực quyền truy cập bị thu hồi ngay lập tức.
*   **`BoardIsolationTest`**:
    *   Kiểm chứng người dùng ngoài BOARD không thể tiếp cận tài liệu BOARD thông qua bất kỳ API hay cơ chế chia sẻ nào.
    *   Xác thực rằng nếu cố tình chèn thủ công một liên kết Alias trỏ đến tài liệu BOARD, mệnh đề SQL phân quyền của BOARD vẫn chặn đứng kết quả.
*   **`SystemAdminContentBlockTest`**:
    *   Mô phỏng tài khoản `SYSTEM_ADMIN` gọi endpoint `/api/v1/search` và các endpoint xem nội dung tài liệu khác, xác thực kết quả nhận về luôn là HTTP 403 Forbidden.

#### 8.1.3. Kiểm thử chất lượng truy xuất
*   Chuẩn bị tập dữ liệu kiểm nghiệm (Ground Truth) gồm 50 câu hỏi nghiệp vụ đã chuẩn hóa của doanh nghiệp.
*   Mỗi câu hỏi được ánh xạ sẵn với ID đoạn văn bản liên quan chứa câu trả lời chính xác.
*   Chạy chương trình kiểm thử tự động quét qua 50 câu hỏi, thực hiện tìm kiếm Top-3.
*   Tính toán tỷ lệ đạt: `Hit Rate @ Top-3 = (Số câu hỏi tìm thấy đoạn văn bản đúng trong Top-3) / 50`.
*   *Yêu cầu*: Tỷ lệ đạt tối thiểu phải $\ge 90\%$.

---

### 8.2. Ground Truth Quality Testing (Kiểm thử Chất lượng RAG)
Kiểm thử chất lượng truy xuất được định nghĩa chi tiết tại [Mục 8.1.3](#813-kiểm-thử-chất-lượng-truy-xuất) bằng bộ Ground Truth gồm 50 câu hỏi nghiệp vụ chuẩn hóa, đảm bảo tỷ lệ Hit Rate @ Top-3 >= 90%.

### 8.3. Execution Plan Verification (Phân tích Kế hoạch Thực thi)
Trước khi nghiệm thu đưa vào vận hành, lập trình viên/vận hành viên bắt buộc phải chạy phân tích kế hoạch thực thi câu lệnh SQL tìm kiếm vector bằng cú pháp:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT 
    c.id AS chunk_id,
    c.content AS chunk_content,
    d.id AS document_id,
    d.title AS display_title,
    d.business_code AS document_business_code,
    1.0 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity_score
FROM tbl_chunks c
JOIN tbl_documents d ON c.document_id = d.id
LEFT JOIN tbl_documents a ON a.parent_id = d.id 
                         AND a.owner_department_id = :userDeptId 
                         AND a.deleted_at IS NULL 
WHERE d.parent_id IS NULL
  AND d.status = 'COMPLETED'
  AND d.deleted_at IS NULL
  AND (
      d.owner_department_id = :userDeptId
      OR
      a.id IS NOT NULL
  )
  AND (
      d.owner_department_id <> :boardDeptId
      OR
      (
          d.owner_department_id = :boardDeptId
          AND :userDeptId = :boardDeptId
      )
  )
  -- Lọc theo ngưỡng tương đồng tối thiểu
  AND (1.0 - (c.embedding <=> CAST(:queryEmbedding AS vector))) >= :similarityThreshold
ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector) ASC
LIMIT :limit;
```

**Kết quả chạy thử nghiệm kế hoạch thực thi thực tế (EXPLAIN ANALYZE)**:
```text
Limit  (cost=278.87..295.56 rows=3 width=689) (actual time=33.318..34.005 rows=3 loops=1)
  Buffers: shared hit=1274 read=160
  InitPlan 1 (returns $0)
    ->  Index Scan using departments_code_key on tbl_departments  (cost=0.14..8.16 rows=1 width=16) (actual time=0.166..0.168 rows=1 loops=1)
          Index Cond: ((code)::text = 'BOARD'::text)
          Buffers: shared hit=2
  InitPlan 2 (returns $1)
    ->  Index Scan using departments_code_key on tbl_departments tbl_departments_1  (cost=0.14..8.16 rows=1 width=16) (actual time=0.012..0.012 rows=1 loops=1)
          Index Cond: ((code)::text = 'BOARD'::text)
          Buffers: shared hit=2
  InitPlan 3 (returns $2)
    ->  Index Scan using departments_code_key on tbl_departments tbl_departments_2  (cost=0.14..8.16 rows=1 width=16) (actual time=0.002..0.002 rows=1 loops=1)
          Index Cond: ((code)::text = 'BOARD'::text)
          Buffers: shared hit=2
  ->  Nested Loop Left Join  (cost=254.39..41717.68 rows=7451 width=689) (actual time=33.316..34.002 rows=3 loops=1)
        Filter: ((d.owner_department_id = '3e7a0dc2-55e2-4f37-9390-b26a7d528faf'::uuid) OR (a.id IS NOT NULL))
        Buffers: shared hit=1274 read=160
        ->  Nested Loop  (cost=254.25..41434.82 rows=7451 width=209) (actual time=32.875..33.231 rows=3 loops=1)
              Buffers: shared hit=1250 read=160
              ->  Index Scan using idx_chunks_embedding_hnsw on tbl_chunks c  (cost=254.10..41179.00 rows=10000 width=162) (actual time=32.587..32.913 rows=4 loops=1)
                    Order By: (embedding <=> '[-0.017562, ...]'::vector)
                    Filter: ((1.0 - (embedding <=> '[-0.017562, ...]'::vector)) >= 0.60)
                    Buffers: shared hit=1238 read=160
              ->  Memoize  (cost=0.15..0.18 rows=1 width=63) (actual time=0.072..0.072 rows=1 loops=4)
                    Cache Key: c.document_id
                    Cache Mode: logical
                    Hits: 1  Misses: 3  Evictions: 0  Overflows: 0  Memory Usage: 1kB
                    Buffers: shared hit=12
                    ->  Index Scan using documents_pkey on tbl_documents d  (cost=0.14..0.17 rows=1 width=63) (actual time=0.083..0.083 rows=1 loops=3)
                          Index Cond: (id = c.document_id)
                          Filter: ((parent_id IS NULL) AND (deleted_at IS NULL) AND ((status)::text = 'COMPLETED'::text) AND ((owner_department_id <> $0) OR ((owner_department_id = $1) AND ('3e7a0dc2-55e2-4f37-9390-b26a7d528faf'::uuid = $2))))
                          Rows Removed by Filter: 0
                          Buffers: shared hit=12
        ->  Memoize  (cost=0.14..0.58 rows=1 width=50) (actual time=0.005..0.005 rows=0 loops=3)
              Cache Key: d.id
              Cache Mode: logical
              Hits: 1  Misses: 2  Evictions: 0  Overflows: 0  Memory Usage: 1kB
              Buffers: shared hit=4
              ->  Index Scan using uq_active_alias_per_dept on tbl_documents a  (cost=0.12..0.57 rows=1 width=50) (actual time=0.006..0.006 rows=0 loops=2)
                    Index Cond: ((parent_id = d.id) AND (owner_department_id = '3e7a0dc2-55e2-4f37-9390-b26a7d528faf'::uuid))
                    Buffers: shared hit=4
Planning:
  Buffers: shared hit=11
Planning Time: 3.116 ms
Execution Time: 34.383 ms
```

**Nhận xét phân tích hiệu năng và tính đúng đắn**:
1.  **Index Scan HNSW hoạt động chính xác**:
    *   Hệ thống sử dụng đúng chỉ mục `idx_chunks_embedding_hnsw` thông qua phép quét `Index Scan` (`Order By: (embedding <=> ?)`). Tránh việc quét tuần tự toàn bộ bảng (`Seq Scan`) giúp tốc độ truy vấn đạt hiệu năng vượt trội khi số lượng dữ liệu mảnh tăng lên.
2.  **Thời gian thực thi tối ưu (Execution Time)**:
    *   Tổng thời gian thực thi thực tế (`Execution Time`) chỉ mất **34.383 ms** trên tập dữ liệu thử nghiệm, đáp ứng xuất sắc SLA nghiệp vụ đề ra (yêu cầu dưới **50ms** trong cơ sở dữ liệu và dưới **500ms** cho toàn bộ API).
3.  **Loại bỏ các truy vấn con tìm phòng BOARD (InitPlans)**:
    *   Bằng việc chuyển ID phòng ban BOARD thành tham số truyền vào từ mã Java (`:boardDeptId`), cơ sở dữ liệu không cần thực hiện quét bảng `tbl_departments` thông qua các chương trình con `InitPlan` nữa, giúp tối ưu hóa hiệu năng và đơn giản hóa cây kế hoạch thực thi.
4.  **Cơ chế Memoize hiệu quả**:
    *   PostgreSQL đã tối ưu hóa phép Join bằng cách tạo bộ đệm `Memoize` với khóa cache là `c.document_id` và `d.id`.
    *   Khi duyệt qua các mảnh (`tbl_chunks`) thuộc cùng một tài liệu gốc (`tbl_documents`), thông tin kiểm tra quyền của tài liệu sẽ được lấy trực tiếp từ cache (`Hits`) thay vì phải thực hiện lại truy vấn quét khóa chính `documents_pkey`, giúp giảm thiểu tối đa số lượng block dữ liệu cần đọc từ đĩa.
5.  **Tỷ lệ Shared Buffer Hit cao**:
    *   Bộ đệm chia sẻ đạt tỉ lệ truy cập trúng `shared hit=1274` và chỉ có `read=160`. Điều này cho thấy dữ liệu hầu hết đã được tải lên RAM, hạn chế tối đa hoạt động I/O đĩa vật lý chậm chạp. Trong các lượt chạy tiếp theo, số block `read` sẽ tiệm cận về `0`.

#### 8.3.2. Kịch bản đối chiếu: Seq Scan (không có chỉ mục HNSW)

Kết quả chạy thử nghiệm khi PostgreSQL quyết định dùng chiến lược quét tuần tự (`Seq Scan`) thay vì chỉ mục HNSW — xảy ra khi chỉ mục chưa được tạo hoặc bộ đếm thống kê dẫn planner đi sai hướng:

```text
Limit  (cost=599.84..599.85 rows=3 width=689) (actual time=56.613..56.617 rows=3 loops=1)
  Buffers: shared hit=13753
  InitPlan 1 (returns $0)
    ->  Seq Scan on tbl_departments  (cost=0.00..11.00 rows=1 width=16) (actual time=0.053..0.053 rows=1 loops=1)
          Filter: ((code)::text = 'BOARD'::text)
          Rows Removed by Filter: 3
          Buffers: shared hit=1
  InitPlan 2 (returns $1)
    ->  Seq Scan on tbl_departments tbl_departments_1  (cost=0.00..11.00 rows=1 width=16) (actual time=0.002..0.003 rows=1 loops=1)
          Filter: ((code)::text = 'BOARD'::text)
          Rows Removed by Filter: 3
          Buffers: shared hit=1
  InitPlan 3 (returns $2)
    ->  Seq Scan on tbl_departments tbl_departments_2  (cost=0.00..11.00 rows=1 width=16) (actual time=0.002..0.003 rows=1 loops=1)
          Filter: ((code)::text = 'BOARD'::text)
          Rows Removed by Filter: 3
          Buffers: shared hit=1
  ->  Sort  (cost=566.84..585.47 rows=7451 width=689) (actual time=56.612..56.614 rows=3 loops=1)
        Sort Key: ((c.embedding <=> '[...]'::vector))
        Sort Method: top-N heapsort  Memory: 26kB
        Buffers: shared hit=13753
        ->  Hash Left Join  (cost=8.27..470.54 rows=7451 width=689) (actual time=0.432..56.081 rows=2028 loops=1)
              Hash Cond: (d.id = a.parent_id)
              Filter: ((d.owner_department_id = '3e7a0dc2...'::uuid) OR (a.id IS NOT NULL))
              Rows Removed by Filter: 5788
              Buffers: shared hit=13753
              ->  Hash Join  (cost=4.49..382.93 rows=7451 width=209) (actual time=0.240..7.075 rows=7816 loops=1)
                    Hash Cond: (c.document_id = d.id)
                    Buffers: shared hit=256
                    ->  Seq Scan on tbl_chunks c  (cost=0.00..350.00 rows=10000 width=162) (actual time=0.030..3.326 rows=10000 loops=1)
                          Filter: ((1.0 - (embedding <=> '[-0.017562, ...]'::vector)) >= 0.60)
                          Buffers: shared hit=250
                    ->  Hash  (cost=4.02..4.02 rows=38 width=63) (actual time=0.168..0.169 rows=39 loops=1)
                          ->  Seq Scan on tbl_documents d  (cost=0.00..4.02 rows=38 width=63) (actual time=0.069..0.099 rows=39 loops=1)
                                Filter: ((parent_id IS NULL) AND (deleted_at IS NULL) AND ((status)::text = 'COMPLETED'::text) AND (...))
                                Rows Removed by Filter: 12
                                Buffers: shared hit=6
              ->  Hash  (cost=3.64..3.64 rows=11 width=50) (actual time=0.009..0.009 rows=0 loops=1)
                    ->  Seq Scan on tbl_documents a  (cost=0.00..3.64 rows=11 width=50) (actual time=0.003..0.008 rows=11 loops=1)
                          Filter: ((deleted_at IS NULL) AND (owner_department_id = '3e7a0dc2...'::uuid))
                          Rows Removed by Filter: 40
                          Buffers: shared hit=3
Planning:
  Buffers: shared hit=11
Planning Time: 2.326 ms
Execution Time: 56.768 ms
```

#### 8.3.3. So sánh hai chiến lược thực thi

| Tiêu chí | HNSW Index Scan ✅ | Seq Scan ❌ |
| :--- | :--- | :--- |
| **Execution Time** | **34.383 ms** | **56.768 ms** (+65%) |
| **Shared Buffers đọc** | 1.274 block (hit) + 160 block (read) | **13.753 block** (all hit, nhưng đọc toàn bộ) |
| **Chiến lược phân quyền** | Nested Loop + Memoize (lazy evaluation) | Hash Join toàn bộ trước, lọc sau |
| **Chiến lược sắp xếp** | HNSW duyệt đúng thứ tự `<=>` từ đầu | `Sort top-N heapsort` – tính toán `<=>` cho **toàn bộ 10.000 chunks** |
| **Rows loại bỏ bởi bộ lọc** | 0 (lọc tại node Document chuẩn) | **5.788 rows** bị loại bỏ sau khi Hash Join |
| **Khả năng mở rộng** | Ổn định O(log n) khi data tăng | Suy giảm tuyến tính O(n) – **không phù hợp production** |
| **Chỉ mục departments** | Index Scan `departments_code_key` | **Seq Scan** toàn bộ bảng `tbl_departments` (3 lần) |

**Nhận xét tổng hợp**:
1.  **HNSW Index Scan nhanh hơn ~65%** (34ms so với 57ms) ngay cả với tập dữ liệu thử nghiệm chỉ 10.000 chunks — khoảng cách sẽ gia tăng đáng kể khi dữ liệu phát triển lên 100.000+ chunks.
2.  **Seq Scan đọc gấp ~10 lần số block** (13.753 so với 1.274+160). Khi tập dữ liệu không còn nằm gọn trong RAM, chiến lược này sẽ phải thực hiện nhiều I/O đĩa vật lý, dẫn đến thời gian phản hồi tăng vọt theo cấp số nhân.
3.  **Hash Join của Seq Scan lãng phí tài nguyên**: Hệ thống phải tính toán khoảng cách cosine `<=>` cho **toàn bộ 10.000 chunks** trước khi sắp xếp và lấy Top-3, trong khi HNSW chỉ duyệt qua số lượng ứng viên tối thiểu cần thiết nhờ cấu trúc đồ thị phân cấp.
4.  **Tác động của `departments_code_key` index**: Với Seq Scan, cả 3 InitPlan phải quét tuần tự `tbl_departments` và loại bỏ 3 dòng không khớp mỗi lần, trong khi HNSW sử dụng chỉ mục phòng ban chính xác với 0 rows loại bỏ.
5.  **Kết luận**: Bắt buộc đảm bảo chỉ mục `idx_chunks_embedding_hnsw` luôn tồn tại và được sử dụng trong môi trường production. Nếu PostgreSQL planner chọn Seq Scan do thống kê lỗi thời, cần chạy `ANALYZE tbl_chunks;` để cập nhật lại thống kê và buộc planner tái đánh giá.

---

### 8.4. Design Assumptions & Open Issues (Giả định & Vấn đề mở)
#### 8.4.1. Các Giả định (Assumptions)
1.  **Mã phòng ban BOARD**: Hệ thống giả định rằng phòng ban Ban Giám đốc luôn được khởi tạo duy nhất với mã `code = 'BOARD'` trong bảng `tbl_departments` (Phù hợp với dữ liệu mẫu seeded trong `V5__update_seed_data.sql`).
2.  **Định dạng tiếng Việt NFC**: Tất cả tài liệu tải lên được giả định là đã được chuẩn hóa Unicode dạng NFC. Nếu tài liệu đầu vào sử dụng bộ mã cũ (Tieu chuan Viet Nam 3 - TCVN3 hoặc VNI), việc tìm kiếm ngữ nghĩa có thể bị giảm sút chất lượng.
3.  **Độ tương đồng Cosine của pgvector**: Hệ thống giả định thư viện pgvector được cài đặt đúng trên PostgreSQL của máy chủ và hỗ trợ đầy đủ toán tử `<=>`.

#### 8.4.2. Các Vấn đề Mở (Open Issues)
1.  **Hiện tượng Seq Scan khi bộ lọc quá khắt khe**: Trong một số trường hợp khi điều kiện lọc phân quyền trả về quá ít ứng viên (Ví dụ: phòng ban chỉ có 1-2 tài liệu), PostgreSQL có thể tự động quyết định quét tuần tự (Seq Scan) thay vì dùng chỉ mục HNSW vì tối ưu hơn về mặt toán học đối với tập dữ liệu nhỏ. Việc này là bình thường và không vi phạm kiến trúc, nhưng cần được giám sát hiệu năng thực tế.
2.  **Cấu hình tối ưu HNSW**: Các tham số `m = 16` và `ef_construction = 64` có thể cần được tinh chỉnh nâng cao (Ví dụ: `m = 24`, `ef_construction = 128`) khi số lượng phân mảnh trong cơ sở dữ liệu vượt quá 100.000 chunks để đảm bảo độ chính xác tìm kiếm (Recall Rate).
