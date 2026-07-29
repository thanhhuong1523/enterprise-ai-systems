# Tài liệu Yêu cầu Sản phẩm (PRD) - Tuần 2
## Phân hệ Tải lên Tài liệu: Chống trùng lặp & Xử lý Tải đồng thời

---

## 1. Bối cảnh Nghiệp vụ (Business Goal & Problem)

### 1.1. Vấn đề Nghiệp vụ (Business Problem)
Khi tốc độ kết nối mạng không ổn định, người dùng có xu hướng nhấn nút gửi yêu cầu tải lên nhiều lần liên tiếp. Nếu không có cơ chế kiểm soát, hành vi này sẽ dẫn đến các hệ quả:
* **Lãng phí tài nguyên**: Hệ thống phải lưu trữ nhiều bản sao giống hệt nhau về nội dung cho cùng một phòng ban, làm gia tăng chi phí vận hành và sao lưu dữ liệu.
* **Không nhất quán dữ liệu**: Ghi nhận thừa nhiều tài liệu trùng lặp trong hệ thống cho cùng một hành động tải lên của người dùng.

### 1.2. Mục tiêu Nghiệp vụ (Business Goal)
* **Tối ưu hóa dung lượng lưu trữ**: Đảm bảo không lưu trữ trùng lặp các tệp tin có cùng nội dung trong phạm vi của một phòng ban.
* **Đảm bảo tính nhất quán dưới tải cao (SLA)**: Khi có nhiều yêu cầu tải lên đồng thời (lên đến 100 yêu cầu) cho cùng một nội dung tệp tin, hệ thống chỉ chấp nhận và tạo duy nhất 1 tài liệu và 1 bản sao lưu trữ thực tế. Các yêu cầu còn lại phải bị từ chối và phản hồi thông báo lỗi trùng lặp rõ ràng.

---

## 2. Phạm vi Sản phẩm (Scope)

### Nằm trong phạm vi (In Scope)
* **Định danh nội dung tệp**: Nhận diện và định danh duy nhất tệp tin dựa trên nội dung thực tế của tệp, không phụ thuộc vào tên tệp do người dùng đặt.
* **Chống trùng lặp cấp Phòng ban**: Ngăn chặn việc tạo tài liệu có cùng nội dung tệp tin trong cùng một phòng ban (chỉ áp dụng đối với các tài liệu đang ở trạng thái hoạt động/chưa bị xóa).
* **Tối ưu hóa lưu trữ đơn bản (Single Instance Storage)**: Khi nhiều phòng ban khác nhau tải lên các tệp tin có cùng nội dung, hệ thống chỉ lưu trữ duy nhất một bản sao lưu trữ thực tế của tệp tin đó, nhưng vẫn đảm bảo hiển thị và quản lý thông tin tài liệu độc lập cho từng phòng ban.
* **Kiểm soát xử lý đồng thời**: Điều phối việc xử lý khi nhận nhiều yêu cầu tải lên cùng một nội dung tại cùng một thời điểm nhằm tránh xung đột dữ liệu và ngăn chặn việc tạo tài liệu trùng lặp.
* **Phản hồi nghiệp vụ rõ ràng**:
  * Khi tải lên và tạo tài liệu thành công: Phản hồi thông tin tài liệu vừa tạo.
  * Khi phát hiện tài liệu đã tồn tại trong phòng ban: Phản hồi thông báo lỗi tài liệu trùng lặp.
  * Khi hệ thống bận do xử lý đồng thời vượt ngưỡng: Phản hồi thông báo yêu cầu thử lại sau.
  * Khi định dạng hoặc tệp tải lên không hợp lệ: Phản hồi thông báo lỗi định dạng hoặc nội dung tệp không hợp lệ.

