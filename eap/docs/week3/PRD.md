# TÀI LIỆU YÊU CẦU SẢN PHẨM (PRD)

**Hệ Thống Xử Lý Tài Liệu Bất Đồng Bộ và Tự Phục Hồi Độ Tin Cậy Cao (Resilient & Asynchronous Document Processing System)**

---

# 1. Quản lý Tài liệu

## 1.1 Thông tin Tài liệu
| Trường | Giá trị |
| :--- | :--- |
| Tiêu đề Tài liệu | Tài liệu Yêu cầu Sản phẩm |
| Mã Tài liệu | PRD-001 |
| Phiên bản | 2.0 |
| Trạng thái | Bản thảo |
| Tác giả | [Đội ngũ Dự án] |
| Người Kiểm duyệt | [Người Kiểm duyệt] |
| Người Phê duyệt | [Người Phê duyệt] |
| Ngày phát hành | 2026-07-30 |

## 1.2 Lịch sử Thay đổi
| Phiên bản | Ngày | Tác giả | Mô tả Thay đổi | Lý do |
| :--- | :--- | :--- | :--- | :--- |
| 1.0 | 2026-07-29 | [Đội ngũ Dự án] | Phiên bản đầu tiên | Yêu cầu ban đầu. |
| 2.0 | 2026-07-30 | [Đội ngũ Dự án] | Đơn giản hóa cơ chế khôi phục lỗi và đưa vào vị trí xử lý bền vững | Loại bỏ yêu cầu watchdog/heartbeat và chuyển sang mô hình khôi phục (recovery) dựa trên trạng thái bền vững và vị trí xử lý (Processing Position) sau khi máy chủ khởi động lại. Yêu cầu sản phẩm được đơn giản hóa. Hệ thống không còn yêu cầu watchdog/heartbeat hoặc cơ chế phát hiện worker treo. Khả năng phục hồi được thực hiện bằng trạng thái tài liệu bền vững và vị trí xử lý. Khi máy chủ khởi động lại, tài liệu đang ở trạng thái PROCESSING được đưa về READY và worker tiếp tục xử lý từ vị trí thành công gần nhất đã lưu. |

## 1.3 Tài liệu Tham khảo
*   **REF-001**: Đề bài Thực tập Intern VCCorp, Phiên bản 1.0, 2026, Kho lưu trữ tài liệu dự án.

---

# 2. Giới thiệu

## 2.1 Mục đích
Tài liệu này định nghĩa các yêu cầu nghiệp vụ, yêu cầu tính năng, yêu cầu phi tính năng và ràng buộc hệ thống cho "Hệ Thống Xử Lý Tài Liệu Bất Đồng Bộ và Tự Phục Hồi Độ Tin Cậy Cao". Tài liệu này đóng vai trò là đặc tả yêu cầu sản phẩm (PRD) chính thức, cung cấp đầu vào trực tiếp cho việc thiết kế kiến trúc hệ thống (Architecture Design Document - ADD).

## 2.2 Phạm vi
Hệ thống tập trung vào việc tiếp nhận tài liệu từ người dùng một cách nhanh chóng, thực hiện phân tích và xử lý tài liệu bất đồng bộ dưới nền, đồng thời đảm bảo khả năng phục hồi tự động và tiếp tục xử lý từ vị trí thành công gần nhất sau khi xảy ra sự cố sập máy chủ (server crash) mà không cần can thiệp thủ công từ người dùng hoặc quản trị viên.

## 2.3 Các bên Liên quan (Stakeholders)
| Bên liên quan | Nhu cầu / Mối quan tâm |
| :--- | :--- |
| Người dùng cuối (End User) | Tiếp nhận tài liệu nhanh chóng, không phải tải lên lại khi hệ thống gặp sự cố. |
| Chủ sở hữu nghiệp vụ (Business Owner) | Bảo toàn tài liệu gốc, đảm bảo tính nhất quán dữ liệu, tối ưu chi phí hạ tầng. |
| Đội ngũ Vận hành (Operations) | Hệ thống tự phục hồi tự động, giảm can thiệp thủ công, có log vết đầy đủ. |
| Đội ngũ Phát triển (Development Team) | Yêu cầu nghiệp vụ rõ ràng, không mập mờ, ranh giới thiết kế rõ ràng. |
| Đội ngũ Kiểm thử (QA/Test Team) | Các yêu cầu có thể kiểm chứng, đo lường được dưới dạng kịch bản nghiệm thu cụ thể. |
| Kiến trúc sư giải pháp (Solution Architect) | Đầu vào đầy đủ về mặt chức năng và phi tính năng để thiết kế giải pháp kỹ thuật. |

## 2.4 Đối tượng Độc giả
Tài liệu này được dành cho:
*   Kiến trúc sư phần mềm / Nhà phân tích hệ thống để thiết kế kiến trúc hệ thống và chi tiết hóa thiết kế kỹ thuật.
*   Đội ngũ phát triển phần mềm để triển khai mã nguồn.
*   Đội ngũ kiểm thử chất lượng để xây dựng kịch bản kiểm thử.
*   Người quản lý dự án để theo dõi tiến độ và nghiệm thu sản phẩm.

