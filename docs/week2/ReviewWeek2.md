# BÁO CÁO ĐÁNH GIÁ THIẾT KẾ CHI TIẾT TUẦN 2
**Dự án:** VCC Enterprise AI Knowledge Platform (VCC-EAP)  
**Người đánh giá:** Antigravity (AI Tech Lead / Architect)  
**Đối tượng đánh giá:** Tài liệu Thiết kế Kiến trúc & Chi tiết - Tuần 2 (Xử lý Trùng lặp & Tải đồng thời)  

---

## 1. Phân tích các Giải pháp Thiết kế Đề xuất (Technical Analysis)

Tài liệu thiết kế Tuần 2 của sinh viên đề xuất các giải pháp kỹ thuật sau:

1.  **Cơ chế khóa đồng bộ**: Sử dụng PostgreSQL Advisory Lock (`pg_advisory_xact_lock`) để đồng bộ hóa các yêu cầu ghi dữ liệu đồng thời trên môi trường phân tán nhiều ứng dụng chạy song song.
2.  **Quy trình tải lên hai pha (Two-Phase Upload)**:
    *   *Pha 1 (Ngoài giao dịch)*: Nhận luồng dữ liệu, ghi file tạm thời ra đĩa, tính toán mã băm SHA-256 (1-pass), và chạy bộ lọc kiểm tra nhanh (Fast-Check).
    *   *Pha 2 (Trong giao dịch)*: Mở giao dịch, gọi khóa cố vấn, kiểm tra lại (Double-Check), kiểm tra và di chuyển file vật lý (atomic rename), ghi metadata vào DB, kết thúc giao dịch và giải phóng khóa.
3.  **Thiết kế chỉ mục**: Sử dụng một chỉ mục duy nhất cho mục đích kép là UNIQUE constraint và tìm kiếm trùng lặp: `uq_documents_hash_dept` (`(hash, owner_department_id) WHERE deleted_at IS NULL`).

---

## 2. Các Lỗ hổng Thiết kế Nghiêm trọng & Rủi ro Vận hành (Architectural Flaws & Risks)

Qua đối chiếu với các cam kết vận hành bắt buộc (SLA) và yêu cầu thực tế, thiết kế hiện tại tồn tại 4 lỗi hệ thống nghiêm trọng cần phải sửa đổi trước khi tiến hành viết mã nguồn:

### 2.1. Lỗi Hiệu năng Hệ thống do Thiết kế Chỉ mục Thiếu sót (AD-007)
*   **Chi tiết lỗi**: 
    *   Quyết định **AD-007** đề xuất loại bỏ hoàn toàn chỉ mục `idx_documents_hash` để tiết kiệm dung lượng đĩa và giảm chi phí ghi.
    *   Tuy nhiên, quy tắc **BR-4** yêu cầu hệ thống phải kiểm tra xem file vật lý đã từng được tải lên bởi bất kỳ phòng ban nào khác trên toàn hệ thống chưa (kể cả các bản ghi đã bị xóa mềm) để thực hiện tối ưu hóa lưu trữ đơn bản (SIS).
    *   Chỉ mục UNIQUE duy nhất hiện tại là `uq_documents_hash_dept` được cấu hình là chỉ mục một phần (partial index) với điều kiện `WHERE deleted_at IS NULL`.
    *   Do chỉ mục này không chứa các bản ghi đã bị xóa mềm (`deleted_at IS NOT NULL`), khi hệ thống cần tìm đường dẫn file vật lý cũ nhất từ các tài liệu đã bị xóa mềm, PostgreSQL bắt buộc phải thực hiện **Quét toàn bảng (Sequential Scan)**. Khi số lượng bản ghi đạt hàng triệu dòng, thao tác Seq Scan trên đĩa cứng sẽ làm nghẽn I/O và sập hiệu năng toàn hệ thống.
*   **Giải pháp khắc phục**: Phải giữ lại một chỉ mục không duy nhất trên trường `hash` (hoặc `(hash, created_at)`) không chứa điều kiện lọc `deleted_at` để đảm bảo tìm kiếm file vật lý cũ nhất đạt độ phức tạp $O(\log N)$.

### 2.2. Va chạm Lock ID trong PostgreSQL Advisory Lock (AD-004)
*   **Chi tiết lỗi**:
    *   Sinh viên đề xuất tạo Lock ID bằng cách gọi hàm `hashtext(concat(owner_department_id, ':', hash))`.
    *   Hàm `hashtext` trả về kiểu dữ liệu số nguyên 32-bit (`integer`). Với không gian khóa 32-bit (khoảng 4 tỷ giá trị), tỷ lệ va chạm (collision) giữa các khóa là rất lớn khi số lượng file và phòng ban tăng lên.
    *   Khi xảy ra va chạm Lock ID, hai yêu cầu tải lên của hai phòng ban khác nhau cho hai file hoàn toàn khác nhau sẽ bị chặn chéo và phải xếp hàng chờ đợi nhau vô lý, gây tắc nghẽn luồng xử lý không đáng có.
*   **Giải pháp khắc phục**: PostgreSQL hỗ trợ cơ chế khóa cố vấn với 2 khóa 32-bit riêng biệt: `pg_advisory_xact_lock(key1 integer, key2 integer)`. Sinh viên phải chuyển sang sử dụng:
    `SELECT pg_advisory_xact_lock(hashtext(owner_department_id::text), hashtext(hash))` để loại bỏ rủi ro va chạm Lock ID.

