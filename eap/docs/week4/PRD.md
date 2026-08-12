# TÀI LIỆU YÊU CẦU SẢN PHẨM (PRD)
**Tuần 4: Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer)**

---

## 1. Quản lý Tài liệu (Document Control)

### 1.1. Thông tin Tài liệu
| Trường thông tin | Giá trị |
| :--- | :--- |
| **Tiêu đề Tài liệu** | Tài liệu Yêu cầu Sản phẩm - Tuần 4 (PRD-004) |
| **Dự án** | VCC Enterprise Archive Platform (VCC-EAP) |
| **Phiên bản** | 1.0 |
| **Trạng thái** | Hoàn thành |
| **Tác giả** | Senior Product Analyst |
| **Ngày phát hành** | 2026-08-07 |

### 1.2. Lịch sử Thay đổi
| Phiên bản | Ngày | Tác giả | Mô tả Thay đổi | Lý do |
| :--- | :--- | :--- | :--- | :--- |
| 1.0 | 2026-08-07 | Senior Product Analyst | Phiên bản đầu tiên. | |

---

## 2. Giới thiệu (Introduction)

### 2.1. Bối cảnh Nghiệp vụ
Doanh nghiệp lưu trữ lượng lớn tài liệu chính sách, quy chế và tài liệu nghiệp vụ. Nhân viên thường mất nhiều thời gian để tra cứu thông tin khi chỉ sử dụng phương pháp tìm kiếm từ khóa chính xác truyền thống. Tính năng tìm kiếm tương đồng ngữ nghĩa (Semantic Search) cho phép nhân viên đặt câu hỏi bằng ngôn ngữ tự nhiên và hệ thống tự động trả về chính xác đoạn văn bản chứa thông tin liên quan nhất, giúp tối ưu hóa hiệu suất làm việc.

> [!IMPORTANT]
> **Định nghĩa về Basic RAG trong Tuần 4**: Trong phạm vi phát triển của Tuần 4, hệ thống chỉ triển khai **Tầng Truy xuất dữ liệu (Retrieval Layer)**. Hệ thống nhận câu hỏi bằng ngôn ngữ tự nhiên, tìm kiếm các đoạn văn bản tương đồng ngữ nghĩa trong kho tri thức đã được số hóa và hiển thị kết quả trực tiếp cho người dùng kèm trích dẫn nguồn. Hệ thống **không** thực hiện việc tự sinh câu trả lời bằng LLM (LLM Generation/RAG Chatbot) hay tóm tắt nội dung tài liệu.

### 2.2. Mục đích
Tài liệu này đặc tả các yêu cầu sản phẩm đối với phân hệ **Số hóa & Tra cứu Tri thức Cơ bản (Basic RAG - Retrieval Layer)**. Tài liệu tập trung mô tả các hành vi chức năng từ góc nhìn của sản phẩm ("Làm cái gì và Tại sao"), thiết lập các ràng buộc bảo mật phòng ban và các tiêu chuẩn chất lượng (SLA) phục vụ cho quá trình thiết kế hệ thống.

### 2.3. Phạm vi (Scope)
* **Quy trình Số hóa**: Tự động trích xuất nội dung văn bản của tài liệu gốc (PDF, Word, Excel), thực hiện phân mảnh ngữ nghĩa (Semantic Chunking) và tạo biểu diễn vector ngữ nghĩa cục bộ để lưu trữ bền vững.
* **Quy trình Tra cứu (Retrieval Layer)**: Cung cấp API tiếp nhận câu hỏi bằng ngôn ngữ tự nhiên, thực hiện so khớp vector tương đồng ngữ nghĩa và trả về tối đa Top-3 đoạn văn bản liên quan nhất kèm thông tin trích dẫn nguồn.
* **Kiểm soát Bảo mật**: Áp dụng quy tắc cô lập phòng ban nghiêm ngặt (Department Isolation), phân giải liên kết chia sẻ tài liệu (Alias Sharing), bảo vệ thông tin BOARD và xử lý tài liệu bị xóa logic.
* **Ngoài phạm vi**: Không tự động sinh câu trả lời bằng LLM (RAG Generation), không xếp hạng lại (Re-ranking), không tìm kiếm lai (Hybrid Search), không xử lý nhận dạng ký tự từ hình ảnh (OCR).

