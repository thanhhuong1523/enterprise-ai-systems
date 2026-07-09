# Tài liệu Yêu cầu Sản phẩm (PRD) - Tuần 2
## Phân hệ Tải lên Tài liệu: Chống trùng lặp & Xử lý Tải đồng thời

---

## 1. Bối cảnh Nghiệp vụ (Business Goal & Problem)

### 1.1. Vấn đề Nghiệp vụ (Business Problem)
Trong thực tế, khi mạng của văn phòng bị chậm, nhân viên và ban lãnh đạo thường có thói quen bấm nút "Tải lên" liên tục 4-5 lần vì nghĩ hệ thống bị treo. Nếu không được kiểm soát, hành vi này dẫn đến:
*   Hệ thống lưu trữ trùng lặp nhiều file giống hệt nhau vào cùng một tài khoản phòng ban, gây lãng phí dung lượng đĩa cứng và tốn chi phí sao lưu, vận hành.
*   Dữ liệu bị không nhất quán hoặc xảy ra lỗi ghi đè thông tin tài liệu.

### 1.2. Mục tiêu Nghiệp vụ (Business Goal)
*   **Tối ưu hóa tài nguyên lưu trữ**: Không lưu trùng lặp các tệp tin giống hệt nhau trong cùng một tài khoản phòng ban.
*   **Trải nghiệm người dùng mượt mà**: Xử lý các yêu cầu tải lên trùng lặp đồng thời và trả về thông tin tài liệu đã tải thành công trước đó để người dùng không cảm thấy hệ thống bị lỗi hoặc treo.
*   **Đảm bảo hiệu năng tải đồng thời**: Giảm thiểu việc xếp hàng chờ đợi đối với các yêu cầu đã được xác định là trùng lặp từ trước, đảm bảo phản hồi nhanh chóng dưới tải cao.

---

## 2. Phạm vi Sản phẩm (Scope)

### Nằm trong phạm vi (In Scope)
*   **Định danh nội dung tệp**: Sinh mã định danh duy nhất cho nội dung tệp tin ngay khi nhận được yêu cầu.
*   **Chống trùng lặp cấp Phòng ban**: Ngăn chặn một phòng ban lưu trùng lặp tệp tin có cùng nội dung trong trạng thái tài liệu chưa bị xóa.
*   **Tối ưu hóa dung lượng lưu trữ trên toàn hệ thống (Đơn bản)**: Nếu hai phòng ban khác nhau tải lên cùng một nội dung tệp tin, hệ thống chỉ lưu trữ duy nhất một bản sao vật lý của tệp tin đó trên ổ đĩa, nhưng vẫn đảm bảo hiển thị độc lập trong danh sách tài liệu của từng phòng ban.
*   **Xử lý đồng thời (Idempotent Concurrency)**: Xử lý an toàn khi nhiều yêu cầu tải lên cùng một tệp tin được gửi tới hệ thống tại cùng một thời điểm.
*   **Thông báo trùng lặp**: Phản hồi thông tin tài liệu hiện tại cùng chỉ thị tệp tin trùng lặp để giao diện hiển thị thông báo phù hợp cho người dùng.

### Nằm ngoài phạm vi (Out of Scope)
*   Chống trùng lặp đối với các tài liệu đã được tải lên trước phiên bản này.
*   Kiểm tra mã độc hoặc virus trong tệp tin (sẽ được xử lý bởi phân hệ bảo mật độc lập).
*   Tính năng tải lên tệp tin theo từng phân đoạn (Chunked Upload).

---

## 3. Các bên liên quan & Vai trò Người dùng (Stakeholders & User Roles)

### 3.1. Các bên liên quan (Stakeholders)
*   **Lãnh đạo Doanh nghiệp**: Mong muốn tối ưu chi phí vận hành phần cứng và đĩa lưu trữ.
*   **Đội ngũ Vận hành (Operations/DevOps)**: Mong muốn hệ thống chạy ổn định dưới tải cao, không bị nghẽn tài nguyên.
*   **Đội ngũ Phát triển**: Cần tài liệu đặc tả nghiệp vụ rõ ràng để hiện thực hóa giải pháp.

### 3.2. Vai trò Người dùng (User Roles)
*   **Nhân viên**: Tải lên và xem tài liệu thuộc phòng ban của mình.
*   **Quản lý phòng ban**: Tải lên, xem và xóa tài liệu thuộc phòng ban của mình.
*   **Ban Giám Đốc (Board)**:
    *   Có quyền xem tài liệu của tất cả các phòng ban trong hệ thống (chỉ đọc).
    *   Có quyền tải lên tài liệu thuộc sở hữu của văn phòng Ban Giám Đốc.
    *   **Quy tắc đặc biệt**: Ban Giám Đốc không được phép chia sẻ tài liệu của mình ra bên ngoài phòng ban và các phòng ban khác không được phép chia sẻ tài liệu cho Ban Giám Đốc.

---

## 4. Luồng xử lý Nghiệp vụ chính (User Flow)