### 2.3. Nghẽn Kết nối Cơ sở dữ liệu (Connection Pool Starvation)
*   **Chi tiết lỗi**:
    *   Thiết kế chặn các yêu cầu trùng lặp đồng thời bằng cách thực hiện `pg_advisory_xact_lock` **bên trong transaction**.
    *   Khi có 100 yêu cầu tải lên đồng thời cho cùng 1 file, 1 yêu cầu giữ khóa, 99 yêu cầu còn lại sẽ phải xếp hàng chờ đợi bên trong transaction. Do transaction đang mở, 99 luồng này sẽ chiếm giữ và khóa chặt 99 kết nối DB từ connection pool (HikariCP).
    *   Hiện tượng này gây cạn kiệt kết nối DB cục bộ (Connection Pool Starvation). Toàn bộ các API khác (đăng nhập, xem danh sách tài liệu phòng ban khác) sẽ bị treo hoàn toàn do không lấy được kết nối DB.
*   **Giải pháp khắc phục**: Thiết lập khóa ở tầng ứng dụng (như Redis/Redisson Lock hoặc JVM lock nếu chạy đơn node) trước khi mở giao dịch cơ sở dữ liệu để luồng chờ khóa block ở mức ứng dụng thay vì chiếm giữ kết nối DB; hoặc sử dụng hàm khóa không chặn `pg_try_advisory_xact_lock` kết hợp cơ chế sleep/retry ngoài giao dịch.

### 2.4. Mất tính Nguyên tử khi Rename tệp chéo Mount Point trên Môi trường Phân tán
*   **Chi tiết lỗi**:
    *   Quyết định **AD-006** yêu cầu `/tmp/eap-uploads` và `/eap-storage` nằm trên cùng một phân vùng đĩa để thao tác di chuyển tệp diễn ra tức thời (atomic rename ở mức OS).
    *   Trong triển khai thực tế đa node (distributed deployment), thư mục lưu trữ `/eap-storage` bắt buộc là một Network File System (như NFS, AWS EFS) dùng chung, còn `/tmp` là ổ đĩa cục bộ của node ứng dụng.
    *   Do khác biệt mount point vật lý, hệ điều hành sẽ phải chuyển thao tác đổi tên thành *copy rồi delete*, làm kéo dài thời gian giữ giao dịch và mất tính nguyên tử (atomic) nếu node bị crash khi đang copy.
*   **Giải pháp khắc phục**: Cấu hình thư mục chứa file tạm nằm cùng phân vùng mạng dùng chung (ví dụ: `/eap-storage/tmp`).

---

## 3. Câu hỏi Phản biện để Nghiệm thu Thiết kế (Architectural Defense)

Sinh viên bắt buộc phải chuẩn bị câu trả lời cho 3 câu hỏi kỹ thuật cốt lõi sau trước khi tiến hành code:

1.  **Cơ sở Toán học của Idempotency**:
    *   Hãy chứng minh tính chất idempotent ($f(f(x)) = f(x)$) của thiết kế khi nhận đồng thời 100 request trùng lặp. Giải thích cơ chế Check-and-Set và vẽ sơ đồ sequence chi tiết của các luồng xử lý chạy song song.
2.  **Xử lý Xung đột Ghi File Vật lý chéo Phòng ban**:
    *   Khi hai phòng ban độc lập cùng lúc tải lên một file hoàn toàn mới (chưa có trên hệ thống), luồng nào đổi tên file tạm thành công trước sẽ tạo file vật lý. Luồng còn lại sẽ bị ném ngoại lệ `FileAlreadyExistsException` ở tầng OS. Hệ thống sẽ xử lý ngoại lệ này như thế nào để đảm bảo không ghi đè đè nát dữ liệu và vẫn liên kết DB đến file đó thành công?
3.  **Xử lý Giao dịch thất bại (Orphaned Files)**:
    *   Nếu tệp tạm được đổi tên sang thư mục chính thức thành công, nhưng câu lệnh INSERT metadata vào database sau đó bị rollback (do lỗi DB hoặc mất kết nối). Tệp vật lý lúc này trở thành tệp mồ côi (Orphaned File). Trình bày phương án dọn dẹp tức thời ở luồng xử lý lỗi và phương án quét định kỳ (Daemon Cleanup).

---

## 4. Đánh giá Script Kiểm thử K6 (`stress_test_k6.js`)

*   **Ưu điểm**: Sử dụng đúng executor `per-vu-iterations` với 100 người dùng ảo (VUs) gửi chính xác 1 request đồng thời để kiểm tra SLA chống trùng lặp. Đầy đủ các assertions kiểm tra mã HTTP, cờ `duplicated` và cấu trúc dữ liệu phản hồi.
*   **Thiếu sót**: Token xác thực `Authorization` bị gán cứng (`Bearer JWT_TOKEN_CUA_NHAN_VIEN_PHONG_BAN_A`), khiến script không thể chạy tự động trong các quy trình kiểm thử liên tục (CI/CD). Sinh viên cần viết thêm bước đăng nhập lấy token động trong hàm `setup()` của k6.

---

## 5. Kết luận & Điểm đánh giá Đề xuất

*   **Trạng thái Đánh giá**: **ĐẠT YÊU CẦU (Yêu cầu sửa đổi thiết kế trước khi code)**
*   **Điểm thiết kế đề xuất**: **7.0 / 10**  
    *(Lý do: Thiết kế tổng thể rõ ràng nhưng có 2 lỗi nghiêm trọng ảnh hưởng trực tiếp đến hiệu năng và độ ổn định của hệ thống dưới tải cao là Seq Scan trên các tài liệu đã xóa và Connection Pool Starvation).*
