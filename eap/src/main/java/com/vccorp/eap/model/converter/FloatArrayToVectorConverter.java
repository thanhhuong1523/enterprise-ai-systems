package com.vccorp.eap.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

@Converter(autoApply = true)
public class FloatArrayToVectorConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        return Arrays.toString(attribute);
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        String value = dbData.trim();
        if (value.isEmpty()) {
            return new float[0];
        }

        // Remove '[' and ']' if present
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }

        if (value.trim().isEmpty()) {
            return new float[0];
        }

        String[] parts = value.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