## 2.5 Thuật ngữ và Định nghĩa
*   **Tài liệu (Document)**: Tệp tin tài liệu do người dùng tải lên hệ thống để thực hiện phân tích, trích xuất.
*   **Đơn vị xử lý (Processing Unit)**: Đơn vị nhỏ nhất có thể xử lý và ghi nhận trạng thái độc lập của một tài liệu (ví dụ: từng trang hoặc các khối phân đoạn dữ liệu độc lập).
*   **Vị trí xử lý (Processing Position)**: Vị trí xử lý thành công gần nhất đã được hệ thống xác nhận và lưu trữ bền vững, được sử dụng làm điểm tiếp tục xử lý sau khi có sự cố.
*   **READY (Sẵn sàng)**: Trạng thái tài liệu đã được hệ thống tiếp nhận thành công và sẵn sàng để bắt đầu xử lý.
*   **PROCESSING (Đang xử lý)**: Trạng thái tài liệu đang được tiến trình xử lý ngầm thực hiện phân tích dưới nền.
*   **COMPLETED (Đã hoàn thành)**: Trạng thái tài liệu đã được xử lý hoàn tất thành công và kết quả được lưu trữ bền vững.
*   **FAILED (Thất bại)**: Trạng thái tài liệu gặp lỗi vĩnh viễn không thể khắc phục hoặc vượt quá giới hạn thử lại cho phép đối với lỗi tạm thời.
*   **Khôi phục (Recovery)**: Quá trình hệ thống tự động quét và đưa các tài liệu dở dang (đang ở trạng thái `PROCESSING` tại thời điểm sập nguồn) trở lại trạng thái `READY` sau khi máy chủ khởi động lại.
*   **Tiếp tục xử lý (Resume)**: Khả năng hệ thống tiếp tục xử lý tài liệu từ đơn vị xử lý kế tiếp sau đơn vị cuối cùng đã được ghi nhận hoàn thành bền vững (`Processing Position`) thay vì xử lý lại từ đầu.
*   **Thử lại (Retry)**: Việc thực hiện lại một thao tác xử lý khi gặp sự cố gián đoạn tạm thời.

---

# 3. Tổng quan Sản phẩm

## 3.1 Bài toán Nghiệp vụ
Hệ thống hiện tại gặp phải các vấn đề lớn sau:
1.  **Nghẽn luồng tiếp nhận**: Việc phân tích tài liệu lớn trực tiếp trên luồng yêu cầu HTTP gây khóa giao diện người dùng và dễ dẫn đến lỗi ngắt kết nối do quá thời gian chờ (timeout).
2.  **Mất mát dữ liệu tiến độ**: Khi hệ thống gặp sự cố vật lý hoặc sập nguồn đột ngột, toàn bộ tiến trình xử lý tài liệu bị mất. Người dùng buộc phải tải tài liệu lên lại và chờ đợi toàn bộ tiến trình từ đầu, gây lãng phí tài nguyên tính toán và chi phí dịch vụ ngoại vi.
3.  **Thiếu tính kháng lỗi**: Lỗi kết nối tạm thời đến các dịch vụ bên thứ ba dễ làm gián đoạn và hỏng toàn bộ tiến trình xử lý tài liệu.

## 3.2 Mục tiêu Nghiệp vụ (Business Objectives)
*   **BO-001 (Tiếp nhận Nhanh)**: Phản hồi tiếp nhận tài liệu tức thì để giải phóng giao diện người dùng.
*   **BO-002 (Xử lý Bất đồng bộ)**: Tách biệt luồng tiếp nhận và luồng xử lý thông qua cơ chế bất đồng bộ ngầm dưới nền.
*   **BO-003 (Bảo toàn Dữ liệu)**: Đảm bảo không thất lạc tài liệu đã tiếp nhận thành công.
*   **BO-004 (Tự động Phục hồi sau Sự cố)**: Tự động khôi phục và tiếp tục xử lý các tác vụ dở dang từ vị trí đã lưu sau khi máy chủ khởi động lại mà không cần can thiệp thủ công.

## 3.3 Nhu cầu Người dùng (User Needs)
*   **UN-001**: Người dùng cần có thể gửi tài liệu mà không phải chờ hệ thống phân tích hoàn thành.
*   **UN-002**: Người dùng không phải gửi lại tài liệu nếu máy chủ gặp sự cố trong lúc xử lý.
*   **UN-003**: Người dùng có thể truy vấn trạng thái xử lý bằng Document ID.

## 3.4 Phạm vi Sản phẩm (In Scope)
*   Hệ thống tiếp nhận, quản lý trạng thái và xử lý bất đồng bộ các tài liệu tải lên.
*   Cơ chế ghi nhận tiến độ xử lý và tự động khôi phục sau sự cố sập máy chủ.
*   Chính sách tự động thử lại khi gặp lỗi tạm thời với các dịch vụ bên ngoài.
*   API truy vấn trạng thái tài liệu theo mã định danh.
*   Ghi nhận log nghiệp vụ mức hệ thống để phục vụ truy vết quá trình xử lý và khôi phục.

