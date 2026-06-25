# Product Requirements Document (PRD)
**Dự án:** VCC Enterprise Archive Platform (VCC-EAP)  
**Tài liệu:** Tài liệu Yêu cầu Sản phẩm - Giai đoạn 1 (Week 1 Baseline)  
**Tác giả:** Trưởng phòng Quản lý Sản phẩm (Senior Product Manager)  

---

## 1. Executive Summary (Tóm tắt Dự án)
Hệ thống **VCC Enterprise Archive Platform (VCC-EAP)** là nền tảng quản trị tri thức nội bộ cho công ty công nghệ VCCorp. Trong giai đoạn khởi tạo (Tuần 1), mục tiêu cốt lõi là thiết lập nền móng quản trị vững chắc cho hệ thống, đảm bảo **an toàn thông tin tuyệt đối cấp phòng ban (0% rò rỉ chéo)**, cung cấp cơ chế **chia sẻ tri thức thông minh (Alias) để tối ưu hóa tài nguyên lưu trữ** và phân quyền chức năng rõ ràng cho nhân sự.

---

## 2. Business Objectives (Mục tiêu Kinh doanh)
*   **An toàn thông tin tối đa**: Triệt tiêu hoàn toàn rủi ro rò rỉ tài liệu nhạy cảm giữa các bộ phận (Nhân sự, Tài chính, R&D, Ban Giám đốc). Tỷ lệ rò rỉ chéo mục tiêu là **0%**.
*   **Tối ưu hóa tài nguyên lưu trữ**: Tiết kiệm dung lượng lưu trữ bằng cách ngăn chặn hành vi sao chép tệp tin trùng lặp khi cần chia sẻ tài liệu giữa các bộ phận, thông qua cơ chế liên kết động (Alias).
*   **Tăng hiệu suất lao động**: Cung cấp công cụ quản lý tập trung giúp nhân viên truy cập tức thì tài liệu của bộ phận mình hoặc tài liệu được chia sẻ mà không phải thực hiện các quy trình thủ công phức tạp.
*   **Minh bạch và Kiểm soát**: Ghi nhận toàn bộ nhật ký thao tác (Audit Log) để Ban Giám đốc có thể giám sát việc truy xuất tài liệu nhạy cảm.

---

## 3. Stakeholders (Các bên liên quan)
*   **Ban Giám đốc VCCorp (BOARD)**: Người đầu tư, phê duyệt ngân sách và là người sử dụng đặc biệt của hệ thống.
*   **Giám đốc Nghiệp vụ Phòng ban (HR, Finance, R&D Managers)**: Người quản lý quy trình, chịu trách nhiệm phê duyệt và quyết định chia sẻ thông tin phòng ban mình sang bộ phận khác.
*   **Nhân viên Nghiệp vụ**: Người sử dụng trực tiếp để tìm kiếm, tra cứu tài liệu hàng ngày.
*   **Đội ngũ Vận hành & IT**: Người quản trị cấu hình nhân sự và giám sát nhật ký an toàn.

---

## 4. User Personas (Hình tượng Người dùng)
*   **Bà Nguyễn Minh Anh (Thành viên Ban Giám đốc)**: Cần xem tài liệu bảo mật của BOARD, đồng thời muốn kiểm tra các tài liệu nghiệp vụ khác mà không gặp rào cản. Yêu cầu tài liệu của BOARD không bao giờ bị rò rỉ hoặc chia sẻ ra ngoài.
*   **Ông Trần Hữu Phước (Trưởng phòng Tài chính)**: Muốn tải lên các quy chế tài chính để nhân viên trong phòng sử dụng ngay. Muốn chia sẻ quy chế này cho phòng Nhân sự tham chiếu qua Alias, nhưng không muốn phòng Nhân sự có quyền sửa đổi file gốc hoặc biết tài liệu này đã được phòng Tài chính chỉnh sửa bao nhiêu lần.
*   **Chị Lê Thùy Linh (Nhân viên phòng Nhân sự)**: Cần xem thông tin quy chế lương (tài liệu của phòng Tài chính chia sẻ). Chị muốn khi mở liên kết này, hệ thống hiển thị tiêu đề dễ hiểu cho phòng Nhân sự và luôn hiển thị thông tin mới nhất nếu phòng Tài chính có cập nhật file gốc.
*   **Anh Vũ Hoàng Nam (Quản trị viên Kỹ thuật)**: Cần tạo tài khoản, phân phòng ban và xem nhật ký thao tác (Audit Log) để phát hiện bất thường, nhưng không được phép đọc nội dung chi tiết của tài liệu để tránh lộ thông tin nội bộ.

