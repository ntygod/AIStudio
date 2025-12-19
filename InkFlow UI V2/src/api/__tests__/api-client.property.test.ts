/**
 * API Client 属性测试
 * 
 * **Feature: frontend-api-integration, Property 1: JWT Token Attachment**
 * **Validates: Requirements 1.2**
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import * as fc from 'fast-check';
import { ApiClient, ApiClientConfig } from '../client';
import { tokenManager } from '../token-manager';

describe('ApiClient Property Tests', () => {
  let apiClient: ApiClient;
  const mockConfig: ApiClientConfig = {
    baseUrl: 'http://localhost:8080/api',
    timeout: 5000,
    onUnauthorized: vi.fn(),
  };

  beforeEach(() => {
    apiClient = new ApiClient(mockConfig);
    tokenManager.clear();
  });

  /**
   * Property 1: JWT Token Attachment
   * *For any* API request made through ApiClient when a valid access token exists 
   * in TokenManager, the request SHALL include the token in the Authorization 
   * header with "Bearer" prefix.
   * 
   * **Feature: frontend-api-integration, Property 1: JWT Token Attachment**
   * **Validates: Requirements 1.2**
   */
  it('should attach JWT token to all requests when token exists', () => {
    fc.assert(
      fc.property(
        fc.record({
          method: fc.constantFrom('GET', 'POST', 'PUT', 'DELETE', 'PATCH'),
          path: fc.string({ minLength: 1 }).map(s => '/' + s.replace(/[^a-zA-Z0-9/]/g, '')),
          token: fc.string({ minLength: 10, maxLength: 500 }).filter(s => s.length > 0 && !s.includes(' ')),
          expiresIn: fc.integer({ min: 3600, max: 86400 }), // 1 hour to 24 hours
        }),
        ({ method, path, token, expiresIn }) => {
          // Arrange: Set up token in TokenManager
          tokenManager.setTokens({
            accessToken: token,
            refreshToken: 'refresh_' + token,
            expiresAt: Date.now() + expiresIn * 1000,
          });

          // Act: Build request
          const request = apiClient.buildRequest(method, path);

          // Assert: Authorization header should contain Bearer token
          const authHeader = request.headers.get('Authorization');
          expect(authHeader).toBe(`Bearer ${token}`);
        }
      ),
      { numRuns: 100 }
    );
  });

  /**
   * Property: No token attachment when no token exists
   * *For any* API request made through ApiClient when no token exists,
   * the request SHALL NOT include an Authorization header.
   */
  it('should not attach Authorization header when no token exists', () => {
    fc.assert(
      fc.property(
        fc.record({
          method: fc.constantFrom('GET', 'POST', 'PUT', 'DELETE', 'PATCH'),
          path: fc.string({ minLength: 1 }).map(s => '/' + s.replace(/[^a-zA-Z0-9/]/g, '')),
        }),
        ({ method, path }) => {
          // Arrange: Ensure no token
          tokenManager.clear();

          // Act: Build request
          const request = apiClient.buildRequest(method, path);

          // Assert: No Authorization header
          const authHeader = request.headers.get('Authorization');
          expect(authHeader).toBeNull();
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: Content-Type header is always set
   * *For any* API request, the Content-Type header SHALL be set to application/json.
   */
  it('should always set Content-Type to application/json', () => {
    fc.assert(
      fc.property(
        fc.record({
          method: fc.constantFrom('GET', 'POST', 'PUT', 'DELETE', 'PATCH'),
          path: fc.string({ minLength: 1 }).map(s => '/' + s.replace(/[^a-zA-Z0-9/]/g, '')),
        }),
        ({ method, path }) => {
          // Act: Build request
          const request = apiClient.buildRequest(method, path);

          // Assert: Content-Type is application/json
          const contentType = request.headers.get('Content-Type');
          expect(contentType).toBe('application/json');
        }
      ),
      { numRuns: 50 }
    );
  });

  /**
   * Property: URL construction is correct
   * *For any* path, the full URL SHALL be baseUrl + path.
   */
  it('should construct correct URL from baseUrl and path', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }).map(s => '/' + s.replace(/[^a-zA-Z0-9/]/g, '')),
        (path) => {
          // Act: Build request
          const request = apiClient.buildRequest('GET', path);

          // Assert: URL is correctly constructed
          expect(request.url).toBe(`${mockConfig.baseUrl}${path}`);
        }
      ),
      { numRuns: 50 }
    );
  });
});