### 2.4. Thuật ngữ và Định nghĩa
* **Tìm kiếm tương đồng ngữ nghĩa (Semantic Search)**: Phương thức tra cứu dựa trên ý nghĩa của câu hỏi thay vì so khớp từ khóa chính xác.
* **Phân mảnh ngữ nghĩa (Semantic Chunking)**: Chia nhỏ văn bản gốc thành các đoạn văn bản (chunk) dựa trên tính liên mạch ý nghĩa của các câu liên tiếp.
* **Vector biểu diễn ngữ nghĩa (Embedding)**: Biểu diễn toán học của một đoạn văn bản dưới dạng vector số thực để đo đạc độ tương đồng ý nghĩa.
* **Cách ly Phòng ban (Department Isolation)**: Người dùng chỉ được tìm kiếm tài liệu thuộc sở hữu của phòng ban mình hoặc được phòng ban khác chia sẻ.
* **Liên kết chia sẻ (Alias Sharing)**: Chia sẻ quyền truy cập tài liệu sang phòng ban khác dưới dạng liên kết logic, không nhân bản tệp vật lý.
* **Xóa logic (Soft Delete)**: Trạng thái tài liệu bị ẩn khỏi hệ thống và không tham gia tra cứu nhưng không bị xóa vật lý ngay lập tức.

---

## 3. Mô tả Tổng quan (Overall Description)

### 3.1. Tác nhân Hệ thống (Actors)
* **Nhân viên Nghiệp vụ**: Người dùng thuộc các phòng ban (như HR, Finance, R&D) có nhu cầu đặt câu hỏi tự nhiên để tra cứu thông tin trong phạm vi phòng ban hoặc tài liệu được chia sẻ hợp lệ.
* **Ban Giám đốc (BOARD)**: Người dùng có quyền tra cứu tài liệu tuyệt mật của BOARD.
* **Quản trị viên (SYSTEM_ADMIN)**: Người quản trị kỹ thuật hệ thống, không có quyền truy cập nội dung tài liệu và không được sử dụng tính năng tìm kiếm ngữ nghĩa.

### 3.2. Giả định và Sự phụ thuộc
* **Tài liệu hợp lệ**: Tài liệu tải lên hệ thống là tài liệu định dạng kỹ thuật số chứa văn bản có thể trích xuất trực tiếp (không phải ảnh quét).
* **Mô hình nhúng cục bộ**: Hệ thống tự chủ việc tạo vector nhúng thông qua mô hình nhúng cục bộ chạy in-process trên máy chủ ứng dụng, không gửi dữ liệu ra API bên ngoài để bảo vệ an toàn thông tin.

---

## 4. Bối cảnh Hệ thống (System Context - C1)

Sơ đồ ngữ cảnh hệ thống (C1) thể hiện các tác nhân tương tác với hệ thống VCC-EAP đối với chức năng tra cứu tri thức:

```mermaid
graph TD
    Employee["Nhân viên Nghiệp vụ (HR, Finance, R&D)"]
    BoardUser["Thành viên Ban Giám đốc (BOARD)"]
    SysAdmin["Quản trị viên (SYSTEM_ADMIN)"]
    
    SystemEAP["Hệ thống VCC-EAP<br/>(Spring Boot Layered Monolith)"]
    
    Employee -->|Tải tài liệu, chia sẻ Alias, đặt câu hỏi tra cứu ngữ nghĩa| SystemEAP
    BoardUser -->|Đặt câu hỏi tra cứu tài liệu mật BOARD| SystemEAP
    SysAdmin -->|Quản trị hệ thống, không được xem hoặc tìm tài liệu| SystemEAP
    
    style SystemEAP fill:#1F4E79,stroke:#1A365D,stroke-width:2px,color:#FFFFFF
    style Employee fill:#D84315,stroke:#BF360C,stroke-width:2px,color:#FFFFFF
    style BoardUser fill:#C62828,stroke:#B71C1C,stroke-width:2px,color:#FFFFFF
    style SysAdmin fill:#37474F,stroke:#263238,stroke-width:2px,color:#FFFFFF
```