## 3.5 Ngoài Phạm vi (Out of Scope)
*   Không xây dựng thuật toán nhận dạng ký tự quang học (OCR) hoặc mô hình trí tuệ nhân tạo (AI/LLM) để phân tích nội dung.
*   Không thiết kế chi tiết giao diện người dùng.
*   Không thiết kế phân quyền chi tiết cấp tài liệu (fine-grained ACL).
*   Không hỗ trợ kiến trúc triển khai đa vùng hoạt động (Multi-region Active-Active).
*   Không hỗ trợ hệ thống worker phân tán hoặc cơ chế điều phối cụm phức tạp.
*   Cơ chế giám sát worker độc lập không thuộc phạm vi yêu cầu của phiên bản sản phẩm này.
*   Không hỗ trợ các trạng thái hủy tác vụ (`CANCELLED`), tạm dừng (`SUSPENDED`), hoặc cách ly tài liệu lỗi lặp lại.

---

# 4. Quy trình Nghiệp vụ

## 4.1 Luồng Xử lý Bình thường
Quy trình xử lý bình thường của một tài liệu diễn ra như sau:
1.  Người dùng tải tài liệu lên thông qua API tiếp nhận.
2.  Hệ thống kiểm tra sơ bộ tính hợp lệ, lưu trữ tài liệu gốc bền vững, tạo mã định danh duy nhất (Document ID), đặt trạng thái tài liệu thành `READY` và lập tức phản hồi kết quả tiếp nhận cho người dùng.
3.  Hệ thống chuyển trạng thái tài liệu sang `PROCESSING` khi tiến trình xử lý nền bắt đầu xử lý tài liệu ở trạng thái `READY`.
4.  Tiến trình xử lý nền tiến hành phân tích tài liệu bằng cách chia nhỏ thành các đơn vị xử lý (`Processing Unit`) và xử lý tuần tự từng đơn vị đó.
5.  Sau khi mỗi đơn vị xử lý được thực hiện thành công, hệ thống thực hiện cập nhật và lưu trữ bền vững vị trí xử lý thành công gần nhất (`Processing Position`).
6.  Khi toàn bộ các đơn vị xử lý của tài liệu hoàn thành, hệ thống cập nhật trạng thái tài liệu sang `COMPLETED` và lưu kết quả tổng hợp cuối cùng.

```text
READY
  ↓
PROCESSING
  ↓
Xử lý từng đơn vị xử lý (Processing Unit)
  ↓
Cập nhật vị trí xử lý thành công gần nhất (Processing Position)
  ↓
Tất cả đơn vị xử lý hoàn thành
  ↓
COMPLETED
```

## 4.2 Luồng Phục hồi sau Sự cố Sập Máy chủ
Khi máy chủ bị sập đột ngột trong quá trình xử lý:
1.  Hệ thống dừng hoạt động đột ngột; các tác vụ đang chạy bị gián đoạn.
2.  Khi máy chủ khởi động lại (restart), hệ thống tự động quét và xác định các tài liệu đang ở trạng thái `PROCESSING` tại thời điểm sập.
3.  Hệ thống chuyển trạng thái của các tài liệu này trở lại `READY`. Vị trí xử lý thành công gần nhất đã được lưu trữ bền vững trước khi sập (`Processing Position`) phải được giữ nguyên.
4.  Khi bắt đầu hoạt động trở lại, hệ thống nhận lại tài liệu từ trạng thái `READY` và chuyển trạng thái tài liệu sang `PROCESSING`.
5.  Hệ thống tiếp tục thực hiện từ đơn vị xử lý kế tiếp sau vị trí xử lý (`Processing Position`) đã lưu. Các đơn vị xử lý đã được xác nhận hoàn thành bền vững trước đó tuyệt đối không được xử lý lại.
6.  Tài liệu được xử lý tiếp tục cho đến khi hoàn thành và chuyển sang trạng thái `COMPLETED`.

```text
PROCESSING
    ↓
[SẬP MÁY CHỦ]
    ↓
[KHỞI ĐỘNG LẠI MÁY CHỦ]
    ↓
Tự động quét & khôi phục (Recovery)
    ↓
Chuyển tài liệu dở dang về READY (Giữ nguyên Processing Position)
    ↓
Bắt đầu xử lý & chuyển sang PROCESSING
    ↓
Tiếp tục xử lý từ vị trí Processing Position đã lưu
    ↓
COMPLETED
```

---

# 5. Vòng đời Tài liệu

