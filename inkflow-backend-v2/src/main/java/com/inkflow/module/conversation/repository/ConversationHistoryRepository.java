package com.inkflow.module.conversation.repository;

import com.inkflow.module.conversation.entity.ConversationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 对话历史 Repository
 *
 * @author zsg
 * @date 2025/12/17
 */
@Repository
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, UUID> {

    /**
     * 按会话ID查询消息，按顺序排序
     */
    List<ConversationHistory> findBySessionIdOrderByMessageOrderAsc(UUID sessionId);

    /**
     * 按会话ID查询最近N条消息
     */
    @Query("SELECT c FROM ConversationHistory c WHERE c.sessionId = :sessionId ORDER BY c.messageOrder DESC")
    List<ConversationHistory> findRecentBySessionId(@Param("sessionId") UUID sessionId, Pageable pageable);

    /**
     * 按项目ID查询所有会话
     */
    @Query("SELECT DISTINCT c.sessionId FROM ConversationHistory c WHERE c.projectId = :projectId ORDER BY MAX(c.createdAt) DESC")
    List<UUID> findSessionIdsByProjectId(@Param("projectId") UUID projectId);

    /**
     * 按用户ID和项目ID查询最近的会话ID
     */
    @Query("SELECT c.sessionId FROM ConversationHistory c WHERE c.userId = :userId AND c.projectId = :projectId ORDER BY c.createdAt DESC LIMIT 1")
    Optional<UUID> findLatestSessionId(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    /**
     * 获取会话的最大消息顺序
     */
    @Query("SELECT COALESCE(MAX(c.messageOrder), 0) FROM ConversationHistory c WHERE c.sessionId = :sessionId")
    Integer findMaxMessageOrder(@Param("sessionId") UUID sessionId);

    /**
     * 按项目ID查询消息
     */
    List<ConversationHistory> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    /**
     * 按用户ID查询消息
     */
    List<ConversationHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * 删除会话的所有消息
     */
    @Modifying
    void deleteBySessionId(UUID sessionId);

    /**
     * 删除项目的所有消息
     */
    @Modifying
    void deleteByProjectId(UUID projectId);

    /**
     * 删除指定时间之前的消息（用于清理）
     */
    @Modifying
    @Query("DELETE FROM ConversationHistory c WHERE c.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    /**
     * 统计会话消息数量
     */
    long countBySessionId(UUID sessionId);

    /**
     * 统计项目消息数量
     */
    long countByProjectId(UUID projectId);

    /**
     * 查询用户最近的会话
     */
    @Query("""
        SELECT c FROM ConversationHistory c 
        WHERE c.userId = :userId 
        AND c.sessionId IN (
            SELECT DISTINCT ch.sessionId FROM ConversationHistory ch 
            WHERE ch.userId = :userId 
            GROUP BY ch.sessionId 
            ORDER BY MAX(ch.createdAt) DESC
        )
        ORDER BY c.createdAt DESC
        """)
    List<ConversationHistory> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);
}
