package com.vccorp.eap.service.impl;

import com.vccorp.eap.dto.ChunkDraft;
import com.vccorp.eap.dto.PageContent;
import com.vccorp.eap.service.ParagraphChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class ParagraphChunkerImpl implements ParagraphChunker {

    @Value("${eap.chunking.paragraph-separator:\\\\n\\\\n}")
    private String paragraphSeparator = "\\n\\n";

    private static class HeadingStackTracker {
        private String h1 = "", h2 = "", h3 = "";

        void push(String headingLine) {
            int depth = detectDepth(headingLine);
            switch (depth) {
                case 1 -> { h1 = headingLine.trim(); h2 = ""; h3 = ""; }
                case 2 -> { h2 = headingLine.trim(); h3 = ""; }
                case 3 -> h3 = headingLine.trim();
            }
        }

        String currentContext() {
            return Stream.of(h1, h2, h3)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(" > "));
        }

        private int detectDepth(String line) {
            String t = line.trim();
            if (t.startsWith("### ")) return 3;
            if (t.startsWith("## "))  return 2;
            if (t.startsWith("# "))   return 1;
            if (t.matches("^\\d+\\.\\d+\\.\\d+.*")) return 3;
            if (t.matches("^\\d+\\.\\d+.*"))         return 2;
            if (t.matches("^\\d+\\.?\\s+.*"))        return 1;
            if (t.matches("(?i)^(Chương|Phần)\\s+.*"))  return 1;
            if (t.matches("(?i)^(Điều|Mục)\\s+.*"))     return 2;
            return 1;
        }
    }

    @Override
    public List<ChunkDraft> chunkText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return List.of();
        }

        // 1. Chuẩn hóa sang NFC Unicode
        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFC);

        // 2. Chuyển đổi kí tự ngắt dòng
        String separator = paragraphSeparator
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");

        String regex;
        if (separator.equals("\n\n")) {
            regex = "\n\\s*\n";
        } else {
            regex = Pattern.quote(separator);
        }

        // 3. Tách theo ranh giới đoạn/dòng thô
        String[] rawLines = normalized.split(regex);
        List<String> cleanedLines = new ArrayList<>();
        for (String line : rawLines) {
            // Tách thêm ký tự xuống dòng đơn nếu có
            String[] internalParts = line.split("\n");
            for (String part : internalParts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !isPageNumber(trimmed)) {
                    cleanedLines.add(trimmed);
                }
            }
        }

        // 4. Áp dụng thuật toán gộp dòng theo ngữ nghĩa/heuristics để dựng lại đoạn văn
        List<String> rawParagraphs = new ArrayList<>();
        if (!cleanedLines.isEmpty()) {
            String current = cleanedLines.get(0);
            for (int i = 1; i < cleanedLines.size(); i++) {
                String next = cleanedLines.get(i);
                if (shouldMerge(current, next)) {
                    current = current + " " + next;
                } else {
                    rawParagraphs.add(current);
                    current = next;
                }
            }
            rawParagraphs.add(current);
        }

        // 5. Duy trì Cây Tiêu Đề qua HeadingStackTracker nhưng không đưa tiêu đề vào content chunk
        HeadingStackTracker headingStack = new HeadingStackTracker();
        List<ChunkDraft> result = new ArrayList<>();

        boolean hasAnyHeading = false;
        boolean hasNumberedHeading = false;
        for (String paragraph : rawParagraphs) {
            if (isHeading(paragraph)) {
                hasAnyHeading = true;
                if (isNumberedHeading(paragraph)) {
                    hasNumberedHeading = true;
                    break;
                }
            }
        }

        boolean firstHeadingFound;
        if (hasNumberedHeading) {
            firstHeadingFound = false;
        } else {
            firstHeadingFound = !hasAnyHeading;
        }

        for (String paragraph : rawParagraphs) {
            if (isHeading(paragraph)) {
                headingStack.push(paragraph);
                if (isNumberedHeading(paragraph)) {
                    firstHeadingFound = true;
                } else if (!hasNumberedHeading) {
                    firstHeadingFound = true;
                }
            } else {
                if (firstHeadingFound) {
                    result.add(new ChunkDraft(paragraph, headingStack.currentContext(), 0));
                }
            }
        }

        return result;
    }

    @Override
    public List<ChunkDraft> chunkByPage(List<PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<ChunkDraft> result = new ArrayList<>();
        for (PageContent page : pages) {
            // Phân mảnh nội dung từng trang, sau đó gán lại pageNumber
            List<ChunkDraft> pageChunks = chunkText(page.text());
            for (ChunkDraft draft : pageChunks) {
                result.add(new ChunkDraft(draft.content(), draft.headingContext(), page.pageNumber()));
            }
        }
        return result;
    }

    private boolean isPageNumber(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();

        // 1. Khớp "Trang X", "Trang X/Y", "Trang X trên Y", "Trang X-Y", v.v. (không phân biệt hoa thường)
        if (trimmed.matches("(?i)^trang\\s*\\d+\\s*(?:[/\\-]\\s*\\d+|trên\\s*\\d+)?$")) {
            return true;
        }

        // 2. Khớp "Page X", "Page X/Y", "Page X of Y", "Page X-Y", v.v. (không phân biệt hoa thường)
        if (trimmed.matches("(?i)^page\\s*\\d+\\s*(?:[/\\-]\\s*\\d+|of\\s*\\d+)?$")) {
            return true;
        }

        // 3. Khớp số đơn lẻ hoặc được bao quanh bởi dấu gạch ngang (ví dụ: "1", "12", "- 1 -", "-1-")
        if (trimmed.matches("^\\-?\\s*\\d+\\s*\\-?$")) {
            return true;
        }

        // 4. Khớp số đặt trong ngoặc vuông (ví dụ: "[1]", "[12]")
        if (trimmed.matches("^\\[\\d+\\]$")) {
            return true;
        }

        return false;
    }

    private boolean isHeading(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();

        // 1. Dòng kết thúc bằng dấu hai chấm (thường là tiêu đề danh sách hoặc giới thiệu)
        if (trimmed.endsWith(":") && trimmed.length() < 120) {
            return true;
        }

        // 2. Chữ viết hoa toàn bộ (tiêu đề chương/phần)
        if (isAllUpperCase(trimmed) && trimmed.length() < 120) {
            return true;
        }

        // 3. Tiêu đề dạng số mục (ví dụ: 1.1 Khái niệm, 2. Phương pháp...)
        if (trimmed.matches("^\\d+(\\.\\d+)*\\.?\\s+.*") && trimmed.length() < 120) {
            return true;
        }

        // 4. Tiêu đề dạng Chương/Phần/Điều/Mục
        if (trimmed.matches("(?i)^(Chương|Phần|Điều|Mục)\\s+.*") && trimmed.length() < 120) {
            return true;
        }

        return false;
    }

    private boolean isNumberedHeading(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        // Dạng số mục: 1. 2. 3. 1.1 etc.
        if (trimmed.matches("^\\d+(\\.\\d+)*\\.?\\s+.*") && trimmed.length() < 120) {
            return true;
        }
        // Dạng Chương/Phần/Điều/Mục
        if (trimmed.matches("(?i)^(Chương|Phần|Điều|Mục)\\s+.*") && trimmed.length() < 120) {
            return true;
        }
        return false;
    }

    private boolean shouldMerge(String current, String next) {
        if (current.isEmpty() || next.isEmpty()) {
            return false;
        }

        // Rule 1: If next line starts with bullet point or numbered list, do NOT merge
        char firstCharNext = next.charAt(0);
        if (firstCharNext == '•' || firstCharNext == '-' || firstCharNext == '*' || Character.isDigit(firstCharNext)) {
            if (next.matches("^\\d+(\\.\\d+)*\\.?\\s+.*")) {
                return false;
            }
        }

        // Rule 2: Uppercase heading rules
        boolean currentUpper = isAllUpperCase(current);
        boolean nextUpper = isAllUpperCase(next);
        if (nextUpper) {
            if (currentUpper && current.length() < 100) {
                return true; // Merge consecutive uppercase lines of a heading
            } else {
                return false; // Do not merge normal text with an uppercase heading
            }
        }
        if (currentUpper && current.length() < 100) {
            return false; // Do not merge an uppercase heading with the paragraph below it
        }

        // Rule 3: If current line or next line matches a section number (e.g. "1.1 Khái niệm"), do NOT merge
        if (current.matches("^\\d+(\\.\\d+)*\\.?\\s+.*") && current.length() < 100) {
            return false;
        }
        if (next.matches("^\\d+(\\.\\d+)*\\.?\\s+.*") && next.length() < 100) {
            return false;
        }

        // Rule 4: Sentence boundary checks
        char lastCharCurrent = current.charAt(current.length() - 1);
        boolean endsWithPunctuation = (lastCharCurrent == '.' || lastCharCurrent == '?' || lastCharCurrent == '!' || lastCharCurrent == ':');

        if (endsWithPunctuation) {
            // If current line is short, treat it as a heading or paragraph end
            if (current.length() < 60) {
                return false;
            }
            // If next starts with lowercase, merge anyway (e.g. abbreviations)
            if (Character.isLowerCase(firstCharNext)) {
                return true;
            }
            // Otherwise, do not merge across sentence boundary if it's a new paragraph
            return false;
        }

        return true;
    }

    private boolean isAllUpperCase(String s) {
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (Character.isLowerCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }
}