### Nằm ngoài phạm vi (Out of Scope - TUYỆT ĐỐI KHÔNG TRIỂN KHAI)
* Không tích hợp các giải pháp trung gian hoặc kiến trúc phân tán bên ngoài (ví dụ: các hệ thống xếp hàng tin nhắn độc lập, bộ nhớ đệm phân tán ngoài hoặc kiến trúc vi dịch vụ).
* Không áp dụng cơ chế kiểm soát trùng lặp do phía người dùng tự tạo và gửi lên.
* Không thiết lập cơ chế tự động gửi lại yêu cầu trên giao diện người dùng.
* Không chấp nhận hoặc xác nhận thành công đối với các yêu cầu tải lên có nội dung trùng lặp.
* Không triển khai các tính năng mở rộng hoặc lộ trình phát triển ngoài các yêu cầu của tuần này.

---

## 3. Vai trò Người dùng (User Roles)
* **Nhân viên & Quản lý phòng ban**: Thực hiện tải lên và xem các tài liệu thuộc sở hữu của phòng ban mình.
* **Ban Giám Đốc (BOARD)**: Thực hiện tải lên, xem và quản lý tài liệu thuộc sở hữu của phòng BOARD. Tài liệu của phòng BOARD là tuyệt mật, được bảo vệ nghiêm ngặt bằng quy tắc phân quyền truy cập và tuyệt đối không chia sẻ cho các phòng ban khác.

---

## 4. Luồng xử lý Nghiệp vụ chính (User Flow)

Hệ thống tiếp nhận và xử lý yêu cầu tải lên theo các bước nghiệp vụ sau:

```text
[Người dùng gửi yêu cầu tải lên]
             │
             ▼
[Bước 1: Tiếp nhận & Kiểm tra tính hợp lệ của tệp]
             │
             ├─(Tệp không hợp lệ) ──► [Thông báo lỗi tệp/định dạng không hợp lệ]
             │
             ▼
[Bước 2: Kiểm tra trùng lặp nội dung trong phòng ban]
             │
             ├─(Tài liệu hoạt động đã tồn tại trong phòng ban) ──► [Thông báo tài liệu trùng lặp]
             │
             ▼
[Bước 3: Kiểm soát đồng thời & Ghi nhận tài liệu]
             │
             ├─(Hệ thống bận do xử lý đồng thời) ──► [Thông báo hệ thống bận / thử lại sau]
             ├─(Phát hiện trùng lặp khi xử lý đồng thời) ──► [Thông báo tài liệu trùng lặp]
             │
             ▼
[Bước 4: Hoàn tất lưu trữ & Trả kết quả] ──► [Phản hồi tạo tài liệu thành công]
```

---

## 5. Yêu cầu Chức năng (Functional Requirements)

### FR-001: Tải lên tài liệu & Chống trùng lặp nội dung
* **Mô tả**: Khi người dùng tải lên một tệp tin, hệ thống phải xác định nội dung tệp tin đó để kiểm tra sự tồn tại của tài liệu có cùng nội dung đang hoạt động (chưa bị xóa) thuộc phòng ban đó. Nếu phát hiện đã tồn tại tài liệu hoạt động trùng lặp nội dung, hệ thống phải từ chối yêu cầu và phản hồi thông báo tài liệu trùng lặp.
* **Nguyên tắc**: Việc xác định trùng lặp phải dựa trên nội dung thực tế của tệp tin, hoàn toàn không phụ thuộc vào tên tệp do người dùng đặt.

### FR-002: Kiểm soát xử lý đồng thời
* **Mô tả**: Khi có nhiều yêu cầu tải lên đồng thời cho cùng một nội dung tệp tin trong cùng một phòng ban, hệ thống phải kiểm soát và xử lý tuần tự để đảm bảo tính nhất quán dữ liệu.
* **Nguyên tắc**:
  1. Chỉ có yêu cầu đầu tiên được xử lý thành công sẽ ghi nhận tài liệu mới và phản hồi kết quả thành công.
  2. Các yêu cầu tải lên đồng thời tiếp theo sau đó phải được xác định là trùng lặp và phản hồi thông báo tài liệu trùng lặp.
  3. Nếu yêu cầu đầu tiên gặp lỗi và không hoàn tất việc ghi nhận tài liệu, các yêu cầu đồng thời tiếp theo phải được tiếp tục xử lý và có cơ hội thành công.