---

## 5. User Stories (Kịch bản Người dùng)
*   **US-01**: Là một nhân viên nghiệp vụ, tôi muốn tài liệu của phòng tôi chỉ có người trong phòng tôi xem được để bảo mật dữ liệu nghiệp vụ riêng biệt.
*   **US-02**: Là một nhân viên nghiệp vụ hoạt động (hoặc quản lý) thuộc phòng ban sở hữu tài liệu, tôi muốn tạo một liên kết (Alias) tài liệu của phòng tôi sang phòng ban khác với tiêu đề phù hợp để họ tham chiếu mà không cần tải lại cùng một file.
*   **US-03**: Là nhân viên nhận tài liệu liên kết, tôi muốn luôn nhìn thấy nội dung file mới nhất mỗi khi phòng ban sở hữu tài liệu gốc thực hiện cập nhật.
*   **US-04**: Là thành viên BOARD, tôi muốn toàn bộ tài liệu của BOARD chỉ dành riêng cho thành viên BOARD và không ai có quyền tạo liên kết để chia sẻ ra ngoài phòng BOARD dưới mọi hình thức.
*   **US-05**: Là quản lý phòng ban, tôi muốn khi tôi xóa tài liệu gốc của phòng mình, toàn bộ liên kết Alias đã chia sẻ sang phòng ban khác phải tự động mất hiệu lực ngay lập tức để tránh lộ lọt thông tin cũ.

---

## 6. Functional Requirements (Yêu cầu Chức năng)

| ID | Mô tả Yêu cầu | Mức độ Ưu tiên | Tiêu chí Nghiệm thu |
| :--- | :--- | :--- | :--- |
| **FR-01** | **Quản lý phân tách Phòng ban**<br/>Hệ thống phải hỗ trợ phân nhóm người dùng cố định vào 4 phòng ban: HR, Finance, R&D, BOARD. | **Must Have** | Mỗi người dùng chỉ được gán cho 1 phòng ban duy nhất. Không có tài khoản nào không thuộc phòng ban. |
| **FR-02** | **Phân quyền chức năng theo Vai trò**<br/>Hệ thống kiểm soát các hành động dựa trên vai trò gán cho tài khoản (Roles). | **Must Have** | Vai trò quyết định việc tài khoản được thực hiện hành động nào (Đọc, Ghi, Xóa, Tạo liên kết, Quản trị). |
| **FR-03** | **Quản lý siêu dữ liệu Tài liệu Gốc**<br/>Nhân viên được tải lên tài liệu gốc cho phòng ban mình và sử dụng ngay không cần duyệt. | **Must Have** | * Bản ghi tài liệu gốc lưu đủ tên, mã nghiệp vụ (`ORIG_xxxxxx`), dung lượng, mã băm và ngày tạo.<br/>* Trường `ownerDepartmentId` tự động lấy từ thông tin phòng ban của người dùng đã xác thực, tuyệt đối không nhận từ request payload. |
| **FR-04** | **Quản lý Tài liệu Liên kết (Alias)**<br/>Cho phép tạo liên kết chia sẻ tài liệu gốc sang phòng ban khác mà không sao chép file. | **Must Have** | *   Alias có tiêu đề riêng.<br/>*   Một phòng ban chỉ nhận tối đa 1 Alias từ cùng 1 tài liệu gốc đang hoạt động.<br/>*   Không cho phép tạo Alias nối tiếp (Alias trỏ tới Alias khác).<br/>*   Bất kỳ người dùng hoạt động nào thuộc phòng ban sở hữu tài liệu gốc đều có quyền tạo Alias (Xác thực dựa trên quyền sở hữu phòng ban). |
| **FR-05** | **Giải quyết liên kết tự động**<br/>Khi người dùng được cấp quyền truy cập Alias, hệ thống tự động hiển thị nội dung của Tài liệu Gốc tương ứng. | **Must Have** | Người nhận Alias chỉ có quyền Xem và Tải file, không được chỉnh sửa thông tin gốc hoặc biết số lần chỉnh sửa. |
| **FR-06** | **Tự động thu hồi liên kết khi xóa**<br/>Khi tài liệu gốc bị xóa, toàn bộ liên kết Alias trỏ tới nó phải tự động bị vô hiệu hóa. | **Must Have** | * Truy cập vào Alias sau khi tài liệu gốc bị xóa phải hiển thị thông báo không tìm thấy tài liệu.<br/>* Khi tài liệu gốc được khôi phục, các Alias đã bị xóa trước đó **không** được khôi phục tự động (phải tạo lại thủ công). |
| **FR-07** | **Nhật ký giám sát bất biến (Audit Log)**<br/>Lưu vết đồng bộ toàn bộ hoạt động nghiệp vụ hệ thống. Nhật ký là bất biến (chỉ hỗ trợ INSERT/SELECT, không hỗ trợ UPDATE/DELETE). | **Must Have** | Ghi nhận rõ: Actor, Actor Department, Action, Target Type, Target Identifier, Outcome và Timestamp. |

