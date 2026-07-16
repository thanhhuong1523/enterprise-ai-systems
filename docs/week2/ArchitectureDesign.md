# Tài liệu Thiết kế Kiến trúc (Architecture Design) - Tuần 2
## Giải pháp Chống trùng lặp & Xử lý Ghi đồng thời

---

## 1. Kiến trúc Tổng thể & Các tầng Component (Overall Architecture & Layers)

Hệ thống VCC-EAP áp dụng mô hình **Kiến trúc Sạch (Clean Architecture)** nhằm đảm bảo tính phân tách độc lập giữa các thành phần hệ thống.

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
4.  Tính toán mã băm kết thúc, hệ thống thực hiện một truy vấn kiểm tra nhanh (Fast-Check) để xác định tệp đã tồn tại trong phòng ban chưa. Nếu đã tồn tại, hủy tệp tạm và trả kết quả trùng ngay lập tức.

### Pha 2: Khóa cố vấn, Giao dịch & Đảm bảo tính nhất quán
1.  Yêu cầu khóa cố vấn (Advisory Lock) ngoài giao dịch với phạm vi `(phòng ban, mã băm)` thông qua kết nối cơ sở dữ liệu riêng biệt.
2.  Thực hiện truy vấn gộp (Double-Check) ngoài giao dịch để kiểm tra lại trạng thái tệp tin. Nếu phát hiện tệp đã tồn tại, tự động nhảy sang bước giải phóng khóa.
3.  Bắt đầu giao dịch cơ sở dữ liệu (Transaction).
4.  Kiểm tra sự tồn tại của tệp vật lý trên hệ thống:
    *   **Đã tồn tại**: Tái sử dụng liên kết tệp vật lý có sẵn.
    *   **Chưa tồn tại**: Di chuyển tệp tạm vào thư mục lưu trữ chính thức bằng thao tác đổi tên tức thời (atomic rename ở tầng hệ điều hành).
5.  Ghi nhận bản ghi siêu dữ liệu tài liệu vào database.
6.  Hoàn tất giao dịch (Commit).
7.  Giải phóng khóa cố vấn ngoài giao dịch ở khối `finally`.

---

## 3. Các Quyết định Thiết kế (Design Decisions / ADR)

### 3.1. Quyết định AD-001: Sử dụng PostgreSQL Advisory Lock ngoài Transaction thay vì Khóa trong Transaction
*   **Context (Bối cảnh)**: Hệ thống cần chạy trên môi trường phân tán đa instance. Việc thực hiện lock cố vấn bên trong giao dịch DB gây ra hiện tượng chiếm giữ kết nối DB lâu, dẫn đến cạn kiệt connection pool (HikariCP Starvation) dưới tải cao.
*   **Alternatives (Phương án thay thế)**:
    *   *Redis Lock*: Đòi hỏi vận hành và duy trì thêm cụm Redis.
    *   *In-Transaction Lock (pg_advisory_xact_lock)*: Gây nghẽn kết nối DB nghiêm trọng dưới tải cao.
*   **Rationale (Lý do chọn)**:
    *   Thực hiện cơ chế khóa cố vấn session-level (`pg_advisory_lock`) ngoài giao dịch và giải phóng tường minh bằng `pg_advisory_unlock` ở khối `finally`.
    *   Cơ chế lock ngoài giao dịch giải phóng kết nối giao dịch chính về pool sớm, giúp triệt tiêu hoàn toàn rủi ro connection starvation. 
    *   Trong trường hợp server ứng dụng bị crash đột ngột, PostgreSQL tự động dọn dẹp các session bị mất kết nối mạng và giải phóng toàn bộ khóa cố vấn session-level liên quan, loại bỏ hoàn toàn nguy cơ deadlock vĩnh viễn.
*   **Trade-offs & Consequences**:
    *   Mỗi yêu cầu đang xếp hàng chờ khóa sẽ chiếm giữ một kết nối cơ sở dữ liệu vật lý trong connection pool. Hệ quả là kích thước connection pool ở tầng ứng dụng và cấu hình số lượng kết nối tối đa ở DB phải được thiết lập đủ lớn để tránh nghẽn luồng.