### FR-003: Tối ưu hóa lưu trữ đơn bản (Single Instance Storage)
* **Mô tả**: Khi nhiều phòng ban khác nhau tải lên các tệp tin có cùng nội dung, hệ thống phải tối ưu hóa dung lượng lưu trữ bằng cách chia sẻ tài nguyên lưu trữ thực tế của tệp tin đó để tiết kiệm tài nguyên, nhưng vẫn phải đảm bảo hiển thị thông tin tài liệu độc lập và cô lập dữ liệu giữa các phòng ban.
* **Nguyên tắc**: Việc xóa tài liệu ở phòng ban này không được làm ảnh hưởng đến khả năng truy cập và sử dụng tài liệu của phòng ban khác có cùng nội dung tệp tin.

---

## 6. Quy tắc Nghiệp vụ (Business Rules)

* **BR-1: Tiêu chí xác định duy nhất**: Tệp tin được định danh duy nhất dựa trên nội dung thực tế của nó, hoàn toàn độc lập với tên tệp do người dùng đặt.
* **BR-2: Phạm vi kiểm tra trùng lặp**: Việc chống trùng lặp tài liệu chỉ áp dụng riêng biệt trong phạm vi từng **Phòng ban**. Hai phòng ban khác nhau được phép sở hữu hai tài liệu độc lập có cùng nội dung.
* **BR-3: Ràng buộc trạng thái tài liệu**: Chỉ kiểm tra trùng lặp đối với các tài liệu đang ở trạng thái hoạt động (chưa bị xóa). Nếu tài liệu trước đó đã bị xóa, hệ thống cho phép ghi nhận tài liệu mới có cùng nội dung và thực hiện tối ưu hóa dung lượng bằng cách tái sử dụng dữ liệu lưu trữ đã tồn tại. Tài liệu đã bị xóa không thể khôi phục lại trạng thái cũ.
* **BR-4: Bảo toàn dữ liệu lưu trữ**: Nội dung tệp tin lưu trữ thực tế chỉ bị xóa hoàn toàn khỏi hệ thống khi không còn bất kỳ tài liệu nào (bao gồm cả tài liệu đang hoạt động hoặc tài liệu đã bị xóa ở dạng lưu trữ lịch sử) sử dụng đến nội dung đó.
* **BR-5: Xử lý đụng độ đồng thời liên phòng ban**: Khi hai phòng ban khác nhau tải lên cùng một nội dung tệp tin tại cùng một thời điểm, cả hai yêu cầu đều được chấp nhận thành công độc lập cho từng phòng ban, đồng thời hệ thống vẫn tối ưu hóa dung lượng lưu trữ chung.

---

## 7. Yêu cầu Phi chức năng (Non-functional Requirements)

### 7.1. Bảo mật & Cô lập dữ liệu
* **Cô lập thông tin trùng lặp**: Hệ thống chỉ báo lỗi trùng lặp nếu tệp đó đã tồn tại trong cùng phòng ban của người dùng hiện tại. Tuyệt đối không tiết lộ sự tồn tại của tệp ở phòng ban khác dưới mọi hình thức.
* **Bảo vệ tài liệu phòng BOARD**: Tài liệu của phòng BOARD phải được đảm bảo cô lập hoàn toàn, không thể bị truy cập bởi bất kỳ người dùng nào thuộc phòng ban khác.

### 7.2. Độ tin cậy & Tính nhất quán
* **Tính trọn vẹn của dữ liệu**: Quá trình ghi nhận thông tin tài liệu và lưu trữ dữ liệu phải được thực hiện trọn vẹn. Nếu xảy ra lỗi giữa chừng, hệ thống phải tự động hoàn tác hoặc dọn dẹp để đảm bảo không tồn tại dữ liệu thừa hoặc trạng thái không nhất quán.

### 7.3. Hiệu năng & Vận hành
* **Khả năng đáp ứng đồng thời**: Hệ thống phải hoạt động ổn định dưới tải cao, có khả năng xử lý mượt mà các yêu cầu tải lên đồng thời mà không làm gián đoạn dịch vụ.

