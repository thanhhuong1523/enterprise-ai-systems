# Báo cáo Đánh giá Thiết kế Hệ thống - Tuần 1

**Dự án:** VCC Enterprise AI Knowledge Platform (VCC-EAP)  
**Tác giả review:** Mentor (AI Assistant)  
**Tài liệu đánh giá:** `PRD.md`, `architecture_design.md`, `detailed_design.md`

---

## 1. Đánh giá Tổng quan (Overall Assessment)

Sinh viên đã hoàn thành đầy đủ và chi tiết các tài liệu thiết kế kỹ thuật theo yêu cầu của Tuần 1. Cách tiếp cận theo **Lean Layered Architecture (KISS)** là rất thực tế, giúp giảm bớt sự cồng kềnh (over-engineering) không cần thiết ở giai đoạn khởi đầu dự án.

Các quy tắc nghiệp vụ quan trọng như **BOARD Protection**, **Anti-Chaining**, và **Unique Alias** đã được thiết lập rõ ràng và chính xác.

---

## 2. Đánh giá đối chiếu Cam kết Vận hành (SLA Compliance)

Dưới đây là bảng đánh giá chi tiết thiết kế của sinh viên so với các cam kết vận hành bắt buộc (SLA) quy định cho Tuần 1:

| Chỉ số SLA yêu cầu | Thiết kế của Sinh viên | Đánh giá Đạt/Không đạt | Phân tích chi tiết |
| :--- | :--- | :--- | :--- |
| **1. Cô lập dữ liệu**:<br>Khả năng rò rỉ dữ liệu chéo giữa các phòng ban = **0%**. | Sử dụng Hibernate `@Filter` tự động cho `OriginalDocument` và `AliasDocument`. | **Không đạt** (Lỗi logic hệ thống) | **Lỗi Hibernate Filter**: Khi HR truy cập file Finance qua Alias, bộ lọc trên `OriginalDocument` ép điều kiện `owner_department_id = HR` sẽ lọc mất file Finance. Luồng chia sẻ Alias bị lỗi (trả về rỗng). Nếu sửa bằng cách tắt Filter thủ công sẽ vi phạm nguyên lý *Secure by Default* và dễ gây rò rỉ dữ liệu chéo phòng ban. |
| **2. Tối ưu hóa tài nguyên**:<br>Kiểm tra loại tài liệu (Original/Alias) tiệm cận **0ms**, không làm tăng I/O hay Index lookup. | Tách làm 2 bảng riêng biệt và sử dụng UUIDv4 ngẫu nhiên; tách endpoints API. | **Không đạt** (Vi phạm nguyên lý tối ưu hóa vật lý) | **Lỗi thiết kế tách bảng**: Khi chỉ có một ID tài liệu, hệ thống buộc phải truy vấn cả 2 bảng hoặc quét Index DB để phân biệt loại tài liệu. Không thể đạt 0ms ở mức DB. Giải pháp đúng là nhúng loại tài liệu vào bit cuối cùng (LSB) của ID để kiểm tra bằng phép toán bit trong RAM (0ms, 0 I/O). |
| **3. Tính sẵn sàng API**:<br>Tài liệu API chuẩn hóa, hoạt động đúng 100% các luồng CRUD. | Định nghĩa chi tiết các API endpoints nghiệp vụ, cấu trúc Request/Response DTOs rõ ràng. | **Đạt** (Mức thiết kế) | Thiết kế API RESTful tốt, phân tách rõ ràng. Cần kiểm chứng qua mã nguồn thực tế ở pha chạy thử nghiệm. |

---

## 3. Các điểm tốt (Strengths)

*   **Bảo mật Multi-tenant chuẩn**: Đã trích xuất thông tin phòng ban người dùng (`ownerDepartmentId`) từ Security Context thông qua JWT Token thay vì tin tưởng dữ liệu truyền lên từ Request Body (vượt qua bẫy bảo mật IAM thông thường).
*   **Bảo vệ dữ liệu Ban Giám đốc**: Cơ chế chặn đứng việc tạo Alias trỏ tới tài liệu BOARD ở tầng Validator hoạt động đúng nghiệp vụ.
*   **Ràng buộc Unique Alias ở DB**: Sử dụng Unique Index có điều kiện (`uq_active_alias_per_dept`) để đảm bảo không bị trùng lặp liên kết Alias đang hoạt động chéo phòng ban.
*   **Audit Log bất biến**: Cấu hình bảng `audit_logs` chỉ cho phép ghi mới (INSERT) và truy vấn (SELECT), không có API chỉnh sửa hay xóa log.

---

## 4. Các lỗi thiết kế nghiêm trọng cần khắc phục (Critical Flaws)