---

## 5. Yêu cầu Chức năng (Functional Requirements)

### 5.1. Quy trình Số hóa Tài liệu
* **FR-4.1. Tiếp nhận tài liệu**: Hệ thống tự động phát hiện và tiếp nhận các tài liệu mới tải lên ở trạng thái sẵn sàng số hóa.
* **FR-4.2. Trích xuất văn bản**:
  * Trích xuất nội dung văn bản từ các định dạng tệp được hỗ trợ: PDF, Word (docx), Excel (xlsx).
  * Bảo đảm giữ nguyên định dạng chữ tiếng Việt có dấu.
  * Trong trường hợp tệp không chứa văn bản trích xuất được, hệ thống chuyển tài liệu sang trạng thái lỗi xử lý và lưu thông tin lỗi để phục vụ công tác quản trị.
* **FR-4.3. Phân mảnh văn bản (Chunking)**:
  * Hệ thống áp dụng phương pháp **Phân mảnh Ngữ nghĩa Cửa sổ trượt cải tiến** (Improved Sliding Window Semantic Chunking). Phương pháp này tự động gom nhóm các câu liên tiếp có độ tương đồng ngữ nghĩa cao bằng cách so khớp ngữ cảnh giữa hai cửa sổ trượt trái và phải, kết hợp với cơ chế gối đầu (overlap) thông minh giữa các phân đoạn.
  * Hệ thống tích hợp cơ chế nhận biết cấu trúc tài liệu: Ưu tiên ngắt phân đoạn cưỡng bức tại các **Neo cấu trúc** tự nhiên (như ranh giới Chương, Điều, Mục, hoặc thẻ Hỏi/Đáp) nhằm bảo vệ tính toàn vẹn của các khối nghiệp vụ và tránh gộp nhầm nội dung.
  * Các tham số phân mảnh (ngưỡng tương đồng ngữ nghĩa, kích thước tối đa của chunk, số câu gối đầu) được thiết kế dưới dạng cấu hình hệ thống để có thể tinh chỉnh tại runtime mà không cần build lại ứng dụng.
* **FR-4.4. Tạo vector biểu diễn ngữ nghĩa**:
  * Hệ thống tự động chuyển đổi từng đoạn văn bản thành một vector biểu diễn ngữ nghĩa có số chiều cố định là **1024 chiều** (sử dụng mô hình nhúng cục bộ).
  * Mỗi đoạn văn bản được liên kết chặt chẽ với vector ngữ nghĩa tương ứng của nó để phục vụ so khớp.
* **FR-4.5. Lưu trữ và xác nhận số hóa**:
  * Lưu trữ bền vững nội dung đoạn văn bản, vector nhúng 1024 chiều và thông tin truy vết nguồn gốc (mã tài liệu, vị trí đoạn văn bản).
  * Sau khi hoàn tất lưu trữ toàn bộ các đoạn văn bản của tài liệu, hệ thống tự động cập nhật trạng thái tài liệu sang hoàn thành số hóa. Quy trình số hóa chạy bất đồng bộ và có cơ chế tự phục hồi tác vụ khi hệ thống khởi động lại.

### 5.2. Quy trình Tìm kiếm Tương đồng Ngữ nghĩa (Retrieval Layer)
* **FR-4.6. Cổng API Tìm kiếm**:
  * Tiếp nhận yêu cầu tìm kiếm từ người dùng dưới dạng câu hỏi ngôn ngữ tự nhiên.
  * Tự động xác thực danh tính và xác định phòng ban trực thuộc của người dùng thực hiện yêu cầu.
* **FR-4.7. Truy xuất tương đồng**:
  * Chuyển đổi câu hỏi của người dùng thành vector ngữ nghĩa 1024 chiều (sử dụng cùng mô hình nhúng với quy trình số hóa).
  * Thực hiện tìm kiếm và đối sánh tương đồng vector giữa vector câu hỏi và vector các đoạn văn bản trong cơ sở dữ liệu dựa trên phép đo Cosine.
  * Sắp xếp kết quả tìm kiếm theo thứ tự độ liên quan ngữ nghĩa giảm dần và trả về tối đa số lượng đoạn văn bản theo yêu cầu (mặc định hiển thị tối đa 3 kết quả liên quan nhất).
  * Mỗi đoạn văn bản trả về bắt buộc phải đi kèm thông tin nguồn gốc tài liệu (như mã tài liệu, tiêu đề tài liệu) để phục vụ đối chiếu nguồn trích dẫn.