```text
[Người dùng nhấn nút Tải lên]
           │
           ▼
[Hệ thống kiểm tra định dạng & dung lượng tệp] ──(Không hợp lệ)──► [Báo lỗi đầu vào]
           │
           ▼
[Hệ thống tính mã định danh nội dung tệp]
           │
           ▼
[Hệ thống kiểm tra nhanh sự tồn tại của tệp]
           │
           ├─(Đã có trong phòng ban & hoạt động) ─────────────────────────► [Trả về Metadata cũ + Cờ trùng lặp]
           │
           └─(Chưa có trong phòng ban)
                      │
                      ▼
           [Đồng bộ luồng xử lý đồng thời]
                      │
                      ▼
           [Kiểm tra lại lần cuối trong phòng ban]
                      │
                      ├─(Phát hiện trùng do luồng khác vừa ghi) ─────────► [Trả về Metadata cũ + Cờ trùng lặp]
                      │
                      └─(Vẫn chưa tồn tại trong phòng ban)
                                 │
                                 ▼
                     [Kiểm tra trên toàn hệ thống]
                                 │
                                 ├─(Nội dung tệp đã tồn tại ở phòng ban khác)
                                 │          │
                                 │          ▼
                                 │  [Liên kết tới tệp vật lý có sẵn]
                                 │  [Tạo bản ghi siêu dữ liệu mới]
                                 │          │
                                 │          ▼
                                 │  [Trả về Kết quả thành công]
                                 │
                                 └─(Tệp hoàn toàn mới)
                                            │
                                            ▼
                                    [Ghi tệp vật lý mới lên đĩa]
                                    [Tạo bản ghi siêu dữ liệu mới]
                                            │
                                            ▼
                                    [Trả về Kết quả thành công]
```

---

## 5. Yêu cầu Chức năng (Functional Requirements)

### FR-001: Tải lên tài liệu chống trùng lặp
*   **Mô tả**: Khi người dùng tải lên một tệp tin, hệ thống tự động kiểm tra xem tệp tin đó đã tồn tại trong phòng ban chưa để đưa ra quyết định xử lý phù hợp.
*   **Luồng xử lý nghiệp vụ**:
    1.  Hệ thống tính mã định danh duy nhất cho nội dung tệp tin tải lên.
    2.  Hệ thống kiểm tra sự tồn tại của mã định danh này trong phạm vi phòng ban hiện tại của người dùng đối với các tài liệu chưa bị xóa.
    3.  Nếu tài liệu đã tồn tại, hệ thống không tạo mới bản ghi và không ghi đè dữ liệu. Thay vào đó, hệ thống trả về phản hồi thành công kèm theo đầy đủ siêu dữ liệu của tài liệu cũ cùng cờ thông báo trùng lặp.
    4.  Nếu tài liệu chưa tồn tại trong phòng ban, hệ thống tiến hành kiểm tra trên toàn hệ thống để thực hiện cơ chế lưu trữ tối ưu (SIS).

### FR-002: Kiểm soát tải đồng thời và Nhất quán dữ liệu
*   **Mô tả**: Khi có nhiều yêu cầu tải lên cùng một tệp tin được gửi lên đồng thời từ một phòng ban, hệ thống phải đảm bảo tính nhất quán và xử lý tuần tự an toàn.
*   **Luồng xử lý nghiệp vụ**:
    1.  Đối với các yêu cầu tải lên đồng thời, hệ thống thực hiện kiểm tra nhanh. Nếu yêu cầu nào đến muộn khi tệp đã được tạo thành công, hệ thống trả về ngay kết quả trùng lặp mà không cần đi vào hàng đợi đồng bộ.
    2.  Các yêu cầu chưa xác định được kết quả sẽ đi vào hàng đợi đồng bộ hóa để xử lý tuần tự.
    3.  Yêu cầu đầu tiên đi ra khỏi hàng đợi sẽ thực hiện ghi nhận dữ liệu chính thức.
    4.  Các yêu cầu tiếp theo sau khi ra khỏi hàng đợi sẽ kiểm tra lại trạng thái. Nếu phát hiện tệp đã được ghi nhận bởi yêu cầu trước đó, hệ thống tự động hủy thao tác ghi và trả về thông tin tài liệu đã tồn tại.

### FR-003: Cơ chế tối ưu lưu trữ (Đơn bản)
*   **Mô tả**: Khi hai phòng ban độc lập tải lên cùng một nội dung tệp tin, hệ thống phải tránh việc nhân bản tệp vật lý trên ổ đĩa.
*   **Luồng xử lý nghiệp vụ**:
    1.  Hệ thống kiểm tra xem mã định danh của tệp tin đã từng tồn tại ở bất kỳ phòng ban nào khác trên toàn hệ thống chưa.
    2.  Nếu tệp vật lý đã tồn tại, hệ thống tạo bản ghi siêu dữ liệu mới cho phòng ban hiện tại và liên kết tới tệp vật lý có sẵn, bỏ qua bước ghi tệp mới lên đĩa.
    3.  Nếu tệp vật lý chưa tồn tại, hệ thống tiến hành lưu tệp mới lên đĩa dưới tên file được chuẩn hóa theo mã định danh nội dung tệp.