## 5.1 Các Trạng thái
Vòng đời của tài liệu được quản lý thông qua 4 trạng thái nghiệp vụ chính sau:
*   **READY (Sẵn sàng)**: Tài liệu đã được tiếp nhận, lưu trữ gốc bền vững và sẵn sàng để đưa vào luồng xử lý nền.
*   **PROCESSING (Đang xử lý)**: Tiến trình xử lý đang thực hiện phân tích tài liệu ngầm dưới nền.
*   **COMPLETED (Đã hoàn thành)**: Xử lý toàn bộ tài liệu thành công, kết quả được lưu trữ bền vững.
*   **FAILED (Thất bại)**: Xử lý thất bại vĩnh viễn do lỗi nghiệp vụ hoặc vượt quá giới hạn thử lại cho phép đối với lỗi tạm thời theo quy định.

## 5.2 Quy tắc Chuyển đổi Trạng thái
Quy tắc chuyển đổi trạng thái của tài liệu bao gồm:
1.  `READY` → `PROCESSING`: Khi tiến trình xử lý nền bắt đầu nhận và xử lý tài liệu.
2.  `PROCESSING` → `COMPLETED`: Khi toàn bộ các đơn vị xử lý của tài liệu hoàn thành thành công và kết quả được lưu trữ bền vững.
3.  `PROCESSING` → `FAILED`: Khi quá trình xử lý gặp lỗi nghiệp vụ vĩnh viễn hoặc lỗi tạm thời vượt quá số lần thử lại tối đa.
4.  `PROCESSING` → `READY`: Khi hệ thống phục hồi sau sự cố khởi động lại máy chủ và xác định tài liệu chưa hoàn thành.

```text
         +-----------+
         |   READY   |<---------------+
         +-----+-----+                |
               |                      |
               | (Bắt đầu xử lý)      | (Khôi phục khi restart máy chủ)
               v                      |
         +-----+-----+                |
         | PROCESSING+----------------+
         +-+-------+-+
           |       |
           |       | (Lỗi xử lý/Vượt giới hạn thử lại)
           |       +----------------------------------> +----------+
           | (Tất cả đơn vị xử lý hoàn thành)           |  FAILED  |
           v                                            +----------+
     +-----+-----+
     | COMPLETED |
     +-----------+
```

## 5.3 Ý nghĩa của Vị trí Xử lý (Processing Position)
*   Vị trí xử lý thành công gần nhất được lưu trữ bền vững để phục vụ khả năng tiếp tục xử lý sau sự cố.
*   Giá trị của vị trí xử lý chỉ được phép cập nhật tiến lên khi một đơn vị xử lý (`Processing Unit`) đã được xử lý thành công và được xác nhận lưu trữ bền vững.
*   Trong quá trình khôi phục sau khi máy chủ khởi động lại, vị trí xử lý tuyệt đối không bị reset hoặc giảm đi.
*   Hệ thống dựa trên giá trị này để xác định điểm bắt đầu xử lý tiếp theo của tiến trình xử lý.

---

# 6. Yêu cầu Tính năng (Functional Requirements)

### FR-001: Tiếp nhận Tài liệu Nhanh
*   **Mô tả**: Hệ thống PHẢI tiếp nhận tài liệu được gửi lên từ người dùng qua API, thực hiện kiểm tra tính hợp lệ sơ bộ, lưu trữ tài liệu gốc bền vững, tạo mã định danh duy nhất (Document ID), đặt trạng thái ban đầu là `READY` và lập tức phản hồi kết quả tiếp nhận cho người dùng.
*   **Tiêu chí Nghiệm thu**:
    *   **AC-FR-001-01**: Sau khi hệ thống đã nhận đủ dữ liệu tài liệu, API PHẢI phản hồi xác nhận tiếp nhận thành công kèm theo Document ID trong thời gian p99 < 300ms (không tính thời gian truyền file vật lý qua mạng).
    *   **AC-FR-001-02**: Hệ thống PHẢI từ chối tiếp nhận tài liệu và trả về thông báo lỗi rõ ràng nếu tài liệu không đáp ứng giới hạn dung lượng hoặc sai định dạng cho phép.
    *   **AC-FR-001-03**: Tài liệu gốc PHẢI được bảo toàn và lưu trữ bền vững trước khi hệ thống trả về phản hồi tiếp nhận thành công.
*   **Độ ưu tiên**: Cao (P0)

### FR-002: Xử lý Nền Bất đồng bộ
*   **Mô tả**: Hệ thống PHẢI tự động chuyển tiếp các tài liệu ở trạng thái `READY` sang tiến trình xử lý ngầm dưới nền một cách bất đồng bộ.
*   **Tiêu chí Nghiệm thu**:
    *   **AC-FR-002-01**: Tiến trình xử lý tài liệu PHẢI chạy ngầm ở luồng độc lập dưới nền và không gây chặn luồng tiếp nhận API của người dùng.
    *   **AC-FR-002-02**: Người dùng PHẢI có thể tiếp tục tải lên tài liệu mới ngay sau khi nhận được Document ID của tài liệu trước đó.
*   **Độ ưu tiên**: Cao (P0)