---

## 7. Non-Functional Requirements (Yêu cầu Phi chức năng)

### 7.1. Security (Bảo mật)
*   **Cô lập dữ liệu tuyệt đối**: Xác suất rò rỉ dữ liệu chéo phòng ban là **0%**.
*   **Cách ly BOARD**: Tài liệu của phòng BOARD là vùng cấm, không bao giờ được phép xuất hiện trong kết quả tìm kiếm của phòng ban khác và không được phép tạo Alias chia sẻ.
*   **Bảo mật quản trị**: Người quản trị hệ thống chỉ được cấu hình nhân sự và xem nhật ký thao tác nghiệp vụ, không có quyền mở/đọc nội dung của bất kỳ tài liệu nào.
*   **Thời gian sống Token ngắn**: Token JWT có thời gian hết hạn ngắn (15 phút). Khi có thay đổi quyền hạn hoặc phòng ban từ Admin, người dùng sẽ đăng nhập lại sau khi token cũ hết hạn để cập nhật ngữ cảnh bảo mật mới.

### 7.2. Reliability (Độ tin cậy)
*   **Bảo tồn dữ liệu xóa**: Cơ chế xóa mềm đảm bảo dữ liệu không bị xóa vĩnh viễn ngay lập tức, cho phép khôi phục khi có sự cố nghiệp vụ.
*   **Tính nhất quán của liên kết**: Không xảy ra tình trạng liên kết Alias bị lỗi mồ côi (trỏ tới tài liệu không tồn tại) mà không có cảnh báo.

### 7.3. Performance (Hiệu năng)
*   **Trải nghiệm xem tài liệu tức thì**: Thời gian kiểm tra và nhận diện tài liệu Gốc hay Liên kết để điều hướng người dùng phải diễn ra tức thời (tiệm cận **0ms** tại tầng API).

### 7.4. Scalability & Maintainability (Mở rộng & Bảo trì)
*   Hệ thống sẵn sàng mở rộng số lượng tài liệu lên hàng triệu bản ghi mà không cần thay đổi cấu trúc quản lý cơ bản.

---

