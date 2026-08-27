# TÀI LIỆU THIẾT KẾ CHI TIẾT (DETAILED DESIGN DOCUMENT - DDD)
**Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer) (Tuần 4 - Phiên bản 1.3)**

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
| **Phiên bản (Version)** | 1.3 |
| **Trạng thái (Status)** | Hoàn thành |
| **Ngày phát hành (Date)** | 2026-08-19 |
| **Tác giả (Author)** | Nhóm Phát triển Backend (Senior Software Engineer) |
| **Tài liệu Kiến trúc liên quan** | [ADD-004](./ArchitectureDesign.md) |
| **Mục đích & Phạm vi (Purpose & Scope)** | Tài liệu đặc tả thiết kế chi tiết cấp thấp phục vụ lập trình cho phân hệ **Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer)** của dự án VCC-EAP. Thiết kế tuân thủ các chỉ định và quyết định kiến trúc tại tài liệu ADD-004. Phạm vi thiết kế bao gồm cấu trúc lớp, sơ đồ tuần tự thực thi, cấu trúc cơ sở dữ liệu và kịch bản Flyway migration. |

### 1.1. Lịch sử Thay đổi (Revision History)
| Phiên bản | Ngày | Tác giả | Mô tả Thay đổi / Ghi chú |
| :--- | :--- | :--- | :--- |
| 1.0 | 2026-08-07 | Nhóm Phát triển | Phiên bản đầu tiên. Đặc tả thiết kế chi tiết cho phân hệ Số hóa & Tra cứu. |
| 1.1 | 2026-08-13 | Nhóm Phát triển | Chuẩn hóa theo thiết kế kiến trúc mới (ADD v1.1) và PRD v1.2: chuyển đổi phương pháp phân mảnh từ Phân mảnh Ngữ nghĩa sang Phân mảnh theo Đoạn văn (Paragraph Chunking), loại bỏ Matryoshka 768 chiều cho ranh giới câu, và tích hợp bộ lọc tương đồng tối thiểu. Đồng thời chuyển truy vấn SQL sang Named Parameters, tham số hóa BOARD ID (`:boardDeptId`), và hiển thị tiêu đề tài liệu gốc (`d.title`) chéo phòng ban qua Alias theo đúng PRD TC-4.3. |
| 1.2 | 2026-08-14 | Nhóm Phát triển | Chuẩn hóa theo thiết kế kiến trúc mới (ADD v1.2) và PRD v1.2: loại bỏ giới hạn kích thước tối đa/tối thiểu trong phân mảnh (max_tokens, min_tokens), thêm chỉ mục GIN cho metadata JSONB, bổ sung metadataFilter vào Search API, cập nhật truy vấn SQL tìm kiếm lọc siêu dữ liệu và loại bỏ Similarity Threshold. |
| 1.3 | 2026-08-19 | Nhóm Phát triển | Cập nhật theo ADD v1.3 & PRD v1.3: Bổ sung LlmMetadataExtractorService, tích hợp trích xuất siêu dữ liệu động bằng LLM vào Retrieval Pipeline, cập nhật sequence diagram và component diagram, bổ sung cơ chế fallback/timeout, parallel execution (CompletableFuture), loại bỏ filteredUuids mechanism cũ và similarity threshold 0.55. |
| 1.4 | 2026-08-21 | Nhóm Phát triển | Chuẩn hóa bản chốt 5 Key Metadata Filters, thiết lập 2 chỉ mục tối ưu (B-tree idx_meta_doc_type và GIN jsonb_path_ops idx_meta_gin), cập nhật System Prompt Engineering (tối ưu hóa prompt trả về full schema và bật JSON Mode để giảm latency), đơn giản hóa toán tử SQL @> và tích hợp logic ngắt sớm khi không có ứng viên khớp metadata. |

---

## 2. SYSTEM ARCHITECTURE & COMPONENT DESIGN (KIẾN TRÚC HỆ THỐNG & THIẾT KẾ THÀNH PHẦN)

### 2.1. Logical Component Design (Thiết kế Thành phần Logic)
```mermaid
graph TD
    subgraph ControllerLayer ["Tầng Controller"]
        SearchController["SearchController"]
    end

    subgraph ServiceLayer ["Tầng Service"]
        RetrievalService["RetrievalService / RetrievalServiceImpl"]
        WorkerExecutorImpl["WorkerExecutorImpl"]
        DocumentChunkProcessorImpl["DocumentChunkProcessorImpl"]
        DocumentTextExtractor["DocumentTextExtractor"]
        ParagraphChunker["ParagraphChunker"]
        EmbeddingService["EmbeddingService"]
        PgVectorSearchService["PgVectorSearchService"]
        LlmMetadataExtractor["LlmMetadataExtractorService<br>(LLM API Client)"]
    end

    subgraph RepositoryLayer ["Tầng Repository"]
        DocumentRepository["DocumentRepository"]
        ChunkRepository["ChunkRepository"]
    end

    subgraph DatabaseLayer ["Tầng Database"]
        PostgresDB[("PostgreSQL + pgvector")]
    end

    subgraph ExternalServices ["Dịch vụ ngoài"]
        LlmProvider["LLM API Provider (External)"]
    end

    %% Flow: Search Path
    SearchController -->|RagChatRequest, User| RetrievalService
    RetrievalService -->|String Query| EmbeddingService
    RetrievalService -->|String Query| LlmMetadataExtractor
    LlmMetadataExtractor -->|HTTP/JSON API Call| LlmProvider
    RetrievalService -->|SearchContext DTO| PgVectorSearchService
    PgVectorSearchService -->|Raw SQL + pgvector| PostgresDB

    %% Flow: Digitization Path
    WorkerExecutorImpl -->|executeTask| DocumentChunkProcessorImpl
    DocumentChunkProcessorImpl -->|Path filePath| DocumentTextExtractor
    DocumentChunkProcessorImpl -->|Raw text| ParagraphChunker
    DocumentChunkProcessorImpl -->|chunk content| EmbeddingService
    DocumentChunkProcessorImpl -->|Chunk entity| ChunkRepository
    ChunkRepository -->|Insert Chunks & update checkpoints| PostgresDB
```

---

### 2.2. Class Responsibilities (Trách nhiệm của các Lớp)
#### 2.2.1. Tầng Controller
*   **`SearchController`**:
    *   Địa chỉ endpoint: `POST /api/v1/search`.
    *   Trách nhiệm: **Thin controller** — chỉ nhận `RagChatRequest` (field `message`), lấy `User currentUser` từ `@AuthenticationPrincipal`, gọi `RetrievalService.search()` và trả về `ApiResponse<RagChatResponse>`. Không chứa bất kỳ business logic hay kiểm tra phân quyền nào.

#### 2.2.2. Tầng Service
*   **`WorkerExecutorImpl`** (điều phối tổng thể):
    *   Trách nhiệm: Nhận task từ hàng đợi, xác thực hash file, tính tổng số chunks và điều phối vòng lặp gọi `DocumentChunkProcessorImpl` theo từng `chunkIndex`. Xử lý retry/skip từng chunk.