* **FR-4.8. Cách ly phòng ban tuyệt đối (Department Isolation)**:
  * Kết quả tìm kiếm của người dùng phải được giới hạn trong phạm vi phòng ban trực thuộc của người dùng đó, ngoại trừ trường hợp tài liệu được chia sẻ hợp lệ.
  * Tài liệu thuộc phòng ban BOARD là tuyệt mật và chỉ các thành viên BOARD mới được phép tìm kiếm; tài liệu BOARD không được phép chia sẻ cho bất kỳ phòng ban nào khác.
* **FR-4.9. Tìm kiếm tài liệu chia sẻ qua Alias**:
  * Cho phép người dùng tìm kiếm và đọc nội dung các đoạn văn bản thuộc tài liệu của phòng ban khác nếu tài liệu đó đã được tạo liên kết chia sẻ (Alias) hợp lệ tới phòng ban của người dùng hiện tại.
  * Khi trả về kết quả tìm kiếm qua Alias, hệ thống hiển thị tiêu đề do phòng ban gửi thiết lập trên liên kết Alias tại thời điểm chia sẻ, thay vì hiển thị tiêu đề gốc của tài liệu.
* **FR-4.10. Kiểm soát trạng thái hiệu lực**:
  * Hệ thống loại bỏ các tài liệu gốc đã bị đánh dấu xóa logic (Soft Delete) khỏi tập dữ liệu tra cứu.
  * Nếu một liên kết chia sẻ (Alias) bị đánh dấu xóa logic (Soft Delete), người dùng thuộc phòng ban nhận liên kết đó lập tức mất quyền truy cập và tìm kiếm tài liệu tương ứng.

---

## 6. Yêu cầu Phi chức năng (Non-functional Requirements)

### 6.1. Hiệu năng & Chất lượng (Performance & Quality)
* **NFR-4.1. Mục tiêu độ trễ tìm kiếm (SLA)**: Thời gian phản hồi cho một yêu cầu tìm kiếm tương đồng ngữ nghĩa (từ lúc gửi câu hỏi đến lúc nhận kết quả) hướng tới mục tiêu đạt **dưới 500ms** trong điều kiện vận hành bình thường.
* **NFR-4.2. Thời gian xử lý số hóa (SLA)**: Quy trình số hóa bất đồng bộ đối với một tài liệu tiêu chuẩn dài 10 trang hướng tới mục tiêu hoàn thành **dưới 10 giây** kể từ khi hệ thống tiếp nhận.
* **NFR-4.3. Chất lượng tìm kiếm (SLA)**: Đoạn văn bản chứa thông tin cần tìm xuất hiện trong **Top 3** kết quả tìm kiếm ngữ nghĩa đầu tiên khi thực hiện đánh giá kiểm thử chất lượng trên tập Ground Truth gồm 50 câu hỏi nghiệp vụ đã chuẩn hóa (Hit Rate @ Top-3).

### 6.2. Độ tin cậy (Reliability)
* **NFR-4.4. Đồng bộ hóa quyền truy cập**: Khi tài liệu hoặc liên kết Alias bị đánh dấu xóa logic, hệ thống phải cập nhật lập tức hiệu lực truy cập trong kết quả tra cứu ngữ nghĩa.

### 6.3. An toàn Bảo mật (Security)
* **NFR-4.5. Ngăn ngừa rò rỉ dữ liệu (SLA)**: Đảm bảo an toàn thông tin, không xảy ra rò rỉ chéo dữ liệu giữa các phòng ban hoặc từ BOARD ra ngoài. Toàn bộ cơ chế kiểm tra quyền truy cập phòng ban và Alias phải được thực hiện triệt để ngay trong truy vấn dữ liệu ở tầng lưu trữ, không được lấy kết quả thô lên bộ nhớ ứng dụng rồi mới lọc.
* **NFR-4.6. Chặn quyền quản trị viên**: Tài khoản quản trị hệ thống (SYSTEM_ADMIN) bị tước quyền tìm kiếm ngữ nghĩa và không được phép xem nội dung chi tiết của bất kỳ tài liệu nghiệp vụ nào.

