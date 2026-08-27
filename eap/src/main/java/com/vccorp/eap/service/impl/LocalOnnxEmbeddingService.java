package com.vccorp.eap.service.impl;

import ai.onnxruntime.*;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.vccorp.eap.service.EmbeddingService;
import com.vccorp.eap.common.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Service
public class LocalOnnxEmbeddingService implements EmbeddingService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalOnnxEmbeddingService.class);

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    public LocalOnnxEmbeddingService(
            @Value("${spring.ai.embedding.transformer.onnx.modelUri}") String modelUri,
            @Value("${spring.ai.embedding.transformer.tokenizer.uri}") String tokenizerUri,
            @Value("${eap.upload.dir:./eap-storage}") String storageDir) throws Exception {
        
        // 1. Tạo thư mục models
        File modelsDir = new File(storageDir, "models");
        if (!modelsDir.exists()) modelsDir.mkdirs();

        // 2. Sao chép ONNX Model từ Classpath ra đĩa cứng vật lý
        File modelFile = new File(modelsDir, "model_quantized.onnx");
        if (!modelFile.exists()) {
            Resource modelResource = new ClassPathResource("onnx/model_quantized.onnx");
            try (InputStream is = modelResource.getInputStream()) {
                Files.copy(is, modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 3. Sao chép Tokenizer từ Classpath ra đĩa cứng vật lý
        File tokenizerFile = new File(modelsDir, "tokenizer.json");
        if (!tokenizerFile.exists()) {
            Resource tokenizerResource = new ClassPathResource("onnx/tokenizer.json");
            try (InputStream is = tokenizerResource.getInputStream()) {
                Files.copy(is, tokenizerFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 4. Khởi tạo OrtSession sử dụng mmap và giới hạn threads
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(2); // intra_op_threads <= CPU/2
        opts.setInterOpNumThreads(1);
        opts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
        this.session = env.createSession(modelFile.getAbsolutePath(), opts);

        // 5. Khởi tạo HuggingFace Tokenizer
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath());
    }

    @Override
    public float[] embedText(String text) {
        try {
            var encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] shape = new long[]{1, inputIds.length};

            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);

            try (OrtSession.Result results = session.run(inputs)) {
                OnnxValue val = results.get(0);
                float[] embedding;
                if (val.getValue() instanceof float[][][]) {
                    // Pool CLS Token: shape [1, seq_len, 1024] -> lấy index 0
                    float[][][] output = (float[][][]) val.getValue();
                    embedding = output[0][0];
                } else if (val.getValue() instanceof float[][]) {
                    // Direct pooled output: shape [1, 1024]
                    float[][] output = (float[][]) val.getValue();
                    embedding = output[0];
                } else {
                    throw new IllegalStateException("Định dạng tensor không mong đợi");
                }

                // Kiểm định độ rộng 1024
                if (embedding.length != 1024) {
                    throw new IllegalStateException("Độ dài vector nhúng kết quả là " + embedding.length + ", kỳ vọng 1024.");
                }

                // Chuẩn hóa L2-Normalize cho Cosine distance
                float sumSq = 0;
                for (float v : embedding) sumSq += v * v;
                float norm = (float) Math.sqrt(sumSq);
                if (norm > 0) {
                    for (int i = 0; i < embedding.length; i++) {
                        embedding[i] /= norm;
                    }
                }
                return embedding;
            } finally {
                inputIdsTensor.close();
                attentionMaskTensor.close();
            }
        } catch (OrtException e) {
            log.error("OrtException during embedding generation", e);
            throw new com.vccorp.eap.common.exception.BusinessException(ErrorCode.EMBEDDING_ERROR, "Lỗi ONNX runtime khi tạo vector nhúng: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.error("IllegalStateException during embedding generation", e);
            throw new com.vccorp.eap.common.exception.BusinessException(ErrorCode.EMBEDDING_ERROR, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
            if (tokenizer != null) tokenizer.close();
        } catch (OrtException e) {
            log.warn("Lỗi khi đóng OrtSession/OrtEnvironment", e);
        } catch (Exception e) {
            log.warn("Lỗi không mong muốn khi đóng tài nguyên ONNX", e);
        }
    }
}