### Lỗi 1: Tê liệt luồng Resolve Alias do Hibernate Filter
*   **Hiện trạng**: Sinh viên định nghĩa `@Filter` tự động trên thực thể `OriginalDocument` với điều kiện `owner_department_id = :userDeptId`.
*   **Vấn đề**: Khi nhân viên phòng HR truy cập tài liệu của Finance qua liên kết Alias (đã được Finance chia sẻ hợp lệ), Hibernate Filter sẽ tự động chèn thêm điều kiện lọc `AND original_document.owner_department_id = 'HR'` vào câu query Join SQL. Vì tài liệu gốc thuộc Finance, câu truy vấn sẽ **không trả về kết quả** (bị lọc mất). Luồng chia sẻ Alias bị tê liệt hoàn toàn.
*   **Giải pháp**: Thiết kế bảng gộp `documents` với trường phân biệt loại tài liệu và áp dụng điều kiện lọc kết hợp phép toán OR như hướng dẫn của Mentor:
    ```sql
    (owner_department_id = :userDeptId AND parent_id IS NULL) 
    OR (parent_id IS NOT NULL AND parent_id IN (
        SELECT id FROM documents WHERE owner_department_id = :userDeptId
    ))
    ```

### Lỗi 2: Phân mảnh dữ liệu & Chưa tối ưu hóa nhận diện loại tài liệu (SLA 0ms)
*   **Hiện trạng**: Sinh viên tách thành 2 bảng `original_documents` và `alias_documents`, sử dụng UUIDv4 ngẫu nhiên.
*   **Phân tích chi tiết các Phương án Thiết kế**:

    #### Phương án A: Tách 2 bảng + UUIDv4 ngẫu nhiên (Hiện trạng của học viên)
    *   **Cơ chế**: Khi chỉ có một ID tài liệu đầu vào (ví dụ kiểm toán hoặc xử lý chung), hệ thống bắt buộc phải truy vấn cả 2 bảng (hoặc dùng câu lệnh `UNION`) để phân biệt loại tài liệu.
    *   **Đánh giá**: **Rất tệ**. Tốn 2 phép quét Index DB, tăng I/O đĩa và tốn kết nối cơ sở dữ liệu. Vi phạm trực tiếp cam kết SLA về hiệu năng.

    #### Phương án B: Gộp 1 bảng + Không dùng Bitwise (Thiết kế DB Chuẩn hóa)
    *   **Cơ chế**: Gộp chung vào một bảng `documents` với quan hệ tự liên kết đệ quy:
        *   Nếu `parent_id IS NULL` $\rightarrow$ Original Document.
        *   Nếu `parent_id IS NOT NULL` $\rightarrow$ Alias Document (trỏ tới `documents(id)`).
    *   **Cách truy cập**: Sử dụng 1 câu query duy nhất kết hợp `LEFT JOIN` để lấy tài liệu bất kể loại nào:
        ```sql
        SELECT doc.id, COALESCE(doc.file_reference, parent.file_reference) AS file_ref
        FROM documents doc
        LEFT JOIN documents parent ON doc.parent_id = parent.id
        WHERE doc.id = :documentId AND doc.deleted_at IS NULL;
        ```
    *   **Đánh giá**: **Tốt cho truy cập dữ liệu** (chỉ tốn 1 query duy nhất trên Primary Key). Tuy nhiên, khi cần kiểm tra nhanh logic nghiệp vụ ở tầng ứng dụng (ví dụ check cấm Alias lồng nhau - Anti-Chaining), hệ thống vẫn phải thực hiện 1 truy vấn xuống DB để check cột `parent_id`, gây tốn kết nối DB và phát sinh độ trễ mạng (~1ms - 5ms).

    #### Phương án C: Gộp 1 bảng + Kết hợp Bitwise LSB (Thiết kế Kỹ sư Hệ thống Tối ưu)
    *   **Cơ chế**: Sử dụng bảng gộp giống Phương án B, đồng thời nhúng loại tài liệu vào **bit cuối cùng (Least Significant Bit - LSB)** của UUID khi sinh ID (bit cuối = 0 là Original, = 1 là Alias).
    *   **Cách kiểm tra ở Java**:
        ```java
        public boolean isAlias(UUID id) {
            return (id.getLeastSignificantBits() & 1L) == 1L;
        }
        ```
    *   **Đánh giá**: **Tối ưu tuyệt đối**. 
        *   Vừa giải quyết được bài toán truy cập dữ liệu nhanh bằng 1 query duy nhất nhờ bảng gộp.
        *   Vừa kiểm tra loại tài liệu trực tiếp trên RAM của Java trong **0ms** (mất <1 nanosecond, 0 I/O, 0 kết nối DB) để chặn đứng các request sai luật nghiệp vụ trước khi chúng chạm tới cơ sở dữ liệu.

### Lỗi 3: Khó khăn khi tích hợp RAG & Vector Search (Tuần 4 & 5)
*   Việc tách bảng khiến bảng lưu trữ vector chunks (`vector_chunks`) sẽ gặp khó khăn lớn khi thiết lập khóa ngoại (Polymorphic Association). Câu truy vấn Hybrid Search kết hợp phân quyền phòng ban sẽ trở nên phức tạp và hiệu năng kém.
*   Nên gộp chung về một bảng `documents` duy nhất để tạo một điểm truy cập và lọc bảo mật đồng nhất.

---

## 5. Định hướng cải tiến cho Tuần 2

1.  Tái cấu trúc: Gộp thực thể `OriginalDocument` và `AliasDocument` vào chung một bảng `documents` (sử dụng kế thừa Single Table hoặc cấu trúc tự liên kết đệ quy).
2.  Viết thuật toán sinh ID nhúng bit loại tài liệu (LSB).
3.  Áp dụng bộ lọc Hibernate Filter ở mức bảng gộp sử dụng điều kiện `OR` kết hợp.