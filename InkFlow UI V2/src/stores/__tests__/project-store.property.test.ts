/**
 * ProjectStore 属性测试
 * 
 * **Feature: frontend-api-integration, Property 4: Store Cache Timestamp**
 * **Validates: Requirements 9.2**
 */

import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { useProjectStore } from '../project-store';

// 缓存有效期（5分钟）
const CACHE_TTL = 5 * 60 * 1000;

describe('ProjectStore Property Tests', () => {
  beforeEach(() => {
    // 重置 store 状态
    useProjectStore.setState({
      projects: [],
      currentProject: null,
      isLoading: false,
      error: null,
      pagination: {
        page: 0,
        size: 20,
        total: 0,
        totalPages: 0,
      },
      projectCache: new Map(),
      listCacheTimestamp: null,
    });
  });

  /**
   * Property 4: Store Cache Timestamp
   * *For any* successful API data fetch, the corresponding store SHALL record 
   * a timestamp, and subsequent fetches within the cache validity period 
   * SHALL return cached data without API call.
   * 
   * **Feature: frontend-api-integration, Property 4: Store Cache Timestamp**
   * **Validates: Requirements 9.2**
   */
  it('should validate cache based on timestamp', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: CACHE_TTL * 2 }),
        (elapsedTime) => {
          // Arrange: 设置缓存时间戳
          const cacheTimestamp = Date.now() - elapsedTime;
          
          // Act: 检查缓存是否有效
          const isValid = useProjectStore.getState().isCacheValid(cacheTimestamp);

          // Assert: 如果经过时间小于 TTL，缓存应该有效
          if (elapsedTime < CACHE_TTL) {
            expect(isValid).toBe(true);
          } else {
            expect(isValid).toBe(false);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: Null timestamp means invalid cache
   * *For any* null timestamp, the cache SHALL be considered invalid.
   */
  it('should treat null timestamp as invalid cache', () => {
    // Act & Assert
    const isValid = useProjectStore.getState().isCacheValid(null);
    expect(isValid).toBe(false);
  });

  /**
   * Property: Cache invalidation clears all timestamps
   * *For any* state with cache data, invalidating cache SHALL clear all timestamps.
   */
  it('should clear all cache timestamps on invalidation', () => {
    fc.assert(
      fc.property(
        fc.record({
          listTimestamp: fc.integer({ min: 1, max: Date.now() }),
          projectIds: fc.array(fc.uuid(), { minLength: 1, maxLength: 5 }),
        }),
        ({ listTimestamp, projectIds }) => {
          // Arrange: 设置缓存
          const projectCache = new Map();
          projectIds.forEach(id => {
            projectCache.set(id, {
              data: { id, title: 'Test' },
              timestamp: Date.now(),
            });
          });

          useProjectStore.setState({
            listCacheTimestamp: listTimestamp,
            projectCache,
          });

          // Act: 使缓存失效
          useProjectStore.getState().invalidateCache();

          // Assert: 所有缓存应该被清除
          const state = useProjectStore.getState();
          expect(state.listCacheTimestamp).toBeNull();
          expect(state.projectCache.size).toBe(0);
        }
      ),
      { numRuns: 30 }
    );
  });

  /**
   * Property: Setting current project updates state correctly
   * *For any* project, setting it as current SHALL update currentProject.
   */
  it('should set current project correctly', () => {
    fc.assert(
      fc.property(
        fc.record({
          id: fc.uuid(),
          title: fc.string({ minLength: 1, maxLength: 100 }),
          userId: fc.uuid(),
          status: fc.constantFrom('ACTIVE', 'ARCHIVED', 'DELETED'),
          creationPhase: fc.constantFrom('IDEA', 'WORLDBUILDING', 'CHARACTER', 'OUTLINE', 'WRITING', 'REVISION', 'COMPLETED'),
          createdAt: fc.date().map(d => d.toISOString()),
          updatedAt: fc.date().map(d => d.toISOString()),
        }),
        (project) => {
          // Act
          useProjectStore.getState().setCurrentProject(project as any);

          // Assert
          const currentProject = useProjectStore.getState().currentProject;
          expect(currentProject).toEqual(project);
        }
      ),
      { numRuns: 30 }
    );
  });

  /**
   * Property: Setting null clears current project
   * *For any* state with current project, setting null SHALL clear it.
   */
  it('should clear current project when set to null', () => {
    fc.assert(
      fc.property(
        fc.record({
          id: fc.uuid(),
          title: fc.string({ minLength: 1 }),
        }),
        (project) => {
          // Arrange: 设置当前项目
          useProjectStore.setState({ currentProject: project as any });

          // Act: 设置为 null
          useProjectStore.getState().setCurrentProject(null);

          // Assert
          expect(useProjectStore.getState().currentProject).toBeNull();
        }
      ),
      { numRuns: 20 }
    );
  });

  /**
   * Property: Error clearing resets error state
   * *For any* error message, clearing error SHALL set it to null.
   */
  it('should clear error correctly', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 200 }),
        (errorMessage) => {
          // Arrange: 设置错误
          useProjectStore.setState({ error: errorMessage });

          // Act: 清除错误
          useProjectStore.getState().clearError();

          // Assert
          expect(useProjectStore.getState().error).toBeNull();
        }
      ),
      { numRuns: 20 }
    );
  });

  /**
   * Property: Cache validity is deterministic
   * *For any* timestamp, calling isCacheValid multiple times SHALL return same result.
   */
  it('should be deterministic for cache validity check', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: Date.now() }),
        (timestamp) => {
          // Act: 多次检查
          const result1 = useProjectStore.getState().isCacheValid(timestamp);
          const result2 = useProjectStore.getState().isCacheValid(timestamp);
          const result3 = useProjectStore.getState().isCacheValid(timestamp);

          // Assert: 结果应该一致
          expect(result1).toBe(result2);
          expect(result2).toBe(result3);
        }
      ),
      { numRuns: 50 }
    );
  });
});