### 6.4. Khả năng Mở rộng (Scalability)
* **NFR-4.7. Quy mô chỉ mục**: Hệ thống phải duy trì hiệu năng tìm kiếm ổn định (dưới 500ms) khi số lượng tài liệu số hóa tăng trưởng theo nhu cầu sử dụng thực tế của doanh nghiệp.
* **NFR-4.8. Khả năng chống quá tải khi xử lý tài liệu lớn**: Hệ thống phải hỗ trợ cơ chế cuốn chiếu phân đoạn (Incremental Section Processing) và giới hạn kích thước Batch tối đa khi nhúng để đảm bảo máy chủ không bị tràn bộ nhớ (OOM) và CPU không bị quá tải khi người dùng tải lên tài liệu siêu lớn (từ 100 đến 1000 trang).

---

## 7. Quy tắc Nghiệp vụ (Business Rules)

* **BR-4.1. Điều kiện số hóa**: Chỉ số hóa tài liệu đang ở trạng thái hoạt động và chưa bị đánh dấu xóa.
* **BR-4.2. Điều kiện tham gia tra cứu**: Chỉ các đoạn văn bản thuộc tài liệu đã hoàn tất số hóa thành công mới được tham gia vào quá trình tìm kiếm tương đồng vector.
* **BR-4.3. Định dạng hiển thị trích dẫn**: Kết quả tìm kiếm hiển thị đoạn nội dung liên quan để người dùng đọc nhanh, kèm theo link liên kết để mở tài liệu gốc nếu người dùng có đủ quyền hạn truy cập tài liệu đó.
* **BR-4.4. Phân quyền chia sẻ tài liệu**: Chỉ phòng sở hữu tài liệu gốc mới có quyền tạo liên kết chia sẻ (Alias) sang phòng ban khác.
* **BR-4.5. Ràng buộc bảo mật của BOARD**: Tài liệu thuộc phòng ban BOARD là tuyệt mật. Hệ thống cấm mọi hành vi tạo liên kết chia sẻ tài liệu BOARD ra ngoài, đồng thời BOARD cũng không tiếp nhận liên kết chia sẻ từ các phòng ban khác.

---

## 8. Kịch bản Nghiệm thu (Acceptance Criteria)

### TC-4.1. Tìm kiếm ngữ nghĩa thành công trong phòng ban
* **GIVEN**: Người dùng thuộc phòng ban HR đã đăng nhập. Hệ thống có tài liệu `Doc_HR_01` thuộc phòng HR đã hoàn thành số hóa, chứa đoạn văn bản: *"Chính sách hỗ trợ phương tiện công cộng áp dụng cho nhân viên đi xe buýt đi làm với mức trợ cấp 200,000 VND/tháng"*.
* **WHEN**: Người dùng gửi yêu cầu tìm kiếm với câu hỏi bằng ngôn ngữ tự nhiên: *"Tôi đi xe buýt đi làm có được trợ cấp không?"*.
* **THEN**:
  1. Thời gian trả kết quả dưới 500ms.
  2. Đoạn văn bản chứa quy định trợ cấp xe buýt hiển thị trong Top 3 kết quả đầu tiên.
  3. Thông tin trích dẫn hiển thị đúng mã tài liệu và tiêu đề của `Doc_HR_01`.

### TC-4.2. Cách ly phòng ban tuyệt đối
* **GIVEN**: Người dùng thuộc phòng ban R&D đã đăng nhập. Hệ thống có tài liệu `Doc_Finance_01` thuộc phòng FINANCE đã hoàn thành số hóa chứa thông tin lương thưởng và tài liệu này **chưa** được chia sẻ cho phòng R&D.
* **WHEN**: Người dùng R&D gửi yêu cầu tìm kiếm với câu hỏi: *"Mức lương thưởng cuối năm là bao nhiêu?"*.
* **THEN**: Kết quả trả về không chứa bất kỳ đoạn văn bản nào trích từ tài liệu `Doc_Finance_01`.