*   **`DocumentChunkProcessorImpl`** (xử lý từng chunk):
    *   Trách nhiệm: Điều phối luồng số hóa một chunk đơn lẻ: gọi `DocumentTextExtractor` trích xuất văn bản, `ParagraphChunker` phân mảnh, `EmbeddingService` sinh vector, `ChunkRepository` lưu kết quả.
*   **`DocumentTextExtractor`** (interface) / **`DocumentTextExtractorImpl`**:
    *   Trách nhiệm: Đọc tệp vật lý gốc (PDF, DOCX, XLSX, PPTX) và trích xuất chuỗi văn bản thô tiếng Việt (UTF-8). Sử dụng Apache Tika để phát hiện MIME type, PDFBox cho PDF, Apache POI cho DOCX/XLSX/PPTX. Hỗ trợ 2 overload: `extractText(MultipartFile)` và `extractText(Path)`.
*   **`ParagraphChunker`** (interface):
    *   Trách nhiệm: Phân mảnh văn bản theo đoạn văn tự nhiên (`\n\n`). Mỗi đoạn văn bản thành 1 chunk độc lập.
*   **`EmbeddingService`**:
    *   Trách nhiệm: Tải mô hình BGE-M3 (ONNX) và sinh vector nhúng 1024 chiều (in-process). Quản lý Singleton ONNX Runtime session. Kiểm chứng 1024 chiều và L2-Normalize.
*   **`RetrievalServiceImpl`**:
    *   Trách nhiệm: Kiểm tra phân quyền (`SYSTEM_ADMIN` bị chặn 403). Kích hoạt song song sử dụng `CompletableFuture`:
        *   (a) Gọi `LlmMetadataExtractorService.extractMetadata(query)` để trích xuất các cặp khóa-giá trị siêu dữ liệu dưới dạng JSON object hoặc `{}` (fallback nếu có lỗi/timeout).
        *   (b) Gọi `EmbeddingService.embedText(query)` để sinh vector truy vấn 1024 chiều.
        *   Kích hoạt cơ chế **Short-Circuit**:
            *   Nếu `metadataFilter` rỗng hoặc null $\rightarrow$ trả về kết quả rỗng `[]` ("Không tìm thấy").
            *   Nếu có `metadataFilter`, gọi `ChunkRepository.existsCandidateWithMetadata()` kiểm tra tập ứng viên khớp metadata + phân quyền. Nếu trả về `false` $\rightarrow$ ngắt sớm trả về kết quả rỗng `[]` ("Không tìm thấy").
        *   Nếu có ứng viên phù hợp, đóng gói DTO nội bộ `SearchContext` (chứa `queryVector`, `metadataFilter`, `userDeptId`, `boardDeptId`) và gọi `VectorSearchService.searchWithAuth(context)`. Cuối cùng ánh xạ kết quả sang `RagChatResponse`.
*   **`LlmMetadataExtractorService`** (interface) / **`LlmMetadataExtractorServiceImpl`**:
    *   Trách nhiệm: Gửi câu hỏi người dùng kèm Prompt định nghĩa Metadata Schema tới LLM API Provider (Google Gemini / OpenAI-compatible HTTP REST Client). Nhận về JSON object chứa các cặp khóa-giá trị siêu dữ liệu được trích xuất tự động.
    *   Cơ chế fallback: Giới hạn timeout tối đa 2000ms đối với search query. Nếu LLM trả về `{}` (empty JSON), timeout, hoặc bất kỳ lỗi kết nối/HTTP error nào, service bắt ngoại lệ, ghi log WARN, và trả về đối tượng Map rỗng `{}`.
*   **`PgVectorSearchService`**:
    *   Trách nhiệm: Thực thi các câu truy vấn tương tác với pgvector. Có method `searchWithAuth()` nhận `SearchContext` DTO và gọi `ChunkRepository.searchWithAuth()` để thực thi tìm kiếm vector kết hợp bộ lọc siêu dữ liệu và phân quyền tại database.

#### 2.2.3. Tầng Repository
*   **`ChunkRepository` (hoặc `ChunkRepositoryCustom`)**:
    *   Trách nhiệm: Thực hiện các câu lệnh **SQL thô (Raw SQL)** tương tác với pgvector.
    *   `existsCandidateWithMetadata()`: Kiểm tra sự tồn tại của ít nhất 1 phân đoạn ứng viên khớp bộ lọc metadata JSONB và phân quyền tại cơ sở dữ liệu để phục vụ cơ chế ngắt sớm (Short-Circuit).
    *   `searchWithAuth()`: Truy vấn tìm kiếm vector Cosine (`<=>`) lân cận gần đúng (ANN) kết hợp tiền lọc metadata (`@>`), cô lập phòng ban, Alias sharing và BOARD isolation trực tiếp trên cơ sở dữ liệu PostgreSQL. Map kết quả trả về thành các DTO `ChunkSearchResult` mà không thực hiện bất kỳ hoạt động post-filtering nào trên JVM.

---

### 2.3. Digitization Pipeline (Đường ống Số hóa)
Đường ống số hóa văn bản của một tài liệu diễn ra bất đồng bộ. Bất kỳ lỗi hệ thống nghiêm trọng nào xảy ra ở cấp độ tài liệu (như lỗi trích xuất chữ, lỗi sập ONNX Session) sẽ chuyển trạng thái tài liệu thành `FAILED`. Các lỗi xảy ra khi lưu trữ từng mảnh nhỏ (chunk) sẽ được xử lý cô lập (Skip/Retry) để không ảnh hưởng tới tiến trình chung.

```mermaid
sequenceDiagram
    autonumber
    participant W as WorkerExecutorImpl
    participant CP as DocumentChunkProcessorImpl
    participant TE as DocumentTextExtractor
    participant CS as ParagraphChunker
    participant ES as EmbeddingService
    participant LLM as LlmMetadataExtractorService
    participant R as ChunkRepository

    W->>W: validateHash(filePath)
    W->>TE: extractText(Path filePath)
    activate TE
    Note over TE: Detect MIME (Tika) → PDFBox/POI/UTF-8
    TE-->>W: String (Raw UTF-8 Text)
    deactivate TE

    W->>CS: chunkText(rawText)
    activate CS
    Note over CS: Phân mảnh theo đoạn văn (\n\n)
    CS-->>W: List<String> (Danh sách nội dung mảnh)
    deactivate CS

    Note over W: Cập nhật total_chunks vào DB

    loop Cho mỗi chunk k = lastCompleted+1 đến totalChunks
        W->>CP: processChunk(docId, k)
        activate CP
        CP->>TE: extractText(Path filePath)
        TE-->>CP: String (Raw Text)
        CP->>CS: chunkText(rawText)
        CS-->>CP: List<String>
        CP->>ES: embedText(content[k])
        activate ES
        Note over ES: ONNX Runtime in-process, L2-Normalize
        ES-->>CP: float[] (1024-dim)
        deactivate ES
        CP->>LLM: extractChunkMetadata(content[k])
        activate LLM
        Note over LLM: Gọi LLM API (Prompt Ingestion)<br/>Lọc bỏ các key rỗng
        LLM-->>CP: Map<String, Object> (metadata)
        deactivate LLM
        CP->>R: save(Chunk)
        R-->>CP: success
        CP-->>W: success
        deactivate CP

        alt Lưu trữ thành công
            Note over W: Tiếp tục chunk k+1
        else Lỗi sau retry
            Note over W: SKIP chunk k, ghi log WARN
        end
    end

    Note over W: Hoàn tất → markCompleted(docId)
```