### FR-003: Lưu trữ Bền vững Vị trí Xử lý
*   **Mô tả**: Hệ thống PHẢI duy trì vị trí xử lý thành công gần nhất của mỗi tài liệu trong quá trình xử lý để phục vụ khả năng tiếp tục xử lý sau sự cố.
*   **Tiêu chí Nghiệm thu**:
    *   **AC-FR-003-01**: Vị trí xử lý PHẢI được lưu trữ bền vững.
    *   **AC-FR-003-02**: Vị trí xử lý chỉ được cập nhật tương ứng với đơn vị xử lý đã được xác nhận hoàn thành.
    *   **AC-FR-003-03**: Khi khôi phục, hệ thống PHẢI sử dụng vị trí đã lưu làm điểm tiếp tục xử lý.
    *   **AC-FR-003-04**: Các đơn vị xử lý đã được xác nhận hoàn thành và lưu trữ bền vững KHÔNG ĐƯỢC xử lý lại trong quá trình khôi phục.
    *   **AC-FR-003-05**: Đơn vị xử lý chưa được xác nhận hoàn thành bền vững tại thời điểm sập máy chủ CÓ THỂ bị xử lý lại.
    *   **AC-FR-003-06**: Vị trí xử lý PHẢI luôn nhất quán với kết quả xử lý đã được ghi nhận bền vững.
*   **Độ ưu tiên**: Cao (P0)

### FR-004: Truy vấn Trạng thái Tài liệu
*   **Mô tả**: Hệ thống PHẢI cung cấp API cho phép người dùng hoặc hệ thống khách truy vấn trạng thái hiện tại của tài liệu bằng Document ID.
*   **Tiêu chí Nghiệm thu**:
    *   **AC-FR-004-01**: API truy vấn trạng thái PHẢI trả về chính xác trạng thái hiện tại của tài liệu (`READY`, `PROCESSING`, `COMPLETED`, `FAILED`).
    *   **AC-FR-004-02**: Thời gian phản hồi truy vấn trạng thái PHẢI dưới 100ms đối với phân vị p95.
    *   **AC-FR-004-03**: Hệ thống KHÔNG ĐƯỢC hiển thị chi tiết tiến độ xử lý nội bộ dạng phần trăm hoặc số lượng đơn vị đã hoàn thành ra bên ngoài cho người dùng thông thường để đảm bảo bảo mật và tính đơn giản của API.
*   **Độ ưu tiên**: Cao (P0)

### FR-005: Tự động Thử lại khi gặp Lỗi Ngoại vi Tạm thời
*   **Mô tả**: Hệ thống PHẢI tự động thực hiện thử lại tiến trình gọi dịch vụ ngoại vi khi gặp các lỗi gián đoạn hoặc phản hồi tạm thời theo chính sách được cấu hình.
*   **Tiêu chí Nghiệm thu**:
    *   **AC-FR-005-01**: Khi gặp lỗi kết nối tạm thời từ dịch vụ ngoại vi, hệ thống PHẢI tự động thử lại theo chính sách giãn cách (retry policy) được cấu hình sẵn.
    *   **AC-FR-005-02**: Hệ thống PHẢI duy trì trạng thái tài liệu là `PROCESSING` trong suốt quá trình thực hiện chính sách thử lại lỗi tạm thời.
    *   **AC-FR-005-03**: Hệ thống PHẢI ghi nhận lỗi vĩnh viễn và chuyển trạng thái tài liệu sang `FAILED` nếu số lần thử lại vượt quá giới hạn cấu hình tối đa mà lỗi vẫn tiếp diễn.
*   **Độ ưu tiên**: Trung bình (P1)

### FR-006: Tự động Khôi phục sau Sự cố sập Máy chủ
*   **Mô tả**: Hệ thống PHẢI tự động khôi phục các tài liệu đang ở trạng thái `PROCESSING` sau khi máy chủ khởi động lại.
*   **Tiêu chí Nghiệm thu**:
    *   **AC-FR-006-01**: Trạng thái tài liệu PHẢI được lưu trữ bền vững.
    *   **AC-FR-006-02**: Vị trí xử lý PHẢI được lưu trữ bền vững.
    *   **AC-FR-006-03**: Sau khi máy chủ khởi động lại, hệ thống PHẢI tự động xác định tất cả các tài liệu đang ở trạng thái `PROCESSING` tại thời điểm xảy ra sự cố.
    *   **AC-FR-006-04**: Các tài liệu này PHẢI được đưa trở lại trạng thái `READY`.
    *   **AC-FR-006-05**: Vị trí xử lý đã ghi nhận trước đó KHÔNG ĐƯỢC bị reset hoặc thay đổi.
    *   **AC-FR-006-06**: Hệ thống PHẢI tiếp tục xử lý từ vị trí xử lý thành công gần nhất đã lưu.
    *   **AC-FR-006-07**: Tài liệu PHẢI được tiếp tục xử lý mà không yêu cầu người dùng phải tải lên lại tài liệu gốc.
    *   **AC-FR-006-08**: Đơn vị xử lý đã hoàn thành và được lưu bền vững thành công KHÔNG ĐƯỢC xử lý lại.
    *   **AC-FR-006-09**: Đơn vị xử lý chưa được lưu bền vững thành công tại thời điểm sập máy chủ CÓ THỂ bị xử lý lại.
    *   **AC-FR-006-10**: Quá trình khôi phục PHẢI diễn ra hoàn toàn tự động và không yêu cầu bất kỳ thao tác thủ công nào từ phía quản trị viên hoặc người dùng.
