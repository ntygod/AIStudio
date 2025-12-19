/**
 * ChatStore 属性测试
 * 
 * **Feature: frontend-api-integration, Property 3: SSE Content Accumulation**
 * **Validates: Requirements 7.2**
 */

import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { useChatStore } from '../chat-store';

describe('ChatStore Property Tests', () => {
  beforeEach(() => {
    // 重置 store 状态
    useChatStore.setState({
      messages: [],
      isStreaming: false,
      currentThoughts: [],
      activeSkills: [],
      agentState: 'idle',
      sessionId: null,
      error: null,
      pendingContent: '',
    });
  });

  /**
   * Property 3: SSE Content Accumulation
   * *For any* sequence of SSE content events received during a chat stream, 
   * the ChatStore SHALL accumulate all content in order, resulting in the 
   * complete message when done event is received.
   * 
   * **Feature: frontend-api-integration, Property 3: SSE Content Accumulation**
   * **Validates: Requirements 7.2**
   */
  it('should accumulate content chunks in order', () => {
    fc.assert(
      fc.property(
        // 生成随机的内容块数组
        fc.array(
          fc.string({ minLength: 1, maxLength: 100 }),
          { minLength: 1, maxLength: 20 }
        ),
        (chunks) => {
          // Arrange: 重置状态
          useChatStore.setState({ pendingContent: '' });

          // Act: 依次追加每个内容块
          chunks.forEach(chunk => {
            useChatStore.getState().appendContent(chunk);
          });

          // Assert: 累积的内容应该等于所有块的连接
          const expectedContent = chunks.join('');
          const actualContent = useChatStore.getState().pendingContent;
          
          expect(actualContent).toBe(expectedContent);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: Content accumulation preserves order
   * *For any* sequence of content chunks, the accumulated content SHALL 
   * preserve the original order of chunks.
   */
  it('should preserve chunk order during accumulation', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.tuple(
            fc.integer({ min: 0, max: 999 }),
            fc.string({ minLength: 1, maxLength: 50 })
          ),
          { minLength: 2, maxLength: 10 }
        ),
        (indexedChunks) => {
          // Arrange: 重置状态
          useChatStore.setState({ pendingContent: '' });

          // 创建带索引的内容块
          const chunks = indexedChunks.map(([index, content]) => `[${index}]${content}`);

          // Act: 依次追加
          chunks.forEach(chunk => {
            useChatStore.getState().appendContent(chunk);
          });

          // Assert: 验证顺序
          const accumulated = useChatStore.getState().pendingContent;
          
          // 检查每个块在累积内容中的位置
          let lastIndex = -1;
          for (const chunk of chunks) {
            const currentIndex = accumulated.indexOf(chunk, lastIndex + 1);
            expect(currentIndex).toBeGreaterThan(lastIndex);
            lastIndex = currentIndex;
          }
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Empty chunks don't affect accumulation
   * *For any* sequence including empty strings, the accumulated content 
   * SHALL equal the concatenation of all chunks (including empty ones).
   */
  it('should handle empty chunks correctly', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.oneof(
            fc.string({ minLength: 0, maxLength: 50 }),
            fc.constant('')
          ),
          { minLength: 1, maxLength: 15 }
        ),
        (chunks) => {
          // Arrange
          useChatStore.setState({ pendingContent: '' });

          // Act
          chunks.forEach(chunk => {
            useChatStore.getState().appendContent(chunk);
          });

          // Assert
          const expectedContent = chunks.join('');
          const actualContent = useChatStore.getState().pendingContent;
          
          expect(actualContent).toBe(expectedContent);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Skill toggle is idempotent for double toggle
   * *For any* skill ID, toggling it twice SHALL return to the original state.
   */
  it('should toggle skills idempotently', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 20 }),
        (skillId) => {
          // Arrange: 重置状态
          useChatStore.setState({ activeSkills: [] });

          // Act: 切换两次
          useChatStore.getState().toggleSkill(skillId);
          const afterFirstToggle = useChatStore.getState().activeSkills.includes(skillId);
          
          useChatStore.getState().toggleSkill(skillId);
          const afterSecondToggle = useChatStore.getState().activeSkills.includes(skillId);

          // Assert: 第一次切换后应该激活，第二次切换后应该取消
          expect(afterFirstToggle).toBe(true);
          expect(afterSecondToggle).toBe(false);
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Clear messages resets all message-related state
   * *For any* state with messages, clearing SHALL result in empty messages array.
   */
  it('should clear all messages and related state', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.record({
            id: fc.string(),
            role: fc.constantFrom('user', 'assistant') as fc.Arbitrary<'user' | 'assistant'>,
            content: fc.string(),
          }),
          { minLength: 1, maxLength: 10 }
        ),
        (messages) => {
          // Arrange: 设置一些消息
          useChatStore.setState({
            messages: messages.map(m => ({
              id: m.id,
              role: m.role,
              content: m.content,
              timestamp: new Date(),
            })),
            pendingContent: 'some pending content',
            sessionId: 'test-session',
          });

          // Act: 清除消息
          useChatStore.getState().clearMessages();

          // Assert: 消息相关状态应该被重置
          const state = useChatStore.getState();
          expect(state.messages).toHaveLength(0);
          expect(state.pendingContent).toBe('');
          expect(state.sessionId).toBeNull();
        }
      ),
      { numRuns: 30 }
    );
  });

  /**
   * Property: Agent state transitions are valid
   * *For any* agent state, setting it SHALL update the state correctly.
   */
  it('should update agent state correctly', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('idle', 'thinking', 'searching', 'generating', 'error'),
        (newState) => {
          // Arrange
          useChatStore.setState({ agentState: 'idle' });

          // Act
          useChatStore.getState().setAgentState(newState as any);

          // Assert
          expect(useChatStore.getState().agentState).toBe(newState);
        }
      ),
      { numRuns: 20 }
    );
  });
});