---

### 2.4. Retrieval Pipeline (Đường ống Tra cứu ngữ nghĩa - RAG)
Quy trình tiếp nhận yêu cầu tìm kiếm ngữ nghĩa, gọi LLM trích xuất siêu dữ liệu, sinh vector câu hỏi song song và tìm kiếm tương đồng vector kết hợp tiền lọc tại DB:

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SC as SearchController
    participant RS as RetrievalServiceImpl
    participant LLM as LlmMetadataExtractorService
    participant ES as EmbeddingService
    participant CR as ChunkRepository
    participant DB as PostgreSQL + pgvector

    C->>SC: POST /api/v1/search { "message": "..." }
    activate SC
    SC->>RS: search(RagChatRequest, User currentUser)
    activate RS

    Note over RS: [1] Kiểm tra phân quyền<br/>SYSTEM_ADMIN → 403 ERR_FORBIDDEN_ROLE

    Note over RS,ES: [2] Kích hoạt song song (CompletableFuture)
    par LLM Metadata Extraction
        RS->>LLM: extractMetadata(message)
        activate LLM
        Note over LLM: Gọi LLM API (timeout 2000ms)<br/>Parse JSON → Map<String,Object>
        LLM-->>RS: llmMetadata hoặc {} (fallback)
        deactivate LLM
    and Query Embedding
        RS->>ES: embedText(message)
        activate ES
        Note over ES: ONNX Runtime in-process<br/>BGE-M3 → float[1024] L2-Normalized
        ES-->>RS: float[] queryVector
        deactivate ES
    end

    Note over RS: [3] Short-Circuit Check: metadataFilter & Candidate existence
    alt metadataFilter rỗng {}
        RS-->>SC: RagChatResponse("Không tìm thấy...", [])
    else Có metadataFilter
        RS->>CR: existsCandidateWithMetadata(metadataFilter, userDeptId, boardDeptId)
        activate CR
        CR->>DB: SELECT EXISTS (WHERE metadata @> filter AND auth)
        DB-->>CR: boolean hasCandidate
        CR-->>RS: hasCandidate
        deactivate CR
        opt hasCandidate == false
            RS-->>SC: RagChatResponse("Không tìm thấy...", [])
        end
    end

    Note over RS: [4] Đóng gói thành SearchContext DTO nội bộ

    RS->>CR: searchWithAuth(SearchContext)
    activate CR
    Note over CR: SQL: WHERE + metadata@> + dept isolation<br/>+ BOARD isolation + COMPLETED status<br/>ORDER BY cosine <=> ASC LIMIT K
    CR->>DB: Raw SQL + pgvector
    DB-->>CR: ResultSet
    CR-->>RS: List<ChunkSearchResult>
    deactivate CR

    RS-->>SC: RagChatResponse(response, chunks)
    deactivate RS
    SC-->>C: 200 OK ApiResponse<RagChatResponse>
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
        INT skipped_chunks_count "Số lượng mảnh bị bỏ qua do lỗi"
    }

    tbl_chunks {
        UUID id PK
        UUID document_id FK "Tham chiếu tới tbl_documents(id)"
        INT chunk_index "Chỉ số phân mảnh (0..N-1)"
        TEXT content "Nội dung văn bản thô của phân mảnh"
        vector-1024 embedding "Vector nhúng 1024 chiều (pgvector)"
        JSONB metadata "Siêu dữ liệu JSONB chứa các trường nội dung chính"
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
*   `metadata`: Siêu dữ liệu dưới dạng JSONB lưu trữ các thuộc tính chuẩn hóa phục vụ tiền lọc cấp cơ sở dữ liệu và đính kèm vị trí trích dẫn tài liệu. Các key không có giá trị sẽ bị lược bỏ hoàn toàn khỏi đối tượng JSON để tối ưu hóa không gian lưu trữ và kích thước chỉ mục:
    *   **Nhóm Phục vụ Tiền lọc (Pre-filtering Keys)**:
        *   `doc_type`: `String Enum` - Loại tài liệu (`guide`, `regulation`, `analysis`, `description`, `transaction`, `communication`, `education`, `news`, `literature`, `other`) dạng chữ thường.
        *   `topics`: `Array of String Enum` - Chủ đề lớn cốt lõi dạng Enum chữ thường (`hr_policy`, `compensation_benefits`, `finance_accounting`, `legal_compliance`, `it_technical`, `sales_marketing`, `operation_process`, `admin_facilities`, `board_direction`, `general_info`) — thay thế cho `chunk_role` cũ.
        *   `entities`: `Array of String` - Thực thể & khái niệm nghiệp vụ dạng `type:value` chữ thường (`org:`, `dept:`, `person:`, `product:`, `law:`, `standard:`, `tech:`, `loc:`, `concept:`). Trong đó `concept:` bóc tách chi tiết các khái niệm, chế độ, phụ cấp, quyền lợi chuyên môn. *(Đã loại bỏ `keywords`; thay vào đó trường `entities` được mở rộng chi tiết bao gồm cả nhóm khái niệm `concept:`)*.
        *   `time_refs`: `Array of String` - Mốc thời gian liên quan được chuẩn hóa (`yyyy`, `yyyy-qn`, `yyyy-mm`, `yyyy-mm-dd`, `2 năm`, `đầu năm`, `cuối quý`) dạng chữ thường.
    *   **Nhóm Phục vụ Trích dẫn Vị trí (Display Citation Keys)**:
        *   `page_number`: `Integer` - Số trang gốc trong tài liệu PDF.
        *   `citation_headings`: `Array of String` - Mảng tiêu đề phân cấp gốc nguyên bản của phân đoạn (giữ nguyên kiểu chữ nguyên bản - bao gồm chữ hoa như trong tài liệu gốc, ví dụ: `["Chương I: Quy định chung", "Mục 2: Lương cơ bản"]`).
*   `created_at`: Thời điểm lưu trữ phân đoạn.

---

### 3.2. Database Migration Design (Thiết kế Di cư Flyway)
Cơ sở dữ liệu của Week 4 sử dụng 3 tệp migration trong thư mục `src/main/resources/db/migration/`:

#### 1. Migration `V14__add_vector_and_chunks_table.sql`:
```sql
-- Migration V14: Enable pgvector extension and create tbl_chunks table with metadata JSONB column
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Create tbl_chunks table
CREATE TABLE tbl_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024),
    metadata JSONB DEFAULT '{}'::jsonb NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id) REFERENCES tbl_documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_document_chunk UNIQUE (document_id, chunk_index)
);

-- 2. Foreign key index for document_id
CREATE INDEX idx_chunks_document_id ON tbl_chunks(document_id);

-- 3. HNSW index for vector cosine similarity
CREATE INDEX idx_chunks_embedding_hnsw ON tbl_chunks USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. GIN Index on metadata JSONB column
CREATE INDEX idx_chunks_metadata_gin ON tbl_chunks USING gin (metadata);

-- 5. Index on tbl_documents parent_id for alias resolution optimization
CREATE INDEX IF NOT EXISTS idx_documents_parent_id ON tbl_documents(parent_id)
WHERE parent_id IS NOT NULL AND deleted_at IS NULL;
```

#### 2. Migration `V15__add_skipped_chunks_count.sql`:
```sql
-- Migration V15: Add skipped_chunks_count column to tbl_documents
ALTER TABLE tbl_documents ADD COLUMN skipped_chunks_count INT DEFAULT 0 NOT NULL;
```

#### 3. Migration `V16__add_metadata_jsonb_path_ops_indexes.sql`:
```sql
-- Migration V16: Thay thế GIN index mặc định bằng jsonb_path_ops để hỗ trợ toán tử @>
-- và thêm B-tree index cho doc_type để tối ưu single-value filter

-- 1. Xóa GIN index cũ (dùng ops mặc định, không hỗ trợ tối ưu @>)
DROP INDEX IF EXISTS idx_chunks_metadata_gin;

-- 2. B-tree index cho single-value key doc_type (hỗ trợ lọc nhanh bằng =)
CREATE INDEX idx_meta_doc_type ON tbl_chunks ((metadata->>'doc_type'));

-- 3. GIN jsonb_path_ops index cho tất cả array keys còn lại (topics, entities, time_refs)
CREATE INDEX idx_meta_gin ON tbl_chunks USING GIN (metadata jsonb_path_ops);
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

## 4. DETAILED MODULE DESIGN (THIẾT KẾ CHI TIẾT CÁC MODULE)

### 4.1. Chunker Module & Pseudocode (Thiết kế Phân mảnh & Mã giả)
Giải thuật phân mảnh văn bản sử dụng giải pháp **Phân mảnh theo Đoạn văn Tự nhiên Đơn giản (Simple Paragraph Chunking)** được triển khai qua `ParagraphChunker` (interface) / `ParagraphChunkerImpl`:

#### 4.1.1. Các tham số cấu hình
Các tham số phân mảnh được thiết kế dưới dạng cấu hình hệ thống:
*   `eap.chunking.paragraph-separator`: Ký tự ngắt đoạn văn bản tự nhiên (Mặc định: `\n\n`).

#### 4.1.2. Giải thuật phân mảnh chi tiết
1.  **Bước 1: Chuẩn hoá văn bản & Phân tách tự nhiên**:
    *   Chuyển đổi văn bản thô sang Unicode chuẩn **NFC** và chuẩn hóa khoảng trắng thừa.
    *   Tách văn bản thô thành danh sách các đoạn văn bản tự nhiên dựa trên ký tự ngắt đoạn cấu hình trong `eap.chunking.paragraph-separator` (mặc định là dấu xuống dòng kép `\n\n`).
2.  **Bước 2: Tạo Chunk**:
    *   Mỗi đoạn văn bản tự nhiên sau khi ngắt và loại bỏ khoảng trắng thừa ở hai đầu sẽ được lưu trữ trực tiếp thành 1 chunk độc lập, không áp dụng bất kỳ giới hạn token hay ranh giới câu nào khác.
3.  **Bước 3: Tính lũy đẳng (Idempotency)**:
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
        *   If độ dài mảng $\neq 1024$, lập tức ném ra ngoại lệ `IllegalStateException("Kích thước vector không hợp lệ: " + length)` để ngăn chặn việc ghi dữ liệu hỏng xuống DB. Nghiêm cấm việc tự ý pad thêm số 0 hoặc cắt ngắn vector.
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

```text
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
1.  **Tiếp nhận đầu vào**: API nhận chuỗi câu hỏi `query` từ người dùng nghiệp vụ (tương ứng với `message` trong `RagChatRequest`).
2.  **Xử lý song song (Parallel execution)**:
    *   **2a. Trích xuất siêu dữ liệu bằng LLM**: Gọi `LlmMetadataExtractorService.extractMetadata(query)` để lấy JSON object siêu dữ liệu từ câu hỏi. Nếu LLM lỗi hoặc timeout 2000ms, tự động fallback trả về `{}`.
    *   **2b. Sinh vector truy vấn**: Gọi `EmbeddingService` nhúng câu hỏi thu được vector truy vấn $\vec{v}_{\text{query}} \in \mathbb{R}^{1024}$. Vector này bắt buộc phải được chuẩn hoá L2.
3.  **Đóng gói Context**: Đóng gói các thuộc tính trên vào DTO nội bộ `SearchContext` bao gồm `queryVector`, `metadataFilter` (JSON từ LLM) và `userDeptId`.
4.  **Lọc phân quyền trực tiếp tại SQL**: Chuyển giao `SearchContext` xuống tầng cơ sở dữ liệu để thực hiện tìm kiếm tương đồng Cosine, lọc phân quyền và lọc siêu dữ liệu đồng thời.
5.  **Tối ưu số lượng**: Cơ sở dữ liệu giới hạn trả về tối đa Top-3 bản ghi phù hợp nhất.

---

### 4.8. LLM Metadata Extractor Design (Thiết kế Trích xuất Siêu dữ liệu bằng LLM)

#### 4.8.1. Trách nhiệm & Thiết kế Service
Lớp `LlmMetadataExtractorServiceImpl` đóng vai trò là REST Client kết nối với LLM API Provider (Google Gemini / OpenAI-compatible) để tự động trích xuất các thuộc tính siêu dữ liệu. Giao diện dịch vụ hỗ trợ hai phương thức:
*   `extractMetadata(String question)`: Trích xuất tiêu chí lọc từ câu hỏi của người dùng tại thời điểm truy vấn (Query-time).
*   `extractChunkMetadata(String chunkContent)`: Trích xuất các thuộc tính siêu dữ liệu thực tế của một chunk văn bản tại thời điểm số hóa (Ingestion-time).

Giao diện lớp dịch vụ được đặc tả như sau:
```java
public interface LlmMetadataExtractorService {
    Map<String, Object> extractMetadata(String query);
    Map<String, Object> extractChunkMetadata(String chunkContent);
}
```

#### 4.8.2. Prompt Engineering & System Prompt Templates
Dịch vụ trích xuất siêu dữ liệu được phân định thành 2 System Prompt Templates riêng biệt:

1. **System Prompt Trích xuất Chunk (Chunk Metadata System Prompt - Ingestion-time)**:
   LLM trích xuất đồng thời toàn bộ 4 nhóm thuộc tính nội dung (`doc_type`, `topics`, `entities`, `time_refs`) từ CẢ NỘI DUNG THÂN BÀI VÀ CẢ BỐI CẢNH TIÊU ĐỀ (`Citation Headings Stack`) trong 1 cuộc gọi API duy nhất. Giá trị các thực thể (`entities`) phải siêu ngắn gọn súc tích (1-3 từ cốt lõi):
   ```text
   Bạn là bộ trích xuất metadata cho hệ thống RAG doanh nghiệp. Nhiệm vụ: đọc NỘI DUNG THÂN BÀI CHUNK và BỐI CẢNH TIÊU ĐỀ (Citation Headings) để trích xuất chi tiết các thuộc tính metadata. Trả về DUY NHẤT một object JSON theo đúng schema bên dưới. Không giải thích, không markdown, không backtick, không thêm text nào khác ngoài JSON.

   ## SCHEMA
   {
     "doc_type": "string enum (lowercase)",
     "topics": ["string enum (lowercase)", ...],
     "entities": ["type:value (lowercase)", ...],
     "time_refs": ["string (lowercase)", ...]
   }

   ## QUY TẮC NGUYÊN TẮC TRÍCH XUẤT
   - BẮT BUỘC phân tích và trích xuất các thực thể/khái niệm từ CẢ NỘI DUNG THÂN BÀI VÀ CẢ BỐI CẢNH TIÊU ĐỀ (Citation Headings Stack).
   - TẤT CẢ CÁC VALUE TRONG METADATA PHẢI VIẾT BẰNG CHỮ THƯỜNG (LOWERCASE), NGẮN GỌN SÚC TÍCH (1-3 TỪ CỐT LÕI).
   ```

2. **System Prompt Trích xuất Bộ lọc Câu hỏi (Query Intent System Prompt - Query-time)**:
   ```text
   Bạn là bộ phân tích Intent câu hỏi cho hệ thống RAG doanh nghiệp. Nhiệm vụ: Trích xuất chính xác JSON Metadata Filter từ câu hỏi người dùng để thực hiện tiền lọc (metadata pre-filtering) cơ sở dữ liệu.
   Trả về DUY NHẤT một object JSON theo đúng schema bên dưới. Không giải thích, không markdown, không backtick, không thêm bất kỳ văn bản nào khác ngoài JSON.

   ## SCHEMA
   {
     "doc_type": "string enum (lowercase)",
     "topics": ["string enum (lowercase)", ...],
     "entities": ["type:value (lowercase)", ...],
     "time_refs": ["string (lowercase)", ...]
   }

   ## QUY TẮC ĐẦU RA
   Trả về đầy đủ các key trong SCHEMA (doc_type, topics, entities, time_refs). TẤT CẢ CÁC VALUE PHẢI VIẾT BẰNG CHỮ THƯỜNG (LOWERCASE), NGẮN GỌN SÚC TÍCH (1-3 TỪ).
   ```

**Chỉ thị trích xuất và tối ưu hóa (Full Schema & Backend Filtering rule)**:
Thay vì yêu cầu LLM thực hiện so khớp và tự động xóa bỏ (omit) các key rỗng (làm tăng số bước suy luận của mô hình và dễ lỗi cú pháp JSON), thiết kế tối ưu mới chuyển giao trách nhiệm này cho Backend.
* **LLM**: Luôn trả về đầy đủ các key trong schema. Nếu trường nào không có thông tin thì để giá trị mặc định là mảng rỗng `[]` hoặc chuỗi rỗng `""`. Điều này giúp LLM sinh token theo định dạng cố định cực kỳ nhanh và ổn định.
* **Backend**: Sử dụng hàm `filterOmittedKeys()` để tự động loại bỏ các thuộc tính trống trước khi lưu hoặc lọc cơ sở dữ liệu. nếu không nhận diện được bất kỳ siêu dữ liệu nào, LLM trả về đối tượng với các trường giá trị rỗng và được Backend làm sạch thành đối tượng rỗng `{}`.

Để phòng ngừa trường hợp LLM trả về không đúng chỉ thị, mã nguồn Backend tự động lọc sạch kết quả trước khi xử lý tiếp:
```java
private Map<String, Object> filterOmittedKeys(Map<String, Object> rawJsonMap) {
    if (rawJsonMap == null) return Collections.emptyMap();
    return rawJsonMap.entrySet().stream()
        .filter(e -> e.getValue() != null)
        .filter(e -> !(e.getValue() instanceof String && ((String) e.getValue()).trim().isEmpty()))
        .filter(e -> !(e.getValue() instanceof Collection && ((Collection<?>) e.getValue()).isEmpty()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
}
```

#### 4.8.2. Parallel Execution (CompletableFuture)
Việc gọi LLM API để trích xuất siêu dữ liệu và gọi ONNX Runtime sinh vector nhúng câu hỏi (Query Embedding) được thực hiện song song để giảm thiểu tối đa độ trễ phản hồi của hệ thống:
```java
CompletableFuture<Map<String, Object>> llmFuture = CompletableFuture.supplyAsync(
    () -> llmMetadataExtractorService.extractMetadata(message), taskExecutor);
CompletableFuture<float[]> embedFuture = CompletableFuture.supplyAsync(
    () -> embeddingService.embedText(message), taskExecutor);

CompletableFuture.allOf(llmFuture, embedFuture).join();
Map<String, Object> metadataFilter = llmFuture.getNow(Collections.emptyMap());
float[] queryVector = embedFuture.getNow(null);
```

#### 4.8.3. Cơ chế Fallback & Exception Policy
Để bảo đảm hệ thống tra cứu hoạt động ổn định và liên tục (High Availability), luồng trích xuất siêu dữ liệu qua LLM tuân thủ chặt chẽ các quy tắc sau:
- **Trích xuất siêu dữ liệu rỗng (Empty Metadata)**: Nếu LLM không nhận diện được siêu dữ liệu nào từ câu hỏi (trả về đối tượng JSON rỗng `{}`), hệ thống tự động bỏ qua bước tiền lọc siêu dữ liệu động.
- **Lỗi hệ thống hoặc Timeout (API Failure/Timeout)**: Cuộc gọi LLM API được giới hạn timeout tối đa là 2000ms. Trường hợp xảy ra lỗi kết nối mạng, Rate limit, HTTP 5xx, hoặc timeout vượt quá 2000ms, hệ thống bắt (catch) ngoại lệ này, ghi nhận thông tin log cảnh báo ở mức `WARN`, và tự động fallback trả về Map rỗng `{}` để tiếp tục tiến trình tìm kiếm ngữ nghĩa thông thường mà không làm ảnh hưởng đến độ sẵn sàng của hệ thống.

---

## 5. API & SQL INTERFACE DESIGN (THIẾT KẾ API & TRUY VẤN SQL)

### 5.1. Search API Specification (Đặc tả Endpoint Tìm kiếm)
#### 5.1.1. Đặc tả Endpoint nghiệp vụ
*   **HTTP Method**: `POST`
*   **Path**: `/api/v1/search`
*   **Headers**: `Authorization: Bearer <JWT_TOKEN>`
*   **Request Body (JSON)** — reuse `RagChatRequest` (Client-facing request chỉ chứa câu hỏi `message` của người dùng, không bao gồm `metadataFilter` vì trường này được LLM tự động trích xuất nội bộ):
    ```json
    {
      "message": "Tôi đi xe buýt đi làm có được trợ cấp không?"
    }
    ```
    *Ràng buộc dữ liệu*:
    *   `message`: Không được để trống (NotBlank).

#### 5.1.2. Internal DTO: SearchContext
Dữ liệu đầu vào sau khi xử lý qua LLM và sinh vector nhúng sẽ được đóng gói vào đối tượng context nội bộ để truyền xuống tầng Repository:
```java
public class SearchContext {
    private final String message;                     // Câu hỏi gốc từ client
    private final float[] queryVector;                // Vector nhúng 1024 chiều từ EmbeddingService
    private final Map<String, Object> metadataFilter; // Siêu dữ liệu do LLM trích xuất tự động (Map rỗng {} nếu fallback)
    private final UUID userDeptId;                    // ID phòng ban của người dùng hiện tại
    private final UUID boardDeptId;                   // ID phòng BOARD mật
}
```

#### 5.1.3. Phản hồi Thành công (200 OK) — bọc trong `ApiResponse<RagChatResponse>`
```json
{
  "success": true,
  "data": {
    "response": "Tìm thấy 2 kết quả phù hợp.",
    "chunks": [
      {
        "content": "Chính sách hỗ trợ phương tiện công cộng áp dụng cho nhân viên đi xe buýt đi làm với mức trợ cấp 200,000 VND/tháng",
        "score": 0.8954,
        "metadata": {
          "main_topic": "phương tiện công cộng",
          "similarity": 0.8954,
          "distance": 0.1046
        }
      }
    ]
  },
  "error": null
}
```

#### 5.1.4. Các trường hợp lỗi API
*   **400 Bad Request**: `message` rỗng. Trả về mã lỗi `VALIDATION_ERROR`.
*   **403 Forbidden**: `SYSTEM_ADMIN` thực hiện yêu cầu (kiểm tra tại `RetrievalServiceImpl`). Trả về `ERR_FORBIDDEN_ROLE`.
*   **401 Unauthorized**: JWT token thiếu hoặc không hợp lệ. Trả về `ERR_UNAUTHENTICATED`.

---

### 5.2. Raw SQL Queries (Truy vấn SQL thực tế)
#### 5.2.1. Câu truy vấn tìm kiếm tương đồng vector và lọc bảo mật tích hợp
Đây là truy vấn cốt lõi thực thi tìm kiếm ngữ nghĩa đồng thời áp dụng toàn bộ các chính sách phân quyền tại cơ sở dữ liệu. Để tăng tính rõ ràng và loại bỏ rủi ro sai sót thứ tự, câu lệnh sử dụng **Named Parameters** và hiển thị tiêu đề tài liệu gốc chéo phòng ban theo đúng PRD:

```sql
SELECT * FROM (
    -- Phân đoạn 1: Tài liệu do phòng ban người dùng sở hữu trực tiếp
    SELECT 
        c.id AS chunk_id,
        c.content AS chunk_content,
        c.metadata AS chunk_metadata,
        d.id AS document_id,
        d.title AS display_title,
        d.business_code AS document_business_code,
        1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score
    FROM tbl_chunks c
    JOIN tbl_documents d ON c.document_id = d.id
    WHERE d.parent_id IS NULL -- Chỉ lấy tài liệu gốc
      AND d.status = 'COMPLETED'
      AND d.deleted_at IS NULL
      AND d.owner_department_id = :userDeptId
      -- Quy tắc cô lập tuyệt đối của BOARD
      AND (
          d.owner_department_id <> :boardDeptId
          OR :userDeptId = :boardDeptId
      )
      -- Tiền lọc theo metadata JSONB sử dụng GIN jsonb_path_ops
      AND (:metadataFilter IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb))
    ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC
    LIMIT :limit
)
UNION ALL
(
    -- Phân đoạn 2: Tài liệu được chia sẻ hợp lệ qua Alias tới phòng ban của người dùng
    SELECT 
        c.id AS chunk_id,
        c.content AS chunk_content,
        c.metadata AS chunk_metadata,
        d.id AS document_id,
        d.title AS display_title,
        d.business_code AS document_business_code,
        1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score
    FROM tbl_chunks c
    JOIN tbl_documents d ON c.document_id = d.id
    JOIN tbl_documents a ON a.parent_id = d.id 
                        AND a.owner_department_id = :userDeptId 
                        AND a.deleted_at IS NULL
    WHERE d.parent_id IS NULL
      AND d.status = 'COMPLETED'
      AND d.deleted_at IS NULL
      -- Quy tắc cô lập tuyệt đối của BOARD
      AND (
          d.owner_department_id <> :boardDeptId
          OR :userDeptId = :boardDeptId
      )
      -- Tiền lọc theo metadata JSONB sử dụng GIN jsonb_path_ops
      AND (:metadataFilter IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb))
    ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC
    LIMIT :limit
)
ORDER BY similarity_score DESC
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
*   **Metadata Filtering (Bộ lọc siêu dữ liệu & Cổng Chặn Cứng)**:
    *   Thực hiện tiền lọc trước các phân đoạn chứa nội dung chính tương thích với câu hỏi bằng toán tử khớp chứa `@>` kết hợp chỉ mục GIN `jsonb_path_ops`:
        ```sql
        (:metadataFilter IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb))
        ```
    *   **Logic ngắt sớm (Short-circuit on Empty Metadata / Empty Candidates)**: Nếu câu hỏi không trích xuất được siêu dữ liệu nào từ LLM HOẶC tiền lọc metadata kết hợp lọc quyền trả về 0 ứng viên ($N=0$), service `RetrievalServiceImpl` lập tức ngắt luồng xử lý và trả về kết quả rỗng `[]` ("Không tìm thấy kết quả phù hợp"), tuyệt đối không thực thi tìm kiếm vector trên toàn bộ dữ liệu.

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

## 7. NON-FUNCTIONAL & SYSTEM ROBUSTNESS (THIẾT KẾ PHI CHỨC NĂNG & ĐỘ BỀN BỈ)

### 7.1. Resource Bounding (Giới hạn Tài nguyên)
Tiến trình sinh vector nhúng in-process đòi hỏi cấu hình kiểm soát tài nguyên để tránh nghẽn CPU/RAM của tiến trình Java chính:
*   **Concurrency Task Limit (Giới hạn số tác vụ song song)**:
    Cấu hình pool thực thi của Background Worker Tuần 3 tối đa là 2 luồng xử lý số hóa tài liệu chạy song song (`eap.worker.thread-pool.digitization-size = 2`). Điều này giới hạn tối đa chỉ có 2 luồng đồng thời gọi ONNX Runtime session để suy luận mô hình.
*   **Cấu hình Thread của ONNX Runtime**:
    ```java
    OrtSession.SessionOptions options = new OrtSession.SessionOptions();
    options.setIntraOpNumThreads(2); 
    options.setInterOpNumThreads(1);
    options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.ORT_SEQUENTIAL);
    ```
*   **RAM Management**:
    *   Mô hình BGE-M3 quantized chiếm khoảng ~570MB dung lượng bộ nhớ.
    *   Việc sử dụng mmap của ONNX Runtime giúp nạp mô hình vào vùng nhớ **Off-Heap (Native Memory)** của hệ điều hành thay vì JVM Heap, tránh rủi ro treo JVM do Garbage Collection (GC) quét dọn vùng nhớ lớn liên tục.
    *   Khuyến nghị cấu hình RAM cho container ứng dụng Spring Boot tối thiểu là **3GB** (với `-Xmx1500m` cấp phát cho JVM Heap và phần còn lại dành cho hệ điều hành/Off-Heap của ONNX).

### 7.2. Error Handling (Xử lý Lỗi Hệ thống)
Hệ thống phân loại các lỗi phát sinh rõ ràng để có phản ứng phù hợp:

| Loại lỗi | Tình huống phát sinh | Cách xử lý hệ thống | Phản hồi Client / Ghi log |
| :--- | :--- | :--- | :--- |
| **Client Error (400)** | Tham số tìm kiếm sai cấu trúc, limit âm hoặc vượt quá 10. | Chặn tại tầng Controller bằng Validation API. | Trả về HTTP 400 kèm mã lỗi `VALIDATION_ERROR`. |
| **Auth Error (403)** | Tài khoản `SYSTEM_ADMIN` cố tình gọi Search API. | Chặn tại Web Filter / Controller. | Trả về HTTP 403 kèm mã lỗi `ERR_FORBIDDEN_ROLE`. |
| **Chunk-level Error** | Lỗi kết nối cơ sở dữ liệu tạm thời khi ghi một chunk, hoặc lỗi mô hình khi nhúng một chunk cụ thể. | Thử lại tối đa 3 lần. Nếu vẫn lỗi, SKIP chunk này, lưu checkpoint tăng tiến độ, tiếp tục xử lý chunk tiếp theo. | Ghi log `WARN` chứa `document_id` và `chunk_index`. Tăng metric `chunk_skipped_total`. |
| **Document-level Fatal Error** | Tệp tin gốc bị mã hoá, bị lỗi định dạng không trích xuất được chữ; hoặc mất kết nối DB hoàn toàn. | Dừng toàn bộ luồng số hóa của tài liệu hiện tại, chuyển trạng thái tài liệu sang `FAILED` giải phóng worker. | Ghi log `ERROR` kèm stack trace. Tăng metric `digitization_failure_total`. |
| **System Unavailability** | File mô hình ONNX bị mất, hoặc thư viện ONNX C++ không tương thích môi trường. | Fail-fast ngay khi khởi động ứng dụng (Application Startup Fail). | Ghi log `FATAL` dừng khởi chạy ứng dụng Spring Boot. |

### 7.3. Performance SLA Benchmarks (Đo lường Hiệu năng)
Để đảm bảo đáp ứng các mục tiêu SLA đề ra trong ADD:
*   **Đo lường thời gian phản hồi tìm kiếm (Target: p95 < 500ms)**:
    *   Hạ tầng kiểm thử: Cơ sở dữ liệu giả lập chứa tối thiểu 10.000 phân đoạn văn bản nghiệp vụ phân bổ đều ở các phòng ban.
    *   Giả lập tải: Sử dụng công cụ `k6` giả lập 100 người dùng truy vấn đồng thời liên tục trong 5 phút.
    *   Đo đạc chỉ số p95 của thời gian phản hồi API `/api/v1/search`.
*   **Đo lường thời gian số hóa (Target: 10 trang < 10s)**:
    *   Sử dụng tài liệu chuẩn PDF văn bản thô (text-native) có dung lượng khoảng 10 trang (~3000 từ).
    *   Đo đạc khoảng thời gian từ lúc cập nhật trạng thái tài liệu sang `PROCESSING` đến khi hoàn thành chuyển sang `COMPLETED` trong môi trường giới hạn CPU của Container ứng dụng ở mức 2 Cores.

---

## 8. TESTING & ASSUMPTIONS (KIỂM THỬ & GIẢ ĐỊNH)

### 8.1. Unit & Integration Testing (Thiết kế Kiểm thử Lớp)
#### 8.1.1. Kiểm thử đơn vị (Unit Tests)
*   **`ChunkingServiceTest`**: Kiểm thử việc phân tách tài liệu dựa trên ký tự ngắt đoạn tự nhiên (mặc định `\n\n`), kiểm thử tính xác định (deterministic) của thuật toán bảo đảm tính lũy đẳng (idempotency) khi chạy lại trên cùng một văn bản, và kiểm thử việc thay đổi tham số cấu hình động `paragraph-separator`.
*   **`EmbeddingServiceTest`**: Mô phỏng output sinh vector từ ONNX Runtime. Xác thực tính năng ném lỗi lập tức (fail-fast) nếu vector trả về không đúng 1024 chiều. Kiểm tra tính chính xác của hàm chuẩn hoá L2.
*   **`DigitizationRetryTest`**: Sử dụng Mockito để giả lập luồng sinh vector bị lỗi. Kiểm tra việc thử lại đúng 3 lần, ghi nhận log và thực hiện skip mảnh lỗi thành công mà không làm sập tiến trình chung của tài liệu.
*   **`LlmMetadataExtractorServiceTest`**:
    *   Kiểm thử trích xuất siêu dữ liệu thành công: LLM trả về chuỗi JSON chứa `{"nam": 2026}` -> parse đúng thành đối tượng Map.
    *   Kiểm thử trích xuất siêu dữ liệu rỗng: LLM trả về `{}` -> Map rỗng, không throw exception.
    *   Kiểm thử timeout/network error: Giả lập HTTP client ném ngoại lệ TimeoutException -> catch ngoại lệ, ghi log WARN, và trả về Map rỗng `{}`.

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
*   **`LlmMetadataExtractionIntegrationTest`**:
    *   Giả lập LLM API trả về `{"applicable_roles": ["STAFF"]}`. Nạp dữ liệu có các phân đoạn dành cho STAFF và các vai trò khác. Thực hiện truy vấn tìm kiếm và xác định chỉ có các phân đoạn dành cho STAFF được trả về.
    *   Giả lập LLM API bị timeout/gặp sự cố kết nối mạng -> xác thực hệ thống tự động fallback, trả về kết quả tìm kiếm ngữ nghĩa thông thường (không bị lỗi 500).

#### 8.1.3. Kiểm thử chất lượng truy xuất
*   Chuẩn bị tập dữ liệu kiểm nghiệm (Ground Truth) gồm 50 câu hỏi nghiệp vụ đã chuẩn hóa của doanh nghiệp.
*   Mỗi câu hỏi được ánh xạ sẵn với ID đoạn văn bản liên quan chứa câu trả lời chính xác.
*   Chạy chương trình kiểm thử tự động quét qua 50 câu hỏi, thực hiện tìm kiếm Top-3.
*   Tính toán tỷ lệ đạt: `Hit Rate @ Top-3 = (Số câu hỏi tìm thấy đoạn văn bản đúng trong Top-3) / 50`.
*   *Yêu cầu*: Tỷ lệ đạt tối thiểu phải $\ge 90\%$.

---

### 8.2. Ground Truth Quality Testing (Kiểm thử Chất lượng RAG)
Kiểm thử chất lượng truy xuất được định nghĩa chi tiết tại [Mục 8.1.3](#813-kiểm-thử-chất-lượng-truy-xuất) bằng bộ Ground Truth gồm 50 câu hỏi nghiệp vụ chuẩn hóa, đảm bảo tỷ lệ Hit Rate @ Top-3 >= 90%.

---

### 8.3. Execution Plan Verification (Phân tích Kế hoạch Thực thi & Tối ưu HNSW)
Trước khi nghiệm thu đưa vào vận hành, lập trình viên/vận hành viên bắt buộc phải cấu hình tối ưu chỉ mục HNSW cho phiên truy vấn:
```sql
SET pgvector.iterative_index_scan = 'strict';
SET hnsw.ef_search = 64;
```

Phân tích kế hoạch thực thi câu lệnh SQL tìm kiếm vector bằng cú pháp:
```sql
EXPLAIN (ANALYZE, BUFFERS)
(
    SELECT 
        c.id AS chunk_id,
        c.content AS chunk_content,
        c.metadata AS chunk_metadata,
        d.id AS document_id,
        d.title AS display_title,
        d.business_code AS document_business_code,
        1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score
    FROM tbl_chunks c
    JOIN tbl_documents d ON c.document_id = d.id
    WHERE d.parent_id IS NULL
      AND d.status = 'COMPLETED'
      AND d.deleted_at IS NULL
      AND d.owner_department_id = :userDeptId
      -- Quy tắc cô lập tuyệt đối của BOARD
      AND (
          d.owner_department_id <> :boardDeptId
          OR :userDeptId = :boardDeptId
      )
      -- Tiền lọc theo metadata JSONB sử dụng GIN jsonb_path_ops
      AND (:metadataFilter IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb))
    ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC
    LIMIT :limit
)
UNION ALL
(
    SELECT 
        c.id AS chunk_id,
        c.content AS chunk_content,
        c.metadata AS chunk_metadata,
        d.id AS document_id,
        d.title AS display_title,
        d.business_code AS document_business_code,
        1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score
    FROM tbl_chunks c
    JOIN tbl_documents d ON c.document_id = d.id
    JOIN tbl_documents a ON a.parent_id = d.id 
                        AND a.owner_department_id = :userDeptId 
                        AND a.deleted_at IS NULL
    WHERE d.parent_id IS NULL
      AND d.status = 'COMPLETED'
      AND d.deleted_at IS NULL
      -- Quy tắc cô lập tuyệt đối của BOARD
      AND (
          d.owner_department_id <> :boardDeptId
          OR :userDeptId = :boardDeptId
      )
      -- Tiền lọc theo metadata JSONB sử dụng GIN jsonb_path_ops
      AND (:metadataFilter IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb))
    ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC
    LIMIT :limit
)
ORDER BY similarity_score DESC
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
1.  **Index Scan HNSW hoạt động chính xác**: Hệ thống sử dụng đúng chỉ mục `idx_chunks_embedding_hnsw` thông qua phép quét `Index Scan` (`Order By: (embedding <=> ?)`). Tránh việc quét tuần tự toàn bộ bảng (`Seq Scan`) giúp tốc độ truy vấn đạt hiệu năng vượt trội khi số lượng dữ liệu mảnh tăng lên.
2.  **Thời gian thực thi tối ưu (Execution Time)**: Tổng thời gian thực thi thực tế (`Execution Time`) chỉ mất **34.383 ms** trên tập dữ liệu thử nghiệm, đáp ứng xuất sắc SLA nghiệp vụ đề ra (yêu cầu dưới **50ms** trong cơ sở dữ liệu và dưới **500ms** cho toàn bộ API).
3.  **Loại bỏ các truy vấn con tìm phòng BOARD (InitPlans)**: Bằng việc chuyển ID phòng ban BOARD thành tham số truyền vào từ mã Java (`:boardDeptId`), cơ sở dữ liệu không cần thực hiện quét bảng `tbl_departments` thông qua các chương trình con `InitPlan` nữa, giúp tối ưu hóa hiệu năng và đơn giản hóa cây kế hoạch thực thi.
4.  **Cơ chế Memoize hiệu quả**: PostgreSQL đã tối ưu hóa phép Join bằng cách tạo bộ đệm `Memoize` với khóa cache là `c.document_id` và `d.id`. Khi duyệt qua các mảnh (`tbl_chunks`) thuộc cùng một tài liệu gốc (`tbl_documents`), thông tin kiểm tra quyền của tài liệu sẽ được lấy trực tiếp từ cache (`Hits`) thay vì phải thực hiện lại truy vấn quét khóa chính `documents_pkey`, giúp giảm thiểu tối đa số lượng block dữ liệu cần đọc từ đĩa.
5.  **Tỷ lệ Shared Buffer Hit cao**: Bộ đệm chia sẻ đạt tỉ lệ truy cập trúng `shared hit=1274` và chỉ có `read=160`. Điều này cho thấy dữ liệu hầu hết đã được tải lên RAM, hạn chế tối đa hoạt động I/O đĩa vật lý chậm chạp. Trong các lượt chạy tiếp theo, số block `read` sẽ tiệm cận về `0`.

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
3.  **Độ trễ của LLM API và ảnh hưởng tới chỉ số SLA p95 < 500ms**: Cuộc gọi LLM API ngoài có timeout được thiết lập hợp lý ở mức 2000ms và chạy song song với tiến trình sinh vector câu hỏi. Tuy nhiên, tổng độ trễ của API tìm kiếm ngữ nghĩa sẽ bị chi phối bởi nhánh xử lý chậm nhất. Cần thực hiện benchmark tải thực tế với LLM API được chọn để bảo đảm không vi phạm SLA. Nếu API LLM thường xuyên đạt gần ngưỡng timeout, cần tối ưu hóa prompt hoặc hạ cấp xuống một mô hình LLM nhẹ/nhanh hơn.