## 8. Business Rules (Quy tắc Nghiệp vụ)
*   **BR-01**: Nhân viên thuộc phòng ban nào chỉ được xem và quản lý tài liệu thuộc sở hữu của phòng ban đó.
*   **BR-02**: Tài liệu thuộc Ban Giám đốc (BOARD) là tài liệu tuyệt mật, chỉ thành viên BOARD mới được phép xem.
*   **BR-03**: Không cho phép tạo liên kết Alias đối với tài liệu thuộc sở hữu của phòng BOARD.
*   **BR-04**: Phòng ban nhận Alias chỉ có quyền Xem và Tải xuống nội dung tài liệu gốc liên kết. Nghiêm cấm các quyền cập nhật, xóa hoặc chia sẻ tiếp sang phòng ban khác.
*   **BR-05**: Mỗi phòng ban chỉ được nhận tối đa 1 Alias từ cùng 1 tài liệu gốc đang hoạt động.
*   **BR-06**: Người quản trị hệ thống (System Admin) bị tước quyền xem trực tiếp nội dung tài liệu.
*   **BR-07**: Quyền tạo Alias thuộc về bất kỳ người dùng hoạt động nào thuộc phòng ban sở hữu tài liệu gốc (ownership-based: `currentUser.departmentId == originalDocument.ownerDepartmentId`). Phòng ban nhận không được phép tự tạo Alias trỏ tới tài liệu của bộ phận khác. Không sử dụng quy trình phê duyệt (approval workflow).
*   **BR-08**: Khi tải lên tài liệu gốc, `ownerDepartmentId` tự động lấy từ thông tin phòng ban của người dùng đã xác thực, tuyệt đối không được nhận từ API requests.
*   **BR-09 (Vòng đời người dùng)**: Khi người dùng bị xóa, toàn bộ tài liệu gốc và Alias do người dùng đó tạo vẫn giữ nguyên hiệu lực và phòng ban sở hữu không thay đổi. Khi người dùng chuyển phòng ban, quyền sở hữu của các tài liệu cũ không bị chuyển tự động.
*   **BR-10 (Cấm Alias nối tiếp)**: Alias chỉ được phép tham chiếu trực tiếp đến tài liệu gốc (Original Document), cấm tuyệt đối việc tạo Alias tham chiếu tới một Alias khác.
*   **BR-11 (Chiến lược phiên bản Phase 1)**: Cập nhật metadata hoặc cập nhật file vật lý sẽ ghi đè trực tiếp lên giá trị cũ. Không lưu giữ các phiên bản file vật lý lịch sử. Các Alias liên quan sẽ tự động tham chiếu đến trạng thái tài liệu gốc hoạt động mới nhất.
*   **BR-12 (Ghi nhật ký Audit)**: Nhật ký Audit là bất biến và chỉ hỗ trợ ghi mới (INSERT) và đọc (SELECT). Cấm mọi API chỉnh sửa hay xóa Audit log.
*   **BR-13 (Giới hạn Tải lên)**: Chỉ chấp nhận các tài liệu có định dạng PDF, DOCX, XLSX, PPTX và dung lượng tối đa 50MB. Hệ thống thực hiện kiểm tra tính hợp lệ qua phần mở rộng tệp và MIME type của file.
*   **BR-14 (Phòng thủ DoS - Phase 2)**: Các cơ chế giới hạn tần suất request (Rate Limiting) tạm thời chưa triển khai cho Week 1 và sẽ được đưa vào phạm vi Phase 2 nếu hệ thống cần công khai trên Internet.

---

## 9. Assumptions (Giả định)
*   Người dùng đăng nhập vào hệ thống luôn được xác định rõ danh tính và phòng ban trực thuộc trước khi thực hiện bất kỳ thao tác nào thông qua JWT token.
*   Việc tạo liên kết Alias chia sẻ tài liệu chéo phòng ban đã được sự đồng ý của Quản lý phòng ban sở hữu tài liệu gốc.

---

## 10. Constraints (Ràng buộc)
*   **Công nghệ**: Java 17, Spring Boot 3.5, PostgreSQL 17, Flyway, Maven.
*   **Hạ tầng**: Hệ thống chỉ chạy 1 instance Spring Boot và kết nối với duy nhất 1 database PostgreSQL.

---

## 11. Scope (Phạm vi bàn giao Tuần 1)
*   Mã nguồn hệ thống Java hoàn thiện chạy được các chức năng cốt lõi: Khởi tạo phòng ban, người dùng, đăng ký tài liệu gốc, tạo và giải quyết liên kết Alias, lưu vết Audit Log đồng bộ.
*   Chỉ bao gồm 3 tài liệu chính: [PRD.md](file:///Users/phantom/.gemini/antigravity/brain/98d68816-1144-4a17-926d-fda48c2eade0/PRD.md), [ArchitectureDesign.md](file:///Users/phantom/.gemini/antigravity/brain/98d68816-1144-4a17-926d-fda48c2eade0/ArchitectureDesign.md), và [DetailedDesign.md](file:///Users/phantom/.gemini/antigravity/brain/98d68816-1144-4a17-926d-fda48c2eade0/DetailedDesign.md).