*   **Độ ưu tiên**: Cao (P0)

---

# 7. Quy tắc Nghiệp vụ (Business Rules)

*   **BR-001 (Tính hợp lệ của tài liệu đầu vào)**: Hệ thống chỉ chấp nhận tài liệu có định dạng được hỗ trợ và nằm trong giới hạn kích thước tối đa cho phép. Các tài liệu không hợp lệ phải bị từ chối ngay lập tức tại bước tiếp nhận.
*   **BR-002 (Quy tắc xử lý độc quyền)**: Tại bất kỳ thời điểm nào, hệ thống phải đảm bảo một tài liệu chỉ có một quá trình xử lý hợp lệ và kết quả cuối cùng không bị ghi nhận trùng lặp hoặc mâu thuẫn.
*   **BR-003 (Nguyên tắc lũy tiến - Ghi nhận tiến độ một chiều)**: Vị trí xử lý phải được ghi nhận tăng tiến và không thể đảo ngược hoặc giảm đi. Không được xử lý lại hoặc ghi đè kết quả của các phần tài liệu đã được ghi nhận hoàn thành.
*   **BR-004 (Phân loại và xử lý lỗi)**: 
    *   *Lỗi tạm thời*: Lỗi mất kết nối mạng tạm thời, lỗi nghẽn dịch vụ ngoại vi. Hệ thống thực hiện tự động thử lại theo chính sách cấu hình và giữ trạng thái `PROCESSING`.
    *   *Lỗi vĩnh viễn*: Dữ liệu sai định dạng cấu trúc nội bộ, lỗi logic nghiệp vụ. Hệ thống dừng xử lý ngay lập tức, chuyển trạng thái tài liệu sang `FAILED` và ghi nhận nguyên nhân lỗi.
*   **BR-005 (Tính nhất quán khi tiếp tục xử lý)**: Các đơn vị xử lý đã được ghi nhận thành công và lưu bền vững trước thời điểm xảy ra sự cố gián đoạn tuyệt đối không được xử lý lại. Chỉ những đơn vị xử lý chưa được xác nhận bền vững tại thời điểm crash mới có thể bị xử lý lại sau khi hệ thống khôi phục hoạt động. Kết quả tổng hợp cuối cùng của tài liệu phải đảm bảo tính toàn vẹn và không chứa dữ liệu trùng lặp.

---

# 8. Yêu cầu Phi Tính Năng (Non-Functional Requirements)

## 8.1 Hiệu năng (Performance)
*   **NFR-001**: API tiếp nhận tài liệu PHẢI phản hồi trong thời gian p99 < 300ms (đáp ứng tiêu chí AC-FR-001-01).
*   **NFR-002**: API truy vấn trạng thái tài liệu PHẢI phản hồi trong thời gian p95 < 100ms (đáp ứng tiêu chí AC-FR-004-02).

## 8.2 Độ sẵn sàng (Availability)
*   **NFR-003**: API tiếp nhận tài liệu PHẢI đạt độ sẵn sàng tối thiểu 99.99%. Chu kỳ đo lường tính toán là 30 ngày liên tục, không bao gồm thời gian bảo trì hệ thống được lên lịch trước và sự cố do lỗi kết nối mạng phía người dùng. Việc khởi động lại máy chủ do sự cố nằm trong phạm vi tính toán độ sẵn sàng.

## 8.3 Độ tin cậy (Reliability / Durability)
*   **NFR-004 (Bảo toàn Tài liệu Gốc - Document Durability)**: Hệ thống PHẢI đảm bảo tỷ lệ mất mát tài liệu gốc đã được xác nhận tiếp nhận thành công (đã nhận Document ID) là 0% (đáp ứng tiêu chí AC-FR-001-03).
*   **NFR-005 (Lưu trữ Bền vững Tiến độ - Processing Recovery)**: Toàn bộ trạng thái xử lý từng phần và vị trí xử lý PHẢI được ghi nhận vào lưu trữ bền vững ngay khi hoàn thành để tránh mất mát dữ liệu tiến độ do sự cố đột ngột.

## 8.4 Khả năng Phục hồi (Recoverability)
*   **NFR-006**: Thời gian khôi phục tự động (RTO) PHẢI dưới 120 giây kể từ thời điểm máy chủ khởi động lại thành công cho đến khi toàn bộ tài liệu bị treo dở dang ở trạng thái `PROCESSING` được đưa về `READY` và sẵn sàng xử lý tiếp (đáp ứng tiêu chí AC-FR-006-10).

