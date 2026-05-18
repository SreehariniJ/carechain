package com.carechain.audit;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditMetadata {

    private AuditMetadata() {
    }

    public static Map<String, Object> map(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Audit metadata requires key/value pairs");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object key = keyValues[index];
            Object value = keyValues[index + 1];
            if (key == null || value == null) {
                continue;
            }
            values.put(String.valueOf(key), value);
        }
        return values;
    }

    public static String id(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