---

## 8. Tiêu chí Nghiệm thu & SLA (Acceptance Criteria)

* **AC-1**: Khi gửi 100 yêu cầu tải lên đồng thời cho cùng một nội dung tệp tin trong cùng một phòng ban:
  * Có đúng 1 yêu cầu tải lên thành công và trả về thông tin tài liệu.
  * 99 yêu cầu còn lại bị từ chối và nhận thông báo lỗi tài liệu trùng lặp.
  * Hệ thống chỉ ghi nhận đúng 1 tài liệu hoạt động.
* **AC-2**: Không xảy ra hiện tượng mất mát dữ liệu, ghi đè thông tin sai lệch hoặc lỗi cấu trúc thông tin tài liệu khi xử lý đồng thời.
* **AC-3**: Hệ thống chỉ lưu trữ duy nhất một bản sao của nội dung tệp tin đó trên hệ thống lưu trữ dùng chung.
* **AC-4**: Khi tài liệu ở phòng ban A bị xóa, phòng ban B (đang sử dụng chung nội dung tệp tin) vẫn xem và tải xuống tài liệu bình thường mà không gặp bất kỳ lỗi nào.

---

## 9. Thông tin Nghiệp vụ bổ sung

### 9.1. Kịch bản Người dùng (User Stories)
* **Kịch bản 1 (Nhân viên tải tài liệu mới)**:
  * *Là* một nhân viên phòng ban, *tôi muốn* tải lên tài liệu mới của mình lên hệ thống, *để* tôi và các đồng nghiệp trong phòng ban có thể truy cập và sử dụng.
* **Kịch bản 2 (Người dùng thao tác nhiều lần)**:
  * *Là* một người dùng đang tải tài liệu dưới mạng chậm, *tôi muốn* hệ thống tự động ngăn chặn các yêu cầu trùng lặp khi tôi lỡ nhấn nút gửi nhiều lần, *để* danh sách tài liệu phòng ban không bị trùng lặp thông tin.
* **Kịch bản 3 (Bảo mật thông tin phòng BOARD)**:
  * *Là* thành viên Ban Giám đốc, *tôi muốn* các tài liệu tuyệt mật của phòng BOARD khi tải lên được bảo mật tuyệt đối, *để* không một phòng ban nào khác có thể nhìn thấy hoặc suy đoán được sự tồn tại của chúng.

### 9.2. Ràng buộc Nghiệp vụ (Business Constraints)
* **Bảo mật chéo**: Không một phòng ban nào được biết hoặc suy đoán được phòng ban khác đã tải lên tệp tin gì thông qua tính năng chống trùng lặp. Việc kiểm tra trùng lặp chỉ có ý nghĩa cục bộ trong nội bộ mỗi phòng ban.
* **Giới hạn số lượng**: Mỗi phòng ban chỉ được phép duy trì tối đa một tài liệu hoạt động cho cùng một nội dung tệp tin tại một thời điểm.

### 9.3. Giả định & Sự phụ thuộc (Assumptions & Dependencies)
* **Định danh người dùng**: Giả định rằng hệ thống đã hoàn tất việc xác thực và phân quyền người dùng vào một phòng ban cụ thể trước khi thực hiện hành động tải lên.
* **Hạ tầng lưu trữ**: Hệ thống phụ thuộc vào một phân hệ quản lý tập tin có khả năng kiểm tra sự tồn tại và đọc/ghi tệp tin ổn định.

### 9.4. Chỉ số Đo lường Thành công (Success Metrics)
* **Tỷ lệ tối ưu lưu trữ**: Đạt 100% việc dùng chung dung lượng lưu trữ đối với các tài liệu có cùng nội dung giữa các phòng ban khác nhau.
* **Độ chính xác xử lý đồng thời**: 100% các tình huống tải lên đồng thời từ cùng một phòng ban đối với cùng một nội dung được xử lý đúng quy tắc (chỉ tạo 1 tài liệu hoạt động, các yêu cầu còn lại bị báo lỗi trùng lặp).