### 3.2. Quyết định AD-002: Thiết lập Phạm vi Khóa theo Phòng ban và Mã băm tệp
*   **Context (Bối cảnh)**: Khi người dùng tải lên tệp, hệ thống cần chống trùng lặp dữ liệu và áp dụng cơ chế lưu trữ tối ưu toàn cục (Single Instance Storage - SIS).
*   **Alternatives (Phương án thay thế)**:
    *   *Khóa toàn cục (Global Lock) trên mã băm tệp*: Khóa toàn bộ hệ thống đối với tệp tin đó.
*   **Rationale (Lý do chọn)**:
    *   Ràng buộc chống trùng lặp bản ghi chỉ áp dụng trong phạm vi từng Phòng ban (hai phòng ban khác nhau được quyền sở hữu hai bản ghi độc lập cho cùng một nội dung tệp).
    *   Việc khóa ở phạm vi `(phòng ban, mã băm)` giúp tăng tối đa băng thông xử lý. Các phòng ban khác nhau tải lên cùng một tệp tin tại cùng một thời điểm sẽ không bị nghẽn luồng hoặc phải xếp hàng chờ đợi nhau.
    *   Điểm tranh chấp duy nhất ở mức toàn cục là việc ghi tệp vật lý lên đĩa (SIS) được giải quyết triệt để ở tầng hệ điều hành bằng cơ chế đặt tên file theo hash kết hợp thao tác đổi tên tức thời (atomic rename), do đó không cần khóa toàn cục ở tầng ứng dụng.
*   **Trade-offs & Consequences**:
    *   Giảm thiểu tối đa phạm vi ảnh hưởng của khóa, nâng cao thông lượng của toàn hệ thống.

### 3.3. Quyết định AD-003: Áp dụng cơ chế Fast-Check trước khi yêu cầu Khóa
*   **Context (Bối cảnh)**: Khi xảy ra thói quen click đúp hoặc dưới tải cao, nhiều request trùng lặp được gửi lên liên tục. Nếu tất cả đều đi thẳng vào hàng đợi khóa cố vấn, hệ thống sẽ bị tuần tự hóa toàn bộ và nghẽn tài nguyên kết nối, dẫn đến vi phạm cam kết SLA phản hồi dưới 15 giây.
*   **Alternatives (Phương án thay thế)**:
    *   Đi thẳng vào transaction và yêu cầu khóa cố vấn mà không kiểm tra trước.
*   **Rationale (Lý do chọn)**:
    *   Bước kiểm tra nhanh (Fast-Check) ngoài khóa giúp lọc toàn bộ các yêu cầu trùng lặp đến muộn khi tệp đã được ghi nhận thành công từ trước. Các yêu cầu này được trả kết quả thành công ngay lập tức mà không cần kết nối vào transaction hay xếp hàng chờ khóa.
*   **Trade-offs & Consequences**:
    *   Tầng ứng dụng phải thực hiện thêm 1 câu truy vấn SELECT nhanh, nhưng đổi lại thông lượng hệ thống tăng vọt dưới tải cao và đảm bảo an toàn cho SLA 15 giây.

### 3.4. Quyết định AD-004: Sinh Lock ID thông qua hàm băm tích hợp sẵn của PostgreSQL (hashtextextended 64-bit)
*   **Context (Bối cảnh)**: Hàm khóa cố vấn của PostgreSQL yêu cầu khóa kiểu số nguyên (`INT` hoặc `BIGINT`). Trong khi đó, định danh tệp tin là chuỗi ký tự (mã băm SHA-256).
*   **Alternatives (Phương án thay thế)**:
    *   Tự tính toán mã băm SHA-256 và cắt mảng byte ở tầng ứng dụng Java để ép kiểu sang số `long`.
    *   Sử dụng hàm băm 32-bit `hashtext` tích hợp của PostgreSQL.
*   **Rationale (Lý do chọn)**:
    *   Sử dụng hàm băm tích hợp sẵn `hashtextextended(concat(owner_department_id, ':', hash), 0)` trực tiếp trong câu lệnh SQL của database. Điều này giúp mã nguồn Java cực kỳ sạch sẽ và giảm thiểu logic tự xử lý.
    *   Hàm `hashtextextended` trả về kiểu số `BIGINT` 64-bit, giúp mở rộng không gian khóa và giảm thiểu tối đa tỷ lệ va chạm Lock ID so với hàm `hashtext` 32-bit (từ tỉ lệ $1/2^{32}$ xuống $1/2^{64}$). Sự va chạm Lock ID (nếu có) vẫn hoàn toàn vô hại nhờ Double-Check và UNIQUE index.
