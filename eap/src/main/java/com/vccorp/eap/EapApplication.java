package com.vccorp.eap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
public class EapApplication {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(EapApplication.class, args);
    }

    private static void loadDotEnv() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            // Nếu chạy từ thư mục con (ví dụ: eap/), tìm ở thư mục cha
            envPath = Paths.get("../.env");
        }
        if (Files.exists(envPath)) {
            try {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        // Loại bỏ dấu nháy kép hoặc nháy đơn nếu có
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Không thể đọc file .env: " + e.getMessage());
            }
        }
    }
}

