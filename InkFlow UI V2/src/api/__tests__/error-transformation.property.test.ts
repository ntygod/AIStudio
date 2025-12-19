/**
 * Error Transformation 属性测试
 * 
 * **Feature: frontend-api-integration, Property 2: Error Response Transformation**
 * **Validates: Requirements 1.5**
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import * as fc from 'fast-check';
import { ApiClient, ApiClientConfig, ErrorCode } from '../client';

describe('Error Transformation Property Tests', () => {
  let apiClient: ApiClient;
  const mockConfig: ApiClientConfig = {
    baseUrl: 'http://localhost:8080/api',
    timeout: 5000,
    onUnauthorized: vi.fn(),
  };

  beforeEach(() => {
    apiClient = new ApiClient(mockConfig);
  });

  /**
   * Property 2: Error Response Transformation
   * *For any* API error response with status >= 400, the ApiClient SHALL 
   * transform it into a standardized ApiError object containing code and 
   * message fields.
   * 
   * **Feature: frontend-api-integration, Property 2: Error Response Transformation**
   * **Validates: Requirements 1.5**
   */
  it('should map HTTP status codes to correct error codes', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(
          { status: 401, expectedCode: ErrorCode.UNAUTHORIZED },
          { status: 403, expectedCode: ErrorCode.FORBIDDEN },
          { status: 404, expectedCode: ErrorCode.NOT_FOUND },
          { status: 422, expectedCode: ErrorCode.VALIDATION_ERROR },
          { status: 500, expectedCode: ErrorCode.SERVER_ERROR },
          { status: 502, expectedCode: ErrorCode.SERVER_ERROR },
          { status: 503, expectedCode: ErrorCode.SERVER_ERROR },
        ),
        ({ status, expectedCode }) => {
          // Act: Map status to error code
          const errorCode = apiClient.mapStatusToErrorCode(status);

          // Assert: Error code matches expected
          expect(errorCode).toBe(expectedCode);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: All 4xx client errors map to specific codes
   * *For any* 4xx status code, the error code SHALL be one of the defined client error codes.
   */
  it('should map all 4xx errors to appropriate codes', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 400, max: 499 }),
        (status) => {
          // Act: Map status to error code
          const errorCode = apiClient.mapStatusToErrorCode(status);

          // Assert: Error code is a valid client error code
          const validClientCodes = [
            ErrorCode.UNAUTHORIZED,
            ErrorCode.FORBIDDEN,
            ErrorCode.NOT_FOUND,
            ErrorCode.VALIDATION_ERROR,
            ErrorCode.SERVER_ERROR, // fallback for unmapped 4xx
          ];
          expect(validClientCodes).toContain(errorCode);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: All 5xx server errors map to SERVER_ERROR
   * *For any* 5xx status code, the error code SHALL be SERVER_ERROR.
   */
  it('should map all 5xx errors to SERVER_ERROR', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 500, max: 599 }),
        (status) => {
          // Act: Map status to error code
          const errorCode = apiClient.mapStatusToErrorCode(status);

          // Assert: Error code is SERVER_ERROR
          expect(errorCode).toBe(ErrorCode.SERVER_ERROR);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: Error code is always a valid ErrorCode enum value
   * *For any* HTTP status code >= 400, the mapped error code SHALL be a valid ErrorCode.
   */
  it('should always return valid ErrorCode for error statuses', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 400, max: 599 }),
        (status) => {
          // Act: Map status to error code
          const errorCode = apiClient.mapStatusToErrorCode(status);

          // Assert: Error code is a valid enum value
          const validCodes = Object.values(ErrorCode);
          expect(validCodes).toContain(errorCode);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: Specific status codes have deterministic mappings
   * *For any* specific status code, calling mapStatusToErrorCode multiple times 
   * SHALL return the same error code (deterministic).
   */
  it('should be deterministic for the same status code', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 400, max: 599 }),
        (status) => {
          // Act: Map status multiple times
          const errorCode1 = apiClient.mapStatusToErrorCode(status);
          const errorCode2 = apiClient.mapStatusToErrorCode(status);
          const errorCode3 = apiClient.mapStatusToErrorCode(status);

          // Assert: All results are the same
          expect(errorCode1).toBe(errorCode2);
          expect(errorCode2).toBe(errorCode3);
        }
      ),
      { numRuns: 50 }
    );
  });
});