*   **Trade-offs & Consequences**:
    *   Đơn giản hóa mã nguồn, đẩy trách nhiệm sinh Lock ID xuống cơ sở dữ liệu và giảm thiểu rủi ro va chạm khóa xuống gần như bằng 0.

### 3.5. Quyết định AD-005: Sử dụng Truy vấn Aggregate để gộp hai truy vấn thành một
*   **Context (Bối cảnh)**: Hệ thống cần thực hiện hai mục đích: (1) kiểm tra trùng lặp hoạt động trong phòng ban hiện tại, và (2) kiểm tra đường dẫn file vật lý cũ nhất trên toàn hệ thống để tái sử dụng (SIS). Thiết kế ban đầu sử dụng hai câu truy vấn tuần tự gây bất đối xứng hiệu năng.
*   **Alternatives (Phương án thay thế)**:
    *   *Option A*: Truy vấn lấy danh sách tài liệu trùng hash rồi lọc trong bộ nhớ ứng dụng Java.
    *   *Option B*: Sử dụng truy vấn aggregate để trả về đúng 1 dòng phẳng duy nhất chứa đủ trạng thái logic.
*   **Rationale (Lý do chọn)**:
    *   Hệ thống lựa chọn **Option B (SQL Aggregate)**. Việc gộp thành đúng 1 câu truy vấn aggregate lọc theo `hash` giúp giảm số lượng DB round-trips từ 2 xuống 1 ở cả hai bước Fast-Check và Double-Check.
    *   Tập kết quả trả về luôn là đúng 1 dòng phẳng với 3 cột logic, giúp mã nguồn ở tầng Service cực kỳ tường minh và loại bỏ toàn bộ vòng lặp xử lý logic thủ công trong RAM ứng dụng.
*   **Trade-offs & Consequences**:
    *   Câu truy vấn SQL aggregate sẽ phức tạp hơn một chút, đòi hỏi hiểu biết về các hàm aggregate như `bool_or` và `array_agg` của PostgreSQL.

### 3.6. Quyết định AD-006: Ghi tệp tạm ngoài Transaction và đổi tên nguyên tử trong Khóa
*   **Context (Bối cảnh)**: Thao tác ghi tệp nhị phân có dung lượng lớn (lên tới 50MB) lên đĩa cứng tốn nhiều thời gian I/O. Nếu thực hiện thao tác này khi đang giữ khóa cố vấn, transaction sẽ kéo dài, chiếm dụng kết nối DB và làm treo toàn bộ các request xếp hàng sau.
*   **Alternatives (Phương án thay thế)**:
    *   Thực hiện toàn bộ quá trình tải lên và ghi file vật lý bên trong giao dịch DB có giữ khóa.
*   **Rationale (Lý do chọn)**:
    *   Tách biệt hoàn toàn phần I/O ghi đĩa chậm ra ngoài phạm vi giao dịch. Luồng dữ liệu được tải lên và ghi vào một tệp tạm thời ngoài transaction.
    *   Trong transaction có giữ khóa cố vấn, hệ thống chỉ thực hiện câu truy vấn gộp nhanh và thao tác di chuyển tệp tạm sang tệp đích chính thức bằng phương pháp đổi tên tức thời (atomic rename ở tầng OS). Thao tác đổi tên này độc lập với kích thước tệp và diễn ra trong thời gian dưới 1ms, giúp giải phóng khóa cố vấn ngay lập tức.
*   **Trade-offs & Consequences**:
    *   Cần thiết lập cơ chế dọn dẹp các tệp tạm thời bị bỏ rơi (ví dụ: khi node ứng dụng bị tắt đột ngột trước khi bắt đầu transaction).

### 3.7. Quyết định AD-007: Sử dụng đồng thời hai chỉ mục tối ưu hóa cho hai phạm vi nghiệp vụ
*   **Context (Bối cảnh)**: Để bảo vệ dữ liệu, hệ thống cần ràng buộc duy nhất đối với cặp `(phòng ban, mã băm)`. Đồng thời, hệ thống cần thực hiện tìm kiếm toàn cục theo `hash` để tái sử dụng tệp vật lý (Single Instance Storage - SIS) mà không phân biệt trạng thái xóa mềm.
*   **Alternatives (Phương án thay thế)**:
    *   Chỉ sử dụng 1 chỉ mục UNIQUE B-Tree duy nhất trên `(hash, owner_department_id)`.