### TC-4.3. Tìm kiếm tài liệu chia sẻ qua Alias
* **GIVEN**:
  - Tài liệu gốc `Doc_Finance_02` thuộc phòng FINANCE có tiêu đề gốc là *"Quy chế Chi tiêu Nội bộ VCCorp"*, đã hoàn thành số hóa, chứa nội dung: *"Nhân viên đi công tác được thanh toán tối đa 1,000,000 VND tiền phòng/ngày"*.
  - Phòng FINANCE đã tạo một liên kết chia sẻ (Alias) tài liệu này sang phòng HR với tiêu đề hiển thị thiết lập là *"Hướng dẫn chi phí công tác cho nhân sự"*.
  - Người dùng thuộc phòng HR thực hiện tìm kiếm.
* **WHEN**: Người dùng HR gửi yêu cầu tìm kiếm với câu hỏi: *"Đi công tác được chi bao nhiêu tiền phòng?"*.
* **THEN**:
  1. Kết quả tìm kiếm hiển thị đoạn văn bản trích từ tài liệu gốc `Doc_Finance_02`.
  2. Tên tài liệu đi kèm kết quả hiển thị tiêu đề là *"Hướng dẫn chi phí công tác cho nhân sự"* (tiêu đề thiết lập trên Alias) thay vì tiêu đề gốc của tài liệu.

### TC-4.4. Không tìm kiếm trên tài liệu đã xóa
* **GIVEN**: Tài liệu `Doc_HR_02` thuộc phòng HR đã bị đánh dấu xóa logic (Soft Delete) trên hệ thống.
* **WHEN**: Người dùng thuộc phòng HR thực hiện tìm kiếm với câu hỏi khớp với nội dung của `Doc_HR_02`.
* **THEN**: Kết quả trả về không chứa bất kỳ nội dung nào thuộc tài liệu `Doc_HR_02`.

### TC-4.5. Nghiệm thu chất lượng tìm kiếm (SLA Hit Rate @ Top-3)
* **GIVEN**: Hệ thống đã số hóa hoàn thành tài liệu quy định nghỉ phép: *"Quy chế nghỉ phép năm quy định nhân viên được nghỉ tối đa 12 ngày làm việc hưởng nguyên lương"*.
* **WHEN**: Người dùng thực hiện đặt câu hỏi ngôn ngữ tự nhiên không trùng khớp từ khóa chính xác: *"Một năm tôi được nghỉ bao nhiêu ngày phép mà vẫn được trả lương?"*.
* **THEN**: Kết quả tìm kiếm trả về đoạn văn bản quy định nghỉ phép 12 ngày trong Top 3 kết quả đầu tiên.

---

## 9. Ngoài Phạm vi (Out of Scope)
* Không xây dựng mô hình tự sinh câu trả lời (LLM Generation/RAG Chatbot).
* Không tích hợp tìm kiếm lai (Hybrid Search) kết hợp Full-text Search.
* Không hỗ trợ xếp hạng lại kết quả (Re-ranking) bằng mô hình bổ sung.
* Không hỗ trợ nhận dạng ký tự quang học (OCR) đối với định dạng ảnh quét.
* Không lưu trữ lịch sử cuộc hội thoại (Multi-turn Chat).
* Không hỗ trợ lọc nâng cao theo siêu dữ liệu (Metadata filtering) ngoài phạm vi phòng ban/Alias.

---

## 10. Chú giải (Glossary)
* **BOARD**: Ban Giám đốc VCCorp, phòng ban tuyệt mật đặc biệt trong hệ thống.
* **HR (Human Resources)**: Phòng ban Nhân sự.
* **FINANCE**: Phòng ban Tài chính.
* **R&D (Research and Development)**: Phòng ban Nghiên cứu và Phát triển.
* **EAP (Enterprise Archive Platform)**: Nền tảng Lưu trữ Tri thức Doanh nghiệp.
* **SYSTEM_ADMIN**: Tài khoản quản trị toàn bộ hệ thống EAP, bị tước quyền đọc tài liệu.
