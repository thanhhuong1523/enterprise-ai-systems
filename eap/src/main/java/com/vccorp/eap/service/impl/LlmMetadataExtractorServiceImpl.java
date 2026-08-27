package com.vccorp.eap.service.impl;

import com.vccorp.eap.service.LlmMetadataExtractorService;
import com.vccorp.eap.service.helper.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LlmMetadataExtractorServiceImpl implements LlmMetadataExtractorService {

    private final LlmClient llmClient;
    private final long queryTimeoutMs;
    private final long chunkTimeoutMs;

    public LlmMetadataExtractorServiceImpl(
            LlmClient llmClient,
            @Value("${eap.llm.query-timeout-ms:5000}") long queryTimeoutMs,
            @Value("${eap.llm.chunk-timeout-ms:40000}") long chunkTimeoutMs) {
        this.llmClient = llmClient;
        this.queryTimeoutMs = queryTimeoutMs;
        this.chunkTimeoutMs = chunkTimeoutMs;
    }

    @Override
    public Map<String, Object> extractMetadata(String query) {
        Map<String, Object> raw = llmClient.callLlm(query, getQueryPrompt(), queryTimeoutMs);
        Map<String, Object> metadata = (raw == null) ? new HashMap<>() : new HashMap<>(raw);
        return normalizeMetadata(metadata, true);
    }

    @Override
    public Map<String, Object> extractChunkMetadata(String chunkContent) {
        return extractChunkMetadata(chunkContent, "");
    }

    @Override
    public Map<String, Object> extractChunkMetadata(String chunkContent, String headingContext) {
        // Tác vụ nền số hóa cho phép timeout dài hơn để đảm bảo chất lượng trích xuất
        Map<String, Object> raw = llmClient.callLlm(chunkContent, getChunkPrompt(headingContext), chunkTimeoutMs);
        Map<String, Object> metadata = (raw == null) ? new HashMap<>() : new HashMap<>(raw);
        return normalizeMetadata(metadata, false);
    }

    private Map<String, Object> normalizeMetadata(Map<String, Object> rawMetadata, boolean isQueryFilter) {
        if (rawMetadata == null || rawMetadata.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawMetadata.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val == null) {
                continue;
            }
            if ("citation_headings".equalsIgnoreCase(key)) {
                normalized.put(key, val);
                continue;
            }
            if (isQueryFilter && "keywords".equalsIgnoreCase(key)) {
                continue;
            }
            if (val instanceof String sVal) {
                String lowerVal = sVal.trim().toLowerCase(Locale.ROOT);
                if (lowerVal.isBlank()) {
                    continue;
                }
                if (isQueryFilter && "doc_type".equalsIgnoreCase(key) && ("other".equals(lowerVal) || "none".equals(lowerVal) || "unknown".equals(lowerVal) || "null".equals(lowerVal))) {
                    continue;
                }
                normalized.put(key, lowerVal);
            } else if (val instanceof List<?> listVal) {
                List<String> normalizedList = new ArrayList<>();
                for (Object item : listVal) {
                    if (item != null) {
                        String sItem = item.toString().trim().toLowerCase(Locale.ROOT);
                        if (!sItem.isBlank()) {
                            if (isQueryFilter && "topics".equalsIgnoreCase(key) && ("general_info".equals(sItem) || "other".equals(sItem) || "none".equals(sItem) || "unknown".equals(sItem))) {
                                continue;
                            }
                            normalizedList.add(sItem);
                        }
                    }
                }
                if (!normalizedList.isEmpty()) {
                    normalized.put(key, normalizedList);
                }
            } else {
                normalized.put(key, val);
            }
        }
        return normalized;
    }

    private String getQueryPrompt() {
        return """
               Bạn là bộ phân tích Intent câu hỏi cho hệ thống RAG doanh nghiệp. Nhiệm vụ: Trích xuất chính xác JSON Metadata Filter từ câu hỏi người dùng để thực hiện tiền lọc (metadata pre-filtering) cơ sở dữ liệu.
               Trả về DUY NHẤT một object JSON theo đúng schema bên dưới. Không giải thích, không markdown, không backtick, không thêm bất kỳ văn bản nào khác ngoài JSON.

               ## SCHEMA

               {
                 "doc_type": "string enum (lowercase, để null nếu không xác định rõ)",
                 "topics": ["string enum (lowercase)", ...],
                 "entities": ["type:value (lowercase)", ...],
                 "time_refs": ["string (lowercase)", ...]
               }

               ## QUY TẮC TRÍCH XUẤT TỪNG TRƯỜNG (TẤT CẢ VALUE PHẢI LÀ CHỮ THƯỜNG / LOWERCASE, NGẮN GỌN SÚC TÍCH 1-3 TỪ)

               ### doc_type (CHỈ trích xuất khi câu hỏi nêu rõ hoặc thể hiện mục đích loại tài liệu cụ thể. Nếu không rõ, ĐỂ NULL hoặc KHÔNG TRẢ VỀ)
               - guide: quy trình, thủ tục, các bước thực hiện, hướng dẫn thao tác, xin cấp/xin nghỉ, cách thức đăng ký/thực hiện/nộp đơn
               - regulation: quy định, chính sách, điều khoản, nội quy, quy chế, tiêu chuẩn, điều kiện, chế độ, mức phụ cấp/lương thưởng/trợ cấp/phạt
               - analysis: phân tích, đánh giá, báo cáo nghiên cứu, so sánh chuyên sâu
               - transaction: hợp đồng, hóa đơn, biên bản giao dịch, chứng từ, đơn hàng
               - communication: thông báo nội bộ, email, thư từ, thông điệp truyền thông
               - education: tài liệu giảng dạy, giáo trình, bài giảng, tài liệu đào tạo
               - news: tin tức, thông cáo báo chí, cập nhật sự kiện
               - literature: văn hóa doanh nghiệp, phong tục, tập quán, lễ hội, văn học, giải trí, địa danh, di tích
               - Nếu không thuộc các loại trên hoặc không thể hiện rõ loại tài liệu → ĐỂ NULL hoặc ĐỂ RỖNG (không tự ép về "other").

               ### topics (CHỈ trích xuất khi câu hỏi đề cập rõ chủ đề chuyên môn cụ thể. Nếu không rõ chủ đề, ĐỂ MẢNG RỖNG [])
               - hr_policy: chính sách nhân sự, tuyển dụng, nghỉ phép, nghỉ thai sản, đi làm muộn, bảo hiểm, hợp đồng lao động
               - compensation_benefits: lương thưởng, phụ cấp (ăn trưa, gửi xe, đi lại, công tác), phúc lợi, trợ cấp
               - finance_accounting: tài chính, kế toán, thuế, tạm ứng, thanh toán, ngân sách, vay vốn
               - legal_compliance: pháp lý, tuân thủ, kiểm soát nội bộ, bản quyền, hợp đồng
               - it_technical: công nghệ thông tin, phần mềm, hạ tầng, phân quyền, bảo mật, hệ thống, máy tính
               - sales_marketing: kinh doanh, bán hàng, tiếp thị, truyền thông, doanh số
               - operation_process: quy trình vận hành, quản lý công việc, vận hành văn phòng
               - admin_facilities: hành chính, quản lý tài sản, trang thiết bị, phòng họp, văn phòng phẩm
               - board_direction: chỉ đạo, nghị quyết, quyết định từ Ban Giám Đốc (BOARD)
               - general_info: thông tin chung khác (văn hóa, đời sống, địa danh, khái niệm tổng quát)

               ### entities (trích xuất kĩ lưỡng các thực thể/địa danh/khái niệm khi xuất hiện trong câu hỏi, dạng "type:value" chữ thường, NGẮN GỌN SÚC TÍCH 1-3 TỪ)
               - Mọi giá trị thực thể/khái niệm (`type:value`) phải cực kỳ NGẮN GỌN, SÚC TÍCH (chỉ 1-3 từ cốt lõi nhất), loại bỏ hoàn toàn các từ nghi vấn hay từ nối rườm rà.
               - Phân loại các type:
                 - org: tên/loại tổ chức (org:vccorp, org:ngân hàng)
                 - dept: tên phòng ban (dept:phòng nhân sự, dept:phòng kế toán)
                 - person: tên người, chức danh, đối tượng (person:nguyễn văn a, person:giám đốc, person:thực tập sinh)
                 - product: tên sản phẩm (product:lotus, product:thẻ tín dụng)
                 - law: tên văn bản pháp luật (law:thông tư 39, law:nội quy lao động)
                 - standard: tiêu chuẩn (standard:iso 27001)
                 - tech: công nghệ, phần mềm (tech:kubernetes, tech:docker)
                 - loc: địa danh, danh thắng (loc:hà nội, loc:bái đính, loc:phát diệm, loc:ninh bình)
                 - concept: khái niệm, đề mục, chế độ, quyền lợi ngắn gọn (concept:danh lam thắng cảnh, concept:di tích lịch sử, concept:thai sản, concept:ăn trưa, concept:gửi xe)

               ### time_refs (mốc thời gian cụ thể dạng YYYY, YYYY-QN, YYYY-MM HOẶC thời gian tương đối như "2 năm", "đầu năm", "cuối quý", chữ thường ngắn gọn. Nếu không có để [])

               ## QUY TẮC ĐẦU RA
               TẤT CẢ CÁC VALUE PHẢI VIẾT BẰNG CHỮ THƯỜNG (LOWERCASE), NGẮN GỌN SÚC TÍCH (1-3 TỪ). NẾU KHÔNG CÓ THÔNG TIN THÌ ĐỂ RỖNG/NULL.

               ## VÍ DỤ OUTPUT
               Q: "Quy trình xin nghỉ thai sản năm 2026 của phòng Nhân sự"
               → {"doc_type":"guide","topics":["hr_policy"],"entities":["dept:phòng nhân sự","concept:thai sản"],"time_refs":["2026"]}

               Q: "Thực tập sinh có được hỗ trợ tiền ăn trưa và gửi xe không?"
               → {"doc_type":"regulation","topics":["compensation_benefits"],"entities":["person:thực tập sinh","concept:ăn trưa","concept:gửi xe"],"time_refs":[]}

               Q: "Thông tin danh lam thắng cảnh Bái Đính Ninh Bình"
               → {"doc_type":null,"topics":[],"entities":["loc:bái đính","loc:ninh bình","concept:danh lam thắng cảnh"],"time_refs":[]}

               Q: "Hỏi về các chính sách chung"
               → {"doc_type":null,"topics":[],"entities":[],"time_refs":[]}
               """;
    }

    private String getChunkPrompt(String headingContext) {
        String headingContextStr = (headingContext == null || headingContext.isBlank()) 
                ? "BỐI CẢNH TIÊU ĐỀ (Citation Headings Stack): (Không có tiêu đề)\n" 
                : "BỐI CẢNH TIÊU ĐỀ (Citation Headings Stack): " + headingContext + "\n";
        return headingContextStr + """
               Bạn là chuyên gia trích xuất metadata chuyên sâu cho hệ thống RAG doanh nghiệp số hóa tài liệu.
               
               YÊU CẦU NGUYÊN TẮC TỐI CAO:
               1. BẮT BUỘC TRÍCH XUẤT VÀ SUY LUẬN TỪ NGỮ THẬT ĐA DẠNG, PHONG PHÚ VÀO METADATA `entities`.
                  - Trích xuất cả CỤM TỪ DÀI gốc (ví dụ: "ẩm thực đặc trưng", "danh lam thắng cảnh", "nghỉ phép thai sản").
                  - Trích xuất cả CÁC TỪ TÁCH NÓI / TỪ ĐƠN NGẮN cấu thành (ví dụ: "ẩm thực", "đặc trưng", "đồ ăn", "món ăn", "ăn trưa", "phụ cấp").
                  - BẮT BUỘC suy luận thêm CÁC TỪ ĐỒNG NGHĨA VÀ TỪ LIÊN QUAN TRỰC TIẾP (ví dụ: "ẩm thực đặc trưng" → trích xuất cả `concept:ẩm thực đặc trưng`, `concept:ẩm thực`, `concept:đặc trưng`, `concept:đồ ăn`, `concept:món ăn`, `concept:đặc sản`, `concept:văn hóa ẩm thực`).
               2. TRÍCH XUẤT ĐẦY ĐỦ TỪ ĐỀ MỤC PHÂN CẤP (I, II, 1, 1.1, 1.1.1...): Đọc kỹ BỐI CẢNH TIÊU ĐỀ (Citation Headings Stack ở dòng đầu tiên) để trích xuất toàn bộ các chủ đề, tên chương/mục, nội dung cấp cao vào `entities`.
               3. TRÍCH XUẤT TỐI ĐA (10 đến 20+ TỪ KHÓA / CHỦ ĐỀ PER CHUNK): Không ngần ngại trả về danh sách phong phú gồm thực thể, địa danh, khái niệm ngắn, cụm từ dài và từ đồng nghĩa.
               4. TRÍCH XUẤT ĐỊA DANH NGẮN GỌN (`loc:value`): Các địa danh, danh thắng (ví dụ: bái đính, phát diệm, ninh bình, hà nội) trích dạng `loc:bái đính`, `loc:phát diệm`, `loc:ninh bình`.

               Trả về DUY NHẤT một object JSON theo đúng schema bên dưới. Không giải thích, không markdown, không backtick, không thêm văn bản nào khác ngoài JSON.

               ## SCHEMA

               {
                 "doc_type": "string enum (lowercase)",
                 "topics": ["string enum (lowercase)", ...],
                 "entities": ["type:value (lowercase)", ...],
                 "time_refs": ["string (lowercase)", ...]
               }

               ## QUY TẮC TRÍCH XUẤT CHI TIẾT (TẤT CẢ VALUE LÀ CHỮ THƯỜNG / LOWERCASE)

               ### 1. doc_type (BẮT BUỘC, chọn 1 Enum chữ thường phù hợp nhất dựa trên CẢ tiêu đề và thân bài):
               - guide: hướng dẫn, quy trình thao tác, các bước thực hiện, nộp đơn/đăng ký
               - regulation: quy định, chính sách, điều khoản pháp lý, quy chế, tiêu chuẩn, điều kiện, mức phụ cấp/lương thưởng/trợ cấp/phạt
               - analysis: phân tích, đánh giá, báo cáo nghiên cứu, so sánh chuyên sâu
               - transaction: hóa đơn, hợp đồng, biên bản giao dịch, chứng từ, đơn hàng
               - communication: email, thư từ, chat, thông báo nội bộ
               - education: tài liệu giảng dạy, giáo trình, bài giảng, tài liệu đào tạo
               - news: tin tức, thông cáo báo chí, cập nhật sự kiện
               - literature: văn hóa, ẩm thực, phong tục, tập quán, lễ hội, văn học truyền thống, lịch sử, danh lam thắng cảnh, di tích
               - other: thông tin tổng quát khác hoặc không thuộc các loại trên

               ### 2. topics (BẮT BUỘC, chọn 1-3 Enum chữ thường từ CẢ tiêu đề và thân bài. Nếu không rõ chủ đề chuyên môn, chọn ["general_info"]):
               - hr_policy: chính sách nhân sự, tuyển dụng, nghỉ phép, nghỉ thai sản, bảo hiểm, hợp đồng lao động
               - compensation_benefits: lương thưởng, phụ cấp (ăn trưa, gửi xe, đi lại, công tác), phúc lợi, trợ cấp
               - finance_accounting: tài chính, kế toán, thuế, tạm ứng, thanh toán, ngân sách, vay vốn
               - legal_compliance: pháp lý, tuân thủ, kiểm soát nội bộ, bản quyền
               - it_technical: công nghệ thông tin, hạ tầng, phân quyền, bảo mật, phần mềm, máy tính
               - sales_marketing: kinh doanh, truyền thông, tiếp thị, bán hàng
               - operation_process: quy trình vận hành, quản lý công việc, vận hành văn phòng
               - admin_facilities: hành chính, quản lý tài sản, trang thiết bị, phòng họp
               - board_direction: chỉ đạo, nghị quyết từ Ban Giám Đốc (BOARD)
               - general_info: thông tin chung khác (văn hóa, ẩm thực, lịch sử, đời sống, du lịch, địa danh, khái niệm tổng quát)

               ### 3. entities (BẮT BUỘC TRÍCH XUẤT VÀ SUY LUẬN TỪ NGỮ ĐA DẠNG PHONG PHÚ):
               - Dạng "type:value" chữ thường.
               - Quy tắc trích xuất linh hoạt:
                 - Cụm từ dài gốc: `concept:ẩm thực đặc trưng`, `concept:danh lam thắng cảnh`, `concept:phụ cấp ăn trưa`.
                 - Từ đơn ngắn tách nhỏ: `concept:ẩm thực`, `concept:đặc trưng`, `concept:ăn trưa`, `concept:phụ cấp`.
                 - Từ đồng nghĩa & từ liên quan: `concept:đồ ăn`, `concept:món ăn`, `concept:đặc sản`, `concept:trợ cấp ăn`, `concept:tiền ăn`.
               - Phân loại các type:
                 - org: tên/loại tổ chức, công ty, triều đại (vd: org:vccorp, org:nhà đinh, org:nhà lê)
                 - dept: tên phòng ban, khối (vd: dept:phòng nhân sự, dept:phòng kế toán)
                 - person: đối tượng, chức danh, nhân vật lịch sử, tên người (vd: person:thực tập sinh, person:nhân viên, person:đinh tiên hoàng)
                 - product: tên sản phẩm, ứng dụng (vd: product:lotus, product:thẻ tín dụng)
                 - law: tên văn bản, quy định, nội quy (vd: law:thông tư 39, law:nội quy lao động)
                 - standard: tiêu chuẩn, chứng chỉ (vd: standard:iso 27001)
                 - tech: công nghệ, phần mềm, hạ tầng (vd: tech:kubernetes, tech:docker)
                 - loc: địa danh, địa điểm, danh thắng ngắn gọn (vd: loc:bái đính, loc:phát diệm, loc:ninh bình, loc:hà nội, loc:tràng an)
                 - concept: khái niệm, từ gốc dài, từ đơn ngắn, từ đồng nghĩa & từ liên quan (vd: concept:ẩm thực đặc trưng, concept:ẩm thực, concept:đặc trưng, concept:đồ ăn, concept:món ăn, concept:đặc sản, concept:danh lam thắng cảnh, concept:danh thắng, concept:di tích lịch sử, concept:du lịch tâm linh, concept:chùa bái đính, concept:phụ cấp ăn trưa, concept:ăn trưa, concept:phụ cấp, concept:trợ cấp ăn)

               ### 4. time_refs (Mốc thời gian trích xuất từ CẢ tiêu đề và thân bài, chữ thường, ngắn gọn):
               - Ưu tiên mốc thời gian cụ thể (yyyy, yyyy-qn, yyyy-mm). Thời gian tương đối ("2 năm", "đầu năm", "cuối quý", "thế kỷ 10", "thời phong kiến").

               ## QUY TẮC ĐẦU RA
               Trả về đầy đủ các key trong SCHEMA (doc_type, topics, entities, time_refs). TẤT CẢ VALUE LÀ CHỮ THƯỜNG (LOWERCASE).

               ## VÍ DỤ OUTPUT 1
               Bối cảnh tiêu đề: "Chương II: Văn hóa & Vùng miền > Mục 1.2: Ẩm thực đặc trưng tỉnh Ninh Bình"
               Nội dung chunk: "Cơm cháy và thịt dê núi là hai món ăn đặc sản nổi tiếng đại diện cho nét ẩm thực đặc trưng tại Ninh Bình."
               → {
                 "doc_type": "literature",
                 "topics": ["general_info"],
                 "entities": [
                   "concept:ẩm thực đặc trưng",
                   "concept:ẩm thực",
                   "concept:đặc trưng",
                   "concept:đồ ăn",
                   "concept:món ăn",
                   "concept:đặc sản",
                   "concept:cơm cháy",
                   "concept:thịt dê núi",
                   "concept:thịt dê",
                   "concept:văn hóa ẩm thực",
                   "concept:vùng miền",
                   "loc:ninh bình"
                 ],
                 "time_refs": []
               }

               ## VÍ DỤ OUTPUT 2
               Bối cảnh tiêu đề: "Mục I: Chế độ phúc lợi > Điều 2: Quy định phụ cấp ăn trưa và gửi xe năm 2026 > 2.1: Thực tập sinh"
               Nội dung chunk: "Hỗ trợ 100% tiền gửi xe và phụ cấp ăn trưa 500,000 VND mỗi tháng cho sinh viên thực tập."
               → {
                 "doc_type": "regulation",
                 "topics": ["compensation_benefits"],
                 "entities": [
                   "person:thực tập sinh",
                   "person:sinh viên thực tập",
                   "concept:phụ cấp ăn trưa",
                   "concept:phụ cấp",
                   "concept:ăn trưa",
                   "concept:gửi xe",
                   "concept:trợ cấp ăn",
                   "concept:tiền ăn",
                   "concept:đồ ăn",
                   "concept:chế độ phúc lợi",
                   "concept:hỗ trợ tiền ăn",
                   "concept:gửi xe miễn phí"
                 ],
                 "time_refs": ["2026"]
               }
               """;
    }
}