*   **Rationale (Lý do chọn)**:
    *   Hệ thống quyết định sử dụng **hai chỉ mục**:
        1.  `CREATE UNIQUE INDEX uq_documents_hash_dept ON documents(hash, owner_department_id) WHERE deleted_at IS NULL;`
        2.  `CREATE INDEX idx_documents_hash ON documents(hash);`
    *   *Lý do tách biệt*: Chỉ mục UNIQUE là chỉ mục bán phần (partial index) lọc theo điều kiện `WHERE deleted_at IS NULL` để cho phép người dùng tải lên lại một tệp đã bị xóa mềm. Tuy nhiên, do là partial index, nó không thể phục vụ cho các truy vấn tìm kiếm toàn cục theo `hash` mà không có điều kiện `deleted_at IS NULL` (luồng tìm kiếm file vật lý để tái sử dụng - SIS luôn quét toàn bộ các bản ghi bao gồm cả các bản ghi đã xóa mềm). Do đó, chỉ mục phụ `idx_documents_hash` là bắt buộc để tối ưu hóa truy vấn tìm kiếm toàn cục theo `hash`.
*   **Trade-offs & Consequences**:
    *   Tăng thêm 1 chỉ mục phụ (idx_documents_hash), nhưng đảm bảo hiệu năng tối ưu và tận dụng được chỉ mục cho cả hai luồng nghiệp vụ khác nhau (chống trùng phòng ban và tái sử dụng vật lý toàn cục).
    *   Chỉ mục UNIQUE đóng vai trò là "lá chắn cứng" ở tầng DB giúp triệt tiêu hoàn toàn rủi ro rò rỉ dữ liệu trùng lặp (Data Integrity Leak) kể cả khi có API mới phát triển trong tương lai hoặc thao tác SQL thủ công bypass qua cơ chế kiểm soát của ứng dụng.

---

## 4. Yêu cầu Triển khai & Hạ tầng (Deployment & Infrastructure Requirements)

Để hệ thống hoạt động ổn định và đáp ứng đúng cam kết SLA, quá trình triển khai thực tế của đội ngũ DevOps phải tuân thủ các chỉ số sau:

### 4.1. Phân vùng lưu trữ dùng chung cho File tạm và File đích (Shared Partition)
*   **Yêu cầu**: Thư mục lưu trữ tạm thời (ví dụ: `/eap-storage/tmp`) và thư mục lưu trữ chính thức (`/eap-storage`) **bắt buộc phải được cấu hình trên cùng một mount point/partition mạng dùng chung** (ví dụ: cùng một ổ AWS EFS hoặc cùng một thư mục NFS).
*   **Lý do**: Trong môi trường phân tán đa node, `/eap-storage` bắt buộc là một Network File System dùng chung, còn `/tmp` cục bộ nằm trên từng node ứng dụng. Nếu lưu tệp tạm ở `/tmp` cục bộ rồi di chuyển tới `/eap-storage`, hệ điều hành sẽ phải chuyển thao tác đổi tên (rename) thành thao tác *copy rồi delete* chéo mount point, làm kéo dài thời gian giữ giao dịch và mất đi tính nguyên tử (atomic rename). Cấu hình tệp tạm nằm tại thư mục con của ổ đĩa mạng dùng chung (như `/eap-storage/tmp`) sẽ đảm bảo thao tác `Files.move` diễn ra tức thời (<1ms) và an toàn tuyệt đối.

### 4.2. Cấu hình Connection Pool & Cơ sở dữ liệu cho Tải cao
*   **Ứng dụng (HikariCP)**: Kích thước kết nối tối đa (`maximum-pool-size`) bắt buộc cấu hình $\ge 110$ kết nối khả dụng đối với kịch bản kiểm thử 100 requests đồng thời. connection-timeout thiết lập tối đa 5 giây (5000ms).
*   **PostgreSQL**: Tham số `max_connections` cấu hình $\ge 150$ để đảm bảo đáp ứng đủ số lượng kết nối đồng thời từ các instance ứng dụng và các công cụ giám sát.