## 8.5 Tính Nhất quán (Consistency / Processing Correctness)
*   **NFR-007**: Trạng thái hiển thị qua API truy vấn và trạng thái thực tế trong hệ thống PHẢI luôn đồng nhất (đáp ứng tiêu chí AC-FR-004-01).
*   **NFR-008**: Kết quả phân tích cuối cùng PHẢI đầy đủ, chính xác, đúng thứ tự ban đầu và không bị trùng lặp dữ liệu sau khi khôi phục xử lý (đáp ứng tiêu chí AC-FR-006-08).
*   **NFR-009**: Vị trí xử lý đã lưu PHẢI luôn nhất quán với kết quả xử lý thực tế được lưu trữ bền vững (đáp ứng tiêu chí AC-FR-003-06).

## 8.6 Khả năng Giám sát (Observability)
*   **NFR-010**: Hệ thống PHẢI ghi nhận các sự kiện nghiệp vụ quan trọng sử dụng Document ID làm khóa tham chiếu chính bao gồm: tiếp nhận thành công, bắt đầu xử lý, cập nhật trạng thái tài liệu, ghi nhận vị trí xử lý, thông tin lỗi chi tiết, sự cố sập/khởi động lại máy chủ và sự kiện bắt đầu/hoàn thành quá trình khôi phục. Tài liệu này không quy định các giải pháp giám sát cụ thể.

## 8.7 Khả năng Bảo trì (Maintainability)
*   **NFR-011**: Các cấu hình hệ thống (giới hạn kích thước tài liệu tối đa, số lần thử lại tối đa, và khoảng cách thời gian thử lại) PHẢI có thể chỉnh sửa mà không cần thay đổi hay biên dịch lại mã nguồn (đáp ứng tiêu chí AC-FR-005-01).

## 8.8 An toàn Bảo mật (Security)
*   **NFR-012**: Tài liệu tải lên PHẢI được kiểm tra sơ bộ tính toàn vẹn và cấu trúc tệp để tránh các cuộc tấn công khai thác lỗi bảo mật tệp tin (đáp ứng tiêu chí AC-FR-001-02).
*   **NFR-013**: Hệ thống PHẢI đảm bảo an toàn dữ liệu lưu trữ tĩnh đối với tài liệu gốc và kết quả xử lý.

---

# 9. SLA và Tiêu chí Thành công (SLA and Success Criteria)

| Ký hiệu | Chỉ số SLA / Tiêu chí Thành công | Tiêu chuẩn Cam kết | Cách thức Đo lường |
| :--- | :--- | :--- | :--- |
| **SLA-001** | Thời gian phản hồi tiếp nhận tài liệu | **p99 < 300ms** (mục tiêu trung bình < 200ms). | Đo khoảng thời gian từ lúc nhận đủ byte cuối cùng của tài liệu cho đến khi API trả về xác nhận tiếp nhận thành công kèm Document ID. |
| **SLA-002** | Tỷ lệ mất mát tài liệu | **0%** (Tuyệt đối không thất lạc tài liệu đã tiếp nhận thành công). | (Tổng số tài liệu bị mất tích khỏi trạng thái hệ thống sau sự cố / Tổng số tài liệu đã tiếp nhận thành công trước sự cố) * 100%. Chỉ số này phải luôn bằng 0. |
| **SLA-003** | Thời gian khôi phục tác vụ (RTO) | **< 120 giây** kể từ khi máy chủ khởi động lại. | Khoảng thời gian từ khi tiến trình hệ thống hoạt động trở lại cho đến khi toàn bộ các tài liệu ở trạng thái `PROCESSING` tại thời điểm sập được tự động đưa về `READY` và sẵn sàng để worker nhận lại. |
| **SLA-004** | Thời gian phản hồi truy vấn trạng thái | **p95 < 100ms**. | Thời gian phản hồi API truy vấn trạng thái bằng Document ID. |
| **SLA-005** | Nhất quán Vị trí Khôi phục | **Hệ thống phải tiếp tục từ vị trí đã lưu gần nhất.** | Hệ thống phải resume từ vị trí xử lý (`Processing Position`) cuối cùng được ghi nhận bền vững trước sự cố. Chỉ đơn vị xử lý chưa được ghi nhận bền vững thành công tại thời điểm crash mới có thể bị xử lý lại. |

---

# 10. Kịch bản Lỗi và Khôi phục (Failure and Recovery Scenarios)

### FS-001: Xử lý thành công bình thường (Successful Processing)
*   **Mã Kịch bản**: FS-001
*   **Kịch bản**: Quá trình xử lý tài liệu diễn ra suôn sẻ không gặp sự cố.
*   **Điều kiện tiên quyết**: Tài liệu ở trạng thái `READY`.
*   **Tác nhân kích hoạt**: Bộ xử lý nền nhận tài liệu để bắt đầu xử lý.
*   **Hành vi hệ thống mong muốn**:
    1. Trạng thái tài liệu chuyển sang `PROCESSING`.
    2. Tiến trình xử lý tuần tự từng đơn vị xử lý (ví dụ từ 1 đến 100).
    3. Sau khi mỗi đơn vị được xử lý thành công, vị trí xử lý (`Processing Position`) được lưu trữ bền vững (tăng dần).
    4. Khi đơn vị cuối cùng hoàn thành, hệ thống ghi nhận kết quả tổng hợp.
