package com.inkflow.module.content.service;

import org.springframework.stereotype.Service;

/**
 * Lexorank排序服务
 * 
 * 实现基于字符串比较的排序算法：
 * - 支持在任意两个值之间插入新值
 * - 无需重排序现有记录
 * - 支持无限精度
 * 
 * 算法原理：
 * - 使用字符 '0'-'z' 作为基数（共62个字符）
 * - 计算两个字符串的"中间值"
 * - 当无法计算中间值时，扩展字符串长度
 */
@Service
public class LexorankService {
    
    /**
     * 字符集：0-9, A-Z, a-z
     */
    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    
    /**
     * 基数
     */
    private static final int BASE = CHARS.length();
    
    /**
     * 最小字符
     */
    private static final char MIN_CHAR = CHARS.charAt(0);
    
    /**
     * 最大字符
     */
    private static final char MAX_CHAR = CHARS.charAt(BASE - 1);
    
    /**
     * 中间字符
     */
    private static final char MID_CHAR = CHARS.charAt(BASE / 2);
    
    /**
     * 生成初始rank值
     * 
     * @return 初始rank值 "U" (中间位置)
     */
    public String generateInitialRank() {
        return String.valueOf(MID_CHAR);
    }
    
    /**
     * 生成在指定值之前的rank
     * 
     * @param before 参考值
     * @return 新的rank值
     */
    public String generateRankBefore(String before) {
        if (before == null || before.isEmpty()) {
            return generateInitialRank();
        }
        
        // 在 MIN_CHAR 和 before 之间生成
        return calculateMiddle(String.valueOf(MIN_CHAR), before);
    }
    
    /**
     * 生成在指定值之后的rank
     * 
     * @param after 参考值
     * @return 新的rank值
     */
    public String generateRankAfter(String after) {
        if (after == null || after.isEmpty()) {
            return generateInitialRank();
        }
        
        // 在 after 和 MAX_CHAR 之间生成
        return calculateMiddle(after, String.valueOf(MAX_CHAR));
    }
    
    /**
     * 生成在两个值之间的rank
     * 
     * @param before 前一个值
     * @param after 后一个值
     * @return 中间的rank值
     * @throws IllegalArgumentException 如果 before >= after
     */
    public String generateRankBetween(String before, String after) {
        if (before == null || before.isEmpty()) {
            return generateRankBefore(after);
        }
        if (after == null || after.isEmpty()) {
            return generateRankAfter(before);
        }
        
        if (before.compareTo(after) >= 0) {
            throw new IllegalArgumentException("before必须小于after: before=" + before + ", after=" + after);
        }
        
        return calculateMiddle(before, after);
    }
    
    /**
     * 计算两个字符串的中间值
     * 
     * @param a 较小的字符串
     * @param b 较大的字符串
     * @return 中间值
     */
    public String calculateMiddle(String a, String b) {
        // 确保 a < b
        if (a.compareTo(b) >= 0) {
            throw new IllegalArgumentException("a必须小于b");
        }
        
        // 补齐长度
        int maxLen = Math.max(a.length(), b.length());
        String paddedA = padRight(a, maxLen, MIN_CHAR);
        String paddedB = padRight(b, maxLen, MIN_CHAR);
        
        // 尝试计算中间值
        StringBuilder result = new StringBuilder();
        boolean foundMiddle = false;
        
        for (int i = 0; i < maxLen; i++) {
            int indexA = CHARS.indexOf(paddedA.charAt(i));
            int indexB = CHARS.indexOf(paddedB.charAt(i));
            
            if (indexA == indexB) {
                result.append(paddedA.charAt(i));
            } else if (indexB - indexA > 1) {
                // 可以在这两个字符之间找到中间值
                int midIndex = (indexA + indexB) / 2;
                result.append(CHARS.charAt(midIndex));
                foundMiddle = true;
                break;
            } else {
                // indexB - indexA == 1，需要继续向后看
                result.append(paddedA.charAt(i));
                
                // 在剩余部分找中间值
                String remainingA = paddedA.substring(i + 1);
                String remainingB = paddedB.substring(i + 1);
                
                // 如果 remainingA 全是最大字符，需要扩展
                if (isAllMaxChar(remainingA)) {
                    // 在 a 后面追加中间字符
                    result.append(MID_CHAR);
                } else {
                    // 在 remainingA 和 MAX 之间找中间值
                    String midRemaining = calculateMiddleSimple(remainingA, repeat(MAX_CHAR, remainingA.length()));
                    result.append(midRemaining);
                }
                foundMiddle = true;
                break;
            }
        }
        
        // 如果没有找到中间值，扩展长度
        if (!foundMiddle) {
            result.append(MID_CHAR);
        }
        
        return result.toString();
    }
    
    /**
     * 简化版中间值计算（用于递归）
     */
    private String calculateMiddleSimple(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return String.valueOf(MID_CHAR);
        }
        
        int indexA = CHARS.indexOf(a.charAt(0));
        int indexB = CHARS.indexOf(b.charAt(0));
        
        if (indexB - indexA > 1) {
            return String.valueOf(CHARS.charAt((indexA + indexB) / 2));
        } else if (indexA == indexB) {
            return a.charAt(0) + calculateMiddleSimple(
                a.length() > 1 ? a.substring(1) : "",
                b.length() > 1 ? b.substring(1) : ""
            );
        } else {
            // indexB - indexA == 1
            String remaining = a.length() > 1 ? a.substring(1) : "";
            if (remaining.isEmpty() || isAllMinChar(remaining)) {
                return a.charAt(0) + String.valueOf(MID_CHAR);
            } else {
                return a.charAt(0) + calculateMiddleSimple(remaining, repeat(MAX_CHAR, remaining.length()));
            }
        }
    }
    
    /**
     * 检查字符串是否全是最大字符
     */
    private boolean isAllMaxChar(String s) {
        for (char c : s.toCharArray()) {
            if (c != MAX_CHAR) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 检查字符串是否全是最小字符
     */
    private boolean isAllMinChar(String s) {
        for (char c : s.toCharArray()) {
            if (c != MIN_CHAR) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 右侧填充字符
     */
    private String padRight(String s, int length, char padChar) {
        if (s.length() >= length) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) {
            sb.append(padChar);
        }
        return sb.toString();
    }
    
    /**
     * 重复字符
     */
    private String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
    
    /**
     * 验证rank值是否有效
     * 
     * @param rank rank值
     * @return 是否有效
     */
    public boolean isValidRank(String rank) {
        if (rank == null || rank.isEmpty()) {
            return false;
        }
        for (char c : rank.toCharArray()) {
            if (CHARS.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
