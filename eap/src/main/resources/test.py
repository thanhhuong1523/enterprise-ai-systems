from pathlib import Path

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer


# ============================================================
# 1. Paths
# ============================================================

BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "onnx" / "model_quantized.onnx"

# Thư mục model hiện tại chứa tokenizer.json,
# tokenizer_config.json, sentencepiece.bpe.model...
TOKENIZER_PATH = BASE_DIR / "onnx"


# ============================================================
# 2. Load tokenizer
# ============================================================

tokenizer = AutoTokenizer.from_pretrained(
    str(TOKENIZER_PATH),
    local_files_only=True
)


# ============================================================
# 3. Load ONNX model
# ============================================================

session = ort.InferenceSession(
    str(MODEL_PATH),
    providers=["CPUExecutionProvider"]
)

print("Model:", MODEL_PATH)
print("Exists:", MODEL_PATH.exists())

print("\n=== INPUTS ===")
for x in session.get_inputs():
    print(x.name, x.shape, x.type)

print("\n=== OUTPUTS ===")
for x in session.get_outputs():
    print(x.name, x.shape, x.type)


# ============================================================
# 4. Test Vietnamese text
# ============================================================

text = "Tôi đi xe buýt đi làm có được trợ cấp không?"

inputs = tokenizer(
    text,
    return_tensors="np",
    padding=True,
    truncation=True,
    max_length=8192
)

input_ids = inputs["input_ids"].astype(np.int64)
attention_mask = inputs["attention_mask"].astype(np.int64)

print("\n=== TOKENIZED ===")
print("input_ids shape:", input_ids.shape)
print("attention_mask shape:", attention_mask.shape)


# ============================================================
# 5. Run ONNX
# ============================================================

outputs = session.run(
    None,
    {
        "input_ids": input_ids,
        "attention_mask": attention_mask
    }
)

last_hidden_state = outputs[0]

print("\n=== ONNX OUTPUT ===")
print("shape:", last_hidden_state.shape)
print("dtype:", last_hidden_state.dtype)


# ============================================================
# 6. Mean Pooling with Attention Mask
# ============================================================

mask = attention_mask[..., None].astype(np.float32)

masked_embeddings = last_hidden_state * mask

sum_embeddings = np.sum(masked_embeddings, axis=1)

sum_mask = np.sum(mask, axis=1)

sum_mask = np.clip(sum_mask, a_min=1e-9, a_max=None)

sentence_embedding = sum_embeddings / sum_mask


# ============================================================
# 7. L2 Normalize
# ============================================================

norm = np.linalg.norm(
    sentence_embedding,
    axis=1,
    keepdims=True
)

normalized_embedding = sentence_embedding / np.clip(
    norm,
    a_min=1e-12,
    a_max=None
)


# ============================================================
# 8. Print result
# ============================================================

print("\n=== FINAL EMBEDDING ===")
print("shape:", normalized_embedding.shape)
print("dimension:", normalized_embedding.shape[1])
print("norm:", np.linalg.norm(normalized_embedding[0]))

print("\nFirst 10 values:")
print(normalized_embedding[0][:10])