---

## 6. Quy tắc Nghiệp vụ (Business Rules)
*   **BR-1: Tiêu chí xác định duy nhất**: Nội dung tệp tin được định danh duy nhất bằng mã định danh nội dung tệp sinh ra từ cấu trúc nhị phân của tệp, không phụ thuộc vào tên tệp do người dùng đặt.
*   **BR-2: Phạm vi kiểm tra trùng lặp**: Việc chống trùng lặp siêu dữ liệu được áp dụng chặt chẽ theo từng **Phòng ban**. Hai phòng ban khác nhau được quyền sở hữu hai tài liệu có cùng nội dung một cách độc lập.
*   **BR-3: Ràng buộc trạng thái tài liệu**: Chỉ kiểm tra trùng lặp với các tài liệu đang ở trạng thái hoạt động (chưa bị xóa mềm). Nếu tài liệu đã bị xóa trước đó, người dùng được quyền tải lên lại tệp tin đó như một tài liệu mới.
*   **BR-4: Bảo toàn tệp vật lý trên đĩa**: File vật lý được xem là còn tồn tại trên đĩa nếu có ít nhất một bản ghi siêu dữ liệu (của bất kỳ phòng ban nào) từng liên kết tới nó, không phân biệt trạng thái xóa của bản ghi đó.
*   **BR-5: Nhất quán liên kết vật lý**: Mọi tài liệu có cùng mã định danh nội dung tệp bắt buộc phải trỏ tới cùng một tệp vật lý duy nhất trên đĩa. Khi hệ thống tìm thấy nhiều liên kết cũ, hệ thống luôn chọn liên kết của bản ghi được tạo sớm nhất để đảm bảo tính nhất quán.

---

## 7. Yêu cầu Phi chức năng (Non-functional Requirements)

### 7.1. Bảo mật
*   **Cô lập dữ liệu**: Thông tin trùng lặp chỉ được thông báo nếu tệp tin đó đã tồn tại trong cùng phòng ban của người dùng đang đăng nhập. Hệ thống tuyệt đối không tiết lộ thông tin tệp tin đã tồn tại ở phòng ban khác để tránh rò rỉ dữ liệu (Side-channel leak).
*   **Xác thực định dạng**: Định dạng tệp tin phải được kiểm chứng qua cấu trúc nhị phân thực tế của tệp, không dựa vào phần mở rộng của tên tệp.

### 7.2. Hiệu năng & Vận hành
*   **Tính sẵn sàng của hạ tầng kết nối**: Hạ tầng kết nối mạng và kết nối cơ sở dữ liệu phải được thiết lập đủ lớn để đáp ứng các kịch bản tải đồng thời theo cam kết SLA mà không gây treo hệ thống.
*   **An toàn lưu trữ**: Việc lưu trữ và đổi tên tệp tin phải diễn ra trên cùng một ổ đĩa vật lý để đảm bảo tốc độ và tính toàn vẹn của tệp tin.

---

## 8. Tiêu chí Nghiệm thu (Acceptance Criteria & SLA)
*   **AC-1 (Đảm bảo SLA)**: Khi bắn 100 yêu cầu tải lên đồng thời cho cùng một tệp tin trong cùng một phòng ban, hệ thống chỉ được tạo ra đúng 1 bản ghi tài liệu duy nhất trong cơ sở dữ liệu.
*   **AC-2 (Thời gian phản hồi SLA)**: Toàn bộ 100 yêu cầu tải lên đồng thời phải được xử lý thành công trong vòng tối đa 15 giây. Không có yêu cầu nào bị trả về lỗi hệ thống (HTTP 500).
*   **AC-3 (Zero Storage Waste)**: Thư mục lưu trữ vật lý trên đĩa chỉ được phép sinh ra thêm đúng 1 tệp vật lý duy nhất cho tệp tin mới đó.
*   **AC-4 (SIS Performance)**: Khi Phòng ban B tải lên tệp tin trùng nội dung với tệp đã có của Phòng ban A, hệ thống phải phản hồi thành công và tái sử dụng liên kết cũ với tốc độ phản hồi nhanh hơn hoặc tương đương luồng ghi tệp mới.
*   **AC-5 (Trải nghiệm Idempotent)**: 1 yêu cầu thắng cuộc nhận phản hồi tạo mới thành công, 99 yêu cầu trùng lặp còn lại nhận phản hồi thành công kèm siêu dữ liệu đầy đủ của tài liệu cũ và chỉ thị trùng lặp.
*   **AC-6 (Xóa mềm độc lập)**: Việc xóa một bản ghi tài liệu ở Phòng ban A không được làm ảnh hưởng đến khả năng truy cập tệp vật lý của Phòng ban B nếu cả hai đang dùng chung một tệp vật lý.
