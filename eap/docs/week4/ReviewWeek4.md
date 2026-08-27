# BÁO CÁO REVIEW TÀI LIỆU THIẾT KẾ KIẾN TRÚC TUẦN 4
**Phân hệ:** Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer)  
**Dự án:** VCC Enterprise Archive Platform (VCC-EAP)  
**Nhánh Git:** `week4/basicrag`  
**Ngày review:** 2026-08-13  
**Tài liệu đánh giá:** [architecture_design.md](file:///f:/Workplace/vcc/intern/student_repo/eap/docs/week4/architecture_design.md) (ADD-004 v1.1), [PRD.md](file:///f:/Workplace/vcc/intern/student_repo/eap/docs/week4/PRD.md) (PRD-004 v1.1), [DetailedDesign.md](file:///f:/Workplace/vcc/intern/student_repo/eap/docs/week4/DetailedDesign.md) (DD-EAP-W4-001 v1.1).

---

## 1. Tóm tắt Phạm vi Kiến trúc

Tài liệu thiết kế kiến trúc Tuần 4 đặc tả **Tầng Tra cứu Tri thức (Retrieval Layer)** trong mô hình Basic RAG với các thông số chính:
- **Phạm vi xử lý**: Tiếp nhận tài liệu ở trạng thái `PROCESSING` $\rightarrow$ Trích xuất văn bản thô $\rightarrow$ Phân mảnh theo đoạn văn $\rightarrow$ Sinh vector nhúng BGE-M3 (1024 chiều) $\rightarrow$ Lưu trữ `pgvector` kèm `metadata JSONB` $\rightarrow$ Cung cấp API tìm kiếm ngữ nghĩa theo phòng ban/Alias.
- **Ranh giới công nghệ**: Spring Boot Monolith, PostgreSQL + `pgvector`, ONNX Runtime in-process. Không sử dụng LLM Generation (Chatbot), Re-ranking, Hybrid Search hoặc OCR.

---

## 2. Phân tích Kỹ thuật & Đánh đổi (Technical Trade-offs)

### 2.1. Chiến lược Phân mảnh Văn bản (Paragraph Chunking - ADR-004-1)
- **Cơ chế**: Tách đoạn văn tự nhiên theo ký tự `\n\n`. Nếu đoạn văn $> 1000$ tokens, cắt cứng tại ranh giới câu (`.`, `?`, `!`). Nếu phần dư $< 100$ tokens, gộp vào chunk liền trước.
- **Ưu điểm kỹ thuật**: Đơn giản, độ phức tạp tính toán $O(N)$ theo độ dài văn bản, không tốn tài nguyên CPU/RAM để tính toán tương đồng vector giữa các câu trong bộ nhớ JVM.
- **Hạn chế & Rủi ro**: 
  - Phụ thuộc vào chất lượng định dạng văn bản gốc. Với các tài liệu không có dấu ngắt đoạn `\n\n` chuẩn (như PDF trích xuất bị mất định dạng), thuật toán sẽ phụ thuộc hoàn toàn vào việc cắt cứng theo ranh giới câu.
  - Phân mảnh theo đoạn văn không đảm bảo tính đồng nhất tuyệt đối về mặt ngữ nghĩa (semantic coherence) so với các phương pháp phân mảnh dựa trên phân tích cấu trúc tài liệu phức tạp.

### 2.2. Lọc Phân quyền tại Tầng Database (SQL-level Filtering - ADR-004-4)
- **Cơ chế**: Nhúng trực tiếp các mệnh đề kiểm tra quyền (`department_id`, Alias sharing, cô lập BOARD, `is_deleted = false`, `status = COMPLETED`) vào câu lệnh SQL truy vấn vector Cosine.
- **Ưu điểm kỹ thuật**: Ngăn ngừa rò rỉ dữ liệu lên bộ nhớ JVM, loại bỏ rủi ro danh sách kết quả rỗng (empty candidate list) sau khi lọc post-filtering trên Java.
- **Hạn chế & Rủi ro**:
  - Truy vấn SQL phức tạp kết hợp chỉ mục HNSW vector search với các mệnh đề lọc có tính chọn lọc cao (low predicate selectivity) có thể khiến PostgreSQL Optimizer chuyển sang quét tuần tự (Sequential Scan), làm tăng thời gian phản hồi vượt quá SLA 500ms.

### 2.3. Sinh Vector Nhúng Cục bộ In-Process (ONNX Runtime - ADR-004-3)
- **Cơ chế**: Chạy mô hình BGE-M3 trực tiếp trong tiến trình JVM bằng ONNX Runtime C++ Native library.
- **Ưu điểm kỹ thuật**: Không phụ thuộc API bên ngoài, đảm bảo dữ liệu không ra khỏi hạ tầng nội bộ, giảm độ trễ mạng (network latency).
- **Hạn chế & Rủi ro**:
  - **Tranh chấp CPU**: Tiến trình sinh vector chạy chung tài nguyên với luồng Web API (Tomcat). Thiết kế đã khắc phục một phần bằng cách giới hạn `intra_op_num_threads` và hạ `Thread.MIN_PRIORITY`, nhưng khi tải tăng cao vẫn có nguy cơ gây ảnh hưởng tới độ trễ phản hồi API.
  - **Quản lý Bộ nhớ Off-Heap**: ONNX C++ cấp phát bộ nhớ ngoài JVM Heap trực tiếp từ RAM vật lý của hệ điều hành Host. Nếu máy chủ host có dung lượng RAM vật lý khiêm tốn hoặc không cài đặt Swap, việc chỉ giới hạn JVM Heap (`-Xmx`) sẽ không ngăn được việc tổng dung lượng bộ nhớ tiến trình vượt quá RAM thực tế của máy chủ, dẫn tới nguy cơ bị OS OOM Killer chấm dứt tiến trình `java`.

### 2.4. Xử lý Lỗi Cô lập theo Chunk (Isolated Failure Strategy)
- **Cơ chế**: Thử lại tối đa 3 lần cho mỗi chunk. Nếu vẫn thất bại, bỏ qua (SKIP) chunk đó, ghi nhật ký cảnh báo và tiếp tục chuyển tài liệu sang `COMPLETED` sau khi xử lý xong các chunk còn lại.
- **Ưu điểm kỹ thuật**: Giúp tiến trình số hóa không bị dừng đột ngột (pipeline block) đối với các tài liệu lớn chứa một vài đoạn văn bản bị lỗi lưu trữ cục bộ.
- **Hạn chế & Rủi ro**:
  - Người dùng không có cơ chế nhận biết tài liệu đã bị thiếu một số đoạn nội dung do bị skip (chỉ có log cảnh báo ở hệ thống), dẫn đến kết quả tìm kiếm có thể bị sót thông tin mà không có thông báo cho giao diện người dùng.

---

## 3. Đánh giá Mức độ Tuân thủ Mô hình C4 (C4 Model Standard Evaluation)

| Cấp độ C4 | Vị trí thể hiện | Mức độ Tuân thủ | Phân tích Chi tiết |
| :--- | :--- | :--- | :--- |
| **C1 — System Context** | `architecture_design.md` (Mục 5.1) | **Đạt chuẩn** | Phân định rõ Ranh giới Hệ thống (`SystemEAP`), xác định đủ các Tác nhân người dùng (`Employee`, `BoardUser`, `SysAdmin`) và tương tác cấp cao. |
| **C2 — Container** | `architecture_design.md` (Mục 5.2) | **Đạt chuẩn** | Bóc tách chính xác các đơn vị triển khai vật lý (`React Web App`, `Spring Boot Monolith`, `NFS File Storage`, `PostgreSQL + pgvector`), chỉ định rõ công nghệ và giao thức truyền thông (`HTTP REST API`, `JDBC / SQL`, `File I/O`). |
| **C3 — Component** | `architecture_design.md` (Mục 5.3) | **Đạt chuẩn** | Phân rã thành phần logic bên trong Spring Boot Monolith (`Web/API Layer`, `Retrieval Service`, `Embedding Engine`, `Digitization Pipeline`, `Persistence Layer`). |
| **C4 — Code / Class** | `DetailedDesign.md` (Mục 2, 3, 4, 5) | **Đạt chuẩn** | Được bố trí đúng tài liệu (đặt tại DDD thay vì nhồi vào ADD). Cung cấp chi tiết Class Diagram, Sequence Diagram cho Ingestion/Retrieval Pipeline, Schema ERD và DDL SQL `V14`. |

---

## 4. Kiểm tra Tính Nhất quán Giữa Các Tài liệu (PRD - ADD - DDD)

| Tiêu chí | Trạng thái đồng nhất | Ghi chú kỹ thuật |
| :--- | :--- | :--- |
| **Ngưỡng tương đồng (Threshold)** | **Đồng nhất** | PRD, ADD, DDD đều quy định ngưỡng tối thiểu cố định (mặc định `0.60`). |
| **Cột Metadata JSONB** | **Đồng nhất** | PRD (FR-4.7), ADD (Mục 10.2), DDD (Mục 3) và Migration `V14` đều quy định lưu trữ `metadata JSONB` cho `tbl_chunks` kèm chỉ mục GIN `idx_chunks_metadata_gin`. |
| **Tên tài liệu hiển thị qua Alias** | **Đồng nhất** | DDD v1.1 đã cập nhật câu lệnh SQL lấy tiêu đề tài liệu gốc `d.title` đúng theo kịch bản TC-4.3 của PRD. |
| **Bảo mật SYSTEM_ADMIN** | **Đồng nhất** | Cả 3 tài liệu đều thống nhất tước quyền tìm kiếm/xem nội dung của tài liệu với vai trò `SYSTEM_ADMIN` (Trả về 403 Forbidden). |
| **SLA Tìm kiếm & Số hóa** | **Đồng nhất** | SLA Tìm kiếm p95 $< 500\text{ms}$, SLA Số hóa $< 10\text{s}$ đối với tài liệu 10 trang. |

---

## 5. Các Khuyến nghị Kỹ thuật Cụ thể cho Triển khai Mã nguồn

1. **Quản lý Bộ nhớ Native Off-Heap trên Host OS**:
   - Khi chạy trực tiếp dạng tiến trình Java (không dùng Docker), cần tính toán sao cho RAM vật lý khả dụng của máy chủ đủ đáp ứng tổng: `JVM Heap (-Xmx)` + `ONNX Native Memory (~1.5GB)` + `Hệ điều hành Host`.
2. **Cấu hình Tối ưu Truy vấn HNSW trên PostgreSQL**:
   - Cần kiểm tra và bật tham số `SET pgvector.iterative_index_scan = 'strict'` hoặc `'relaxed'` trên PostgreSQL (phiên bản pgvector 0.7+) để chỉ mục HNSW không bị vô hiệu hóa khi kết hợp với các điều kiện lọc phòng ban và Alias.
3. **Theo dõi Các Chunk bị Skip**:
   - Bổ sung một trường metadata (ví dụ `skipped_chunks_count`) trong bảng lưu trữ trạng thái tài liệu để ghi nhận số lượng chunk bị bỏ qua. Việc này giúp đội ngũ vận hành và người dùng có thể tra cứu được mức độ đầy đủ của tài liệu đã số hóa.
4. **Kiểm tra Tokenizer Tiếng Việt**:
   - Bộ đếm token trong `ChunkingService` cần tương thích với Tokenizer của BGE-M3 (WordPiece/SentencePiece) để tránh tình trạng tính sai số lượng token khiến đoạn văn bị vượt quá 1000 tokens khi đưa vào mô hình nhúng.