*   **Trạng thái cuối mong muốn**: Tài liệu có trạng thái `COMPLETED` và kết quả phân tích đầy đủ.

### FS-002: Sập máy chủ khi đang xử lý tài liệu (Server Crash During Processing)
*   **Mã Kịch bản**: FS-002
*   **Kịch bản**: Máy chủ bị sập đột ngột trong khi tiến trình đang thực hiện phân tích tài liệu dở dang.
*   **Điều kiện tiên quyết**: Tài liệu đang xử lý, vị trí xử lý thành công gần nhất đã được lưu bền vững là `Processing Position = 70`.
*   **Tác nhân kích hoạt**: Máy chủ sập đột ngột (mất điện, sập phần cứng, tắt tiến trình) khi đang xử lý đơn vị thứ 71.
*   **Hành vi hệ thống mong muốn**:
    1. Tiến trình xử lý bị gián đoạn. Máy chủ khởi động lại (restart).
    2. Sau khi restart, hệ thống quét và xác định tài liệu này ở trạng thái `PROCESSING`.
    3. Trạng thái tài liệu tự động chuyển về `READY`. Vị trí xử lý đã ghi nhận (`70`) được giữ nguyên không thay đổi hay bị reset.
    4. Hệ thống nhận lại tài liệu từ trạng thái `READY`, chuyển sang `PROCESSING` và tiếp tục xử lý từ đơn vị xử lý thứ 71.
    5. Đơn vị thứ 71 có thể được xử lý lại từ đầu nếu nó chưa được xác nhận hoàn thành bền vững trước lúc sập.
    6. Các đơn vị từ 1 đến 70 không bị xử lý lại và kết quả của chúng không bị ghi nhận trùng lặp.
*   **Trạng thái cuối mong muốn**: Tài liệu tiếp tục xử lý đến đơn vị 100 và đạt trạng thái `COMPLETED`.

---

# 11. Giả định (Assumptions)

1.  **Tính phân rã của tài liệu**: Tài liệu đầu vào có thể được phân tách thành các đơn vị xử lý tuần tự độc lập (`Processing Unit`) và có thể xử lý riêng biệt mà không làm hỏng logic của tài liệu.
2.  **Khả năng biểu diễn tiến độ**: Điểm hoàn thành của quá trình xử lý có thể được biểu diễn chính xác thông qua giá trị vị trí xử lý (`Processing Position`).
3.  **Lưu trữ bền vững**: Hệ thống lưu trữ cơ sở dữ liệu và lưu trữ tài liệu vật lý đảm bảo tính toàn vẹn dữ liệu, không bị mất mát dữ liệu đã được ghi nhận thành công trong phạm vi mô hình lỗi sập máy chủ thông thường.
4.  **Tự khởi động của hệ thống**: Các tiến trình xử lý ngầm tự động hoạt động trở lại sau khi máy chủ khởi động lại thành công.
5.  **Chấp nhận khả năng xử lý lại đơn vị dở dang**: Đơn vị xử lý đang thực hiện dở dang và chưa được xác nhận thành công bằng việc ghi nhận vị trí xử lý bền vững tại thời điểm crash có thể được xử lý lại sau khi phục hồi.
6.  **Triển khai đơn lẻ**: Hệ thống hiện tại chỉ cần hỗ trợ triển khai trên một máy chủ duy nhất (single-server deployment) mà không cần xem xét các cơ chế đồng bộ hóa phức tạp của môi trường phân tán.

---

# 12. Ràng buộc (Constraints)

*   **Triển khai trên máy chủ đơn (Single-server deployment)**: Phiên bản hiện tại chỉ triển khai trên một máy chủ duy nhất, không yêu cầu thiết kế phân tán phức tạp.
*   **Cơ chế giám sát worker ngoài phạm vi**: Các cơ chế giám sát sức khỏe worker độc lập nằm ngoài phạm vi yêu cầu của phiên bản sản phẩm này.
*   **Xử lý bất đồng bộ phi thời gian thực**: Quá trình xử lý tài liệu được thực hiện ngầm và không cam kết hoàn thành tức thời; thời gian hoàn thành phụ thuộc dung lượng tài liệu và tốc độ của dịch vụ ngoại vi.
*   **Khả năng Reprocess ở mức Đơn vị xử lý**: Hệ thống chấp nhận ràng buộc có khả năng phải xử lý lại đơn vị xử lý atomic cuối cùng chưa được ghi nhận thành công tại thời điểm crash.
*   **Hạ tầng công nghệ được phê duyệt**: Hệ thống phải được triển khai trong môi trường công nghệ đã được dự án phê duyệt. Không quy định các thư viện, framework hay database engine cụ thể trong tài liệu này.

---

# 13. Phụ lục (Appendix)

*(Không có yêu cầu đặc biệt bổ sung)*
