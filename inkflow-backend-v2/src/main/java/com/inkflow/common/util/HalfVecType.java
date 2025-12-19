package com.inkflow.common.util;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;

/**
 * pgvector向量类型的Hibernate自定义类型
 * 
 * 用于将Java的float[]数组映射到PostgreSQL的vector类型
 */
public class HalfVecType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        if (x == y) return true;
        if (x == null || y == null) return false;
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) {
            if (Float.compare(x[i], y[i]) != 0) return false;
        }
        return true;
    }

    @Override
    public int hashCode(float[] x) {
        if (x == null) return 0;
        int result = 1;
        for (float element : x) {
            result = 31 * result + Float.floatToIntBits(element);
        }
        return result;
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, 
            SharedSessionContractImplementor session, Object owner) throws SQLException {
        String value = rs.getString(position);
        if (value == null || rs.wasNull()) {
            return null;
        }
        return parseVector(value);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index,
            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, formatVector(value), Types.OTHER);
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        if (value == null) return null;
        return value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }

    /**
     * 解析PostgreSQL vector字符串为float数组
     * 格式: [0.1,0.2,0.3,...]
     */
    private float[] parseVector(String value) {
        // 移除方括号
        String content = value.substring(1, value.length() - 1);
        if (content.isEmpty()) {
            return new float[0];
        }
        
        String[] parts = content.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    /**
     * 将float数组格式化为PostgreSQL vector字符串
     */
    private String formatVector(float[] value) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < value.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(value[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
