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

### Pha 2: Giao dịch & Đảm bảo Tính nhất quán (Trong Giao dịch)
1.  Bắt đầu giao dịch cơ sở dữ liệu và yêu cầu khóa cố vấn (Advisory Lock) với phạm vi `(phòng ban, mã băm)`.
2.  Thực hiện truy vấn gộp (Double-Check) trong database để kiểm tra lại trạng thái tệp tin.
3.  Nếu phát hiện tệp đã được tạo bởi một yêu cầu trước đó, hủy tệp tạm và trả về siêu dữ liệu cũ cùng cờ trùng lặp.
4.  Nếu chưa tồn tại tệp trong phòng ban, kiểm tra sự tồn tại của tệp vật lý trên hệ thống:
    *   **Đã tồn tại**: Tái sử dụng liên kết tệp vật lý có sẵn, hủy tệp tạm.
    *   **Chưa tồn tại**: Di chuyển tệp tạm vào thư mục lưu trữ chính thức bằng thao tác đổi tên tức thời (atomic rename ở tầng hệ điều hành).
5.  Ghi nhận bản ghi siêu dữ liệu tài liệu vào database.
6.  Hoàn tất giao dịch (Commit), cơ sở dữ liệu tự động giải phóng khóa cố vấn.

---

## 3. Các Quyết định Thiết kế (Design Decisions / ADR)

### 3.1. Quyết định AD-001: Sử dụng PostgreSQL Advisory Lock thay vì Khóa phân tán (Redis Lock)
*   **Context (Bối cảnh)**: Hệ thống cần chạy trên môi trường phân tán đa instance (nhiều API Node phía sau Load Balancer). Do đó, không thể dùng Mutex ở tầng ứng dụng (Java Synchronized). Hệ thống cần một cơ chế khóa phân tán để đồng bộ các tiến trình ghi đồng thời vào DB.
*   **Alternatives (Phương án thay thế)**:
    *   *Redis Lock (Redlock)*: Sử dụng Redis làm máy chủ quản lý khóa.
    *   *Application-level Mutex*: Dùng khóa trong bộ nhớ JVM.
*   **Rationale (Lý do chọn)**:
    *   PostgreSQL đã là cơ sở dữ liệu chính của dự án. Việc sử dụng Advisory Lock tận dụng hạ tầng hiện có, không phát sinh chi phí cài đặt, vận hành hay bảo trì cụm Redis Cluster.
    *   Khóa cố vấn cấp giao dịch (`pg_advisory_xact_lock`) tự động giải phóng hoàn toàn khi transaction kết thúc (dù thành công hay lỗi) hoặc khi kết nối mạng bị đứt đột ngột, loại bỏ hoàn toàn rủi ro deadlock vĩnh viễn mà không cần cơ chế dọn dẹp khóa (watchdog/TTL) phức tạp như Redis.
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

### 3.4. Quyết định AD-004: Sinh Lock ID thông qua hàm băm tích hợp sẵn của PostgreSQL
*   **Context (Bối cảnh)**: Hàm khóa cố vấn của PostgreSQL yêu cầu khóa kiểu số nguyên (`INT` hoặc `BIGINT`). Trong khi đó, định danh tệp tin là chuỗi ký tự (mã băm SHA-256).
*   **Alternatives (Phương án thay thế)**:
    *   Tự tính toán mã băm SHA-256 và cắt mảng byte ở tầng ứng dụng Java để ép kiểu sang số `long`.
*   **Rationale (Lý do chọn)**:
    *   Sử dụng hàm băm tích hợp sẵn `hashtext(concat(owner_department_id, ':', hash))` trực tiếp trong câu lệnh SQL của database. Điều này giúp mã nguồn Java cực kỳ sạch sẽ và giảm thiểu logic tự xử lý.
    *   Mặc dù hàm `hashtext` trả về kiểu số nguyên 32-bit (có tỉ lệ va chạm nhỏ), sự va chạm này hoàn toàn vô hại: nếu hai tệp khác nhau bị va chạm Lock ID, hệ thống chỉ tạm thời xử lý tuần tự hai yêu cầu đó. Khi vào trong khóa, bước Double-Check thực tế và ràng buộc cứng `UNIQUE` của database sẽ đảm bảo dữ liệu không bao giờ bị ghi sai lệch.
*   **Trade-offs & Consequences**:
    *   Đơn giản hóa mã nguồn, đẩy trách nhiệm sinh Lock ID xuống cơ sở dữ liệu.

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

### 3.7. Quyết định AD-007: Cấu trúc lại thứ tự cột của chỉ mục UNIQUE
*   **Context (Bối cảnh)**: Để bảo vệ dữ liệu, hệ thống cần ràng buộc duy nhất đối với cặp `(phòng ban, mã băm)`.
*   **Alternatives (Phương án thay thế)**:
    *   Thiết lập UNIQUE constraint theo thứ tự `(owner_department_id, hash)`.
*   **Rationale (Lý do chọn)**:
    *   Thiết lập index unique theo thứ tự **`(hash, owner_department_id) WHERE deleted_at IS NULL`**.
    *   Do cột `hash` nằm ở vị trí tiền tố, database có thể sử dụng chỉ mục này để tìm kiếm theo cột `hash` mà không cần cung cấp `owner_department_id`.
    *   Nhờ đó, chỉ mục UNIQUE này đóng vai trò kép: vừa là chốt chặn bảo vệ dữ liệu, vừa phục vụ trực tiếp cho câu truy vấn gộp theo `hash` ở Quyết định AD-005. Hệ thống hoàn toàn **loại bỏ được chỉ mục phụ `idx_documents_hash`**, giảm Write Amplification và dung lượng đĩa của DB.
*   **Trade-offs & Consequences**:
    *   Tối ưu hóa tối đa số lượng index của database.

---

## 4. Yêu cầu Triển khai & Hạ tầng (Deployment & Infrastructure Requirements)

Để hệ thống hoạt động ổn định và đáp ứng đúng cam kết SLA, quá trình triển khai thực tế của đội ngũ DevOps phải tuân thủ các chỉ số sau:

### 4.1. Phân vùng lưu trữ dùng chung cho File tạm và File đích (Shared Partition)
*   **Yêu cầu**: Thư mục lưu trữ tạm thời (`/tmp/eap-uploads`) và thư mục lưu trữ chính thức (`/eap-storage`) **bắt buộc phải được cấu hình trên cùng một mount point/partition vật lý** (ví dụ: cùng một ổ EBS hoặc cùng một phân vùng đĩa).
*   **Lý do**: Nếu nằm khác phân vùng ổ đĩa, thao tác `Files.move` của Java sẽ bị hệ điều hành chuyển đổi thành *copy rồi delete*, khiến thời gian xử lý kéo dài và làm mất đi tính nguyên tử (atomic rename).

### 4.2. Cấu hình Connection Pool & Cơ sở dữ liệu cho Tải cao
*   **Ứng dụng (HikariCP)**: Kích thước kết nối tối đa (`maximum-pool-size`) bắt buộc cấu hình $\ge 110$ kết nối khả dụng đối với kịch bản kiểm thử 100 requests đồng thời. connection-timeout thiết lập tối đa 5 giây (5000ms).
*   **PostgreSQL**: Tham số `max_connections` cấu hình $\ge 150$ để đảm bảo đáp ứng đủ số lượng kết nối đồng thời từ các instance ứng dụng và các công cụ giám sát.
