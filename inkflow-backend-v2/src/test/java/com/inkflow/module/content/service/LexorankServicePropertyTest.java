package com.inkflow.module.content.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lexorank排序服务属性测试
 * 
 * 使用jqwik进行属性测试，验证Lexorank算法的正确性
 */
@PropertyDefaults(tries = 100)
class LexorankServicePropertyTest {
    
    private final LexorankService lexorankService = new LexorankService();
    
    /**
     * Property 1: 中间值排序正确性
     * 
     * 对于任意两个有效的rank值 a < b，
     * 计算的中间值 m 应满足 a < m < b
     */
    @Property
    void middleRankSortsBetweenAdjacentRanks(
        @ForAll("validRanks") String before,
        @ForAll("validRanks") String after
    ) {
        // 确保 before < after
        if (before.compareTo(after) >= 0) {
            String temp = before;
            before = after;
            after = temp;
        }
        
        // 如果相等，跳过
        if (before.equals(after)) {
            return;
        }
        
        String middle = lexorankService.generateRankBetween(before, after);
        
        assertThat(middle.compareTo(before))
            .as("中间值应大于前值: before=%s, middle=%s", before, middle)
            .isGreaterThan(0);
        
        assertThat(middle.compareTo(after))
            .as("中间值应小于后值: middle=%s, after=%s", middle, after)
            .isLessThan(0);
    }
    
    /**
     * Property 2: 连续插入保持顺序
     * 
     * 连续在末尾插入N个元素后，所有元素应保持正确的排序顺序
     */
    @Property
    void consecutiveInsertsPreserveOrder(
        @ForAll @IntRange(min = 2, max = 50) int count
    ) {
        List<String> ranks = new ArrayList<>();
        
        // 生成初始rank
        String current = lexorankService.generateInitialRank();
        ranks.add(current);
        
        // 连续在末尾插入
        for (int i = 1; i < count; i++) {
            current = lexorankService.generateRankAfter(current);
            ranks.add(current);
        }
        
        // 验证所有rank都是递增的
        for (int i = 1; i < ranks.size(); i++) {
            assertThat(ranks.get(i).compareTo(ranks.get(i - 1)))
                .as("rank[%d]=%s 应大于 rank[%d]=%s", i, ranks.get(i), i - 1, ranks.get(i - 1))
                .isGreaterThan(0);
        }
    }
    
    /**
     * Property 3: 随机插入保持顺序
     * 
     * 在随机位置插入元素后，排序后的列表应与原始插入顺序一致
     */
    @Property
    void randomInsertsPreserveOrder(
        @ForAll @IntRange(min = 3, max = 20) int count
    ) {
        List<String> ranks = new ArrayList<>();
        
        // 生成初始rank
        ranks.add(lexorankService.generateInitialRank());
        
        // 随机插入
        for (int i = 1; i < count; i++) {
            int insertIndex = (int) (Math.random() * (ranks.size() + 1));
            
            String newRank;
            if (insertIndex == 0) {
                newRank = lexorankService.generateRankBefore(ranks.get(0));
            } else if (insertIndex == ranks.size()) {
                newRank = lexorankService.generateRankAfter(ranks.get(ranks.size() - 1));
            } else {
                newRank = lexorankService.generateRankBetween(
                    ranks.get(insertIndex - 1),
                    ranks.get(insertIndex)
                );
            }
            
            ranks.add(insertIndex, newRank);
        }
        
        // 验证列表已排序
        List<String> sorted = new ArrayList<>(ranks);
        Collections.sort(sorted);
        
        assertThat(ranks).isEqualTo(sorted);
    }
    
    /**
     * Property 4: 生成的rank值都是有效的
     * 
     * 所有生成的rank值都应该通过有效性验证
     */
    @Property
    void generatedRanksAreValid(
        @ForAll("validRanks") String before,
        @ForAll("validRanks") String after
    ) {
        // 确保 before < after
        if (before.compareTo(after) >= 0) {
            String temp = before;
            before = after;
            after = temp;
        }
        
        if (before.equals(after)) {
            return;
        }
        
        String middle = lexorankService.generateRankBetween(before, after);
        
        assertThat(lexorankService.isValidRank(middle))
            .as("生成的rank应该是有效的: %s", middle)
            .isTrue();
    }
    
    /**
     * Property 5: 初始rank生成一致性
     * 
     * 多次调用generateInitialRank应返回相同的值
     */
    @Property
    void initialRankIsConsistent() {
        String rank1 = lexorankService.generateInitialRank();
        String rank2 = lexorankService.generateInitialRank();
        
        assertThat(rank1).isEqualTo(rank2);
        assertThat(lexorankService.isValidRank(rank1)).isTrue();
    }
    
    /**
     * Property 6: 前插入生成的rank小于参考值
     * 
     * generateRankBefore生成的rank应小于参考值
     * 注意：参考值不能是最小值"0"，否则无法在其前面插入
     */
    @Property
    void rankBeforeIsLessThanReference(
        @ForAll("validRanksNotMin") String reference
    ) {
        String before = lexorankService.generateRankBefore(reference);
        
        assertThat(before.compareTo(reference))
            .as("前插入的rank应小于参考值: before=%s, reference=%s", before, reference)
            .isLessThan(0);
    }
    
    /**
     * Property 7: 后插入生成的rank大于参考值
     * 
     * generateRankAfter生成的rank应大于参考值
     * 注意：参考值不能是最大值"z"，否则无法在其后面插入
     */
    @Property
    void rankAfterIsGreaterThanReference(
        @ForAll("validRanksNotMax") String reference
    ) {
        String after = lexorankService.generateRankAfter(reference);
        
        assertThat(after.compareTo(reference))
            .as("后插入的rank应大于参考值: reference=%s, after=%s", reference, after)
            .isGreaterThan(0);
    }
    
    /**
     * 提供有效的rank值
     */
    @Provide
    Arbitrary<String> validRanks() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        return Arbitraries.strings()
            .withChars(chars.toCharArray())
            .ofMinLength(1)
            .ofMaxLength(5);
    }
    
    /**
     * 提供不以最小字符开头的有效rank值（用于generateRankBefore测试）
     */
    @Provide
    Arbitrary<String> validRanksNotMin() {
        String chars = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        return Arbitraries.strings()
            .withChars(chars.toCharArray())
            .ofMinLength(1)
            .ofMaxLength(5);
    }
    
    /**
     * 提供不以最大字符开头的有效rank值（用于generateRankAfter测试）
     */
    @Provide
    Arbitrary<String> validRanksNotMax() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxy";
        return Arbitraries.strings()
            .withChars(chars.toCharArray())
            .ofMinLength(1)
            .ofMaxLength(5);
    }
}
