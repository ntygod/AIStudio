# Implementation Plan

- [x] 1. 创建数据库迁移脚本





  - [x] 1.1 创建 V2__zhparser_integration.sql 迁移文件


    - 安装 zhparser 扩展
    - 创建 chinese 全文搜索配置
    - 配置词典映射（名词、动词、形容词等）
    - 设置 zhparser 参数（multi_short, punctuation_ignore）
    - _Requirements: 1.1, 2.1, 2.2_

  - [x] 1.2 更新 knowledge_chunks 表的 text_search 列

    - 修改 GENERATED ALWAYS AS 使用 chinese 配置
    - 重建 GIN 索引
    - _Requirements: 3.1, 3.3_

  - [x] 1.3 创建验证函数 verify_zhparser_config()

    - 返回当前 zhparser 配置状态
    - _Requirements: 6.1_

- [x] 2. 实现 ZhparserHealthChecker 组件






  - [x] 2.1 创建 ZhparserHealthChecker 类


    - 实现 ApplicationRunner 接口
    - 启动时检查 zhparser 扩展可用性
    - 验证 chinese 配置是否正常工作
    - 提供 isZhparserAvailable() 和 getEffectiveLanguage() 方法
    - _Requirements: 6.1, 1.4_
  - [ ]* 2.2 编写属性测试：降级行为
    - **Property 2: Graceful Degradation**
    - **Validates: Requirements 1.4, 5.4**

- [x] 3. 扩展 RagProperties 配置

  - [x] 3.1 添加 ZhparserConfig record
    - 包含 multiShort, multiDuality, punctuationIgnore, customDictPath 字段
    - 提供默认值
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 更新 FullTextConfig
    - 添加 zhparser 字段
    - 将默认语言改为 chinese
    - _Requirements: 1.1_

  - [x] 3.3 更新 application.yml 配置示例

    - 添加 zhparser 配置节
    - _Requirements: 2.1, 2.2_

- [x] 4. 更新 FullTextSearchService





  - [x] 4.1 注入 ZhparserHealthChecker 依赖


    - 添加构造函数参数
    - _Requirements: 5.3_

  - [x] 4.2 实现 getEffectiveLanguage() 方法

    - 检查配置的语言
    - 验证 zhparser 可用性
    - 实现降级逻辑
    - _Requirements: 1.4, 5.1_

  - [x] 4.3 更新 SUPPORTED_LANGUAGES 白名单

    - 添加 "chinese" 到支持的语言列表
    - _Requirements: 5.1_

  - [x] 4.4 更新所有 SQL 查询构建方法

    - 使用 getEffectiveLanguage() 替代 getSearchLanguage()
    - 确保 plainto_tsquery, phraseto_tsquery, to_tsquery 都使用正确配置
    - _Requirements: 5.1, 5.2_
  - [ ]* 4.5 编写属性测试：配置一致性
    - **Property 1: Chinese Configuration Consistency**
    - **Validates: Requirements 1.2, 1.3, 3.1, 3.2, 5.1**
  - [ ]* 4.6 编写属性测试：排名正确性
    - **Property 5: Relevance Ranking Correctness**
    - **Validates: Requirements 4.1, 4.3, 4.4**

- [ ] 5. Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. 实现 ZhparserDictionaryService






  - [x] 6.1 创建 ZhparserDictionaryService 类

    - 实现 testSegmentation() 方法用于测试分词效果
    - 实现 loadCustomDictionary() 方法（预留接口）
    - _Requirements: 2.3, 2.4_
  - [ ]* 6.2 编写属性测试：标点过滤
    - **Property 3: Punctuation Filtering**
    - **Validates: Requirements 2.2**
  - [ ]* 6.3 编写属性测试：自定义词典识别
    - **Property 4: Custom Dictionary Recognition**
    - **Validates: Requirements 2.4**

- [x] 7. 实现混合语言查询支持





  - [x] 7.1 更新 detectQueryType() 方法


    - 改进中英混合查询检测逻辑
    - _Requirements: 4.2_

  - [x] 7.2 更新 preprocessQuery() 方法

    - 保留英文字符的正确处理
    - _Requirements: 4.2_
  - [ ]* 7.3 编写属性测试：混合语言查询
    - **Property 6: Mixed-Language Query Handling**
    - **Validates: Requirements 4.2**

- [ ] 8. 添加性能监控
  - [ ] 8.1 添加查询执行时间日志
    - 在 executeSearch() 方法中记录执行时间
    - 超过阈值时记录警告
    - _Requirements: 6.2_
  - [ ] 8.2 添加 Micrometer 指标
    - 记录搜索延迟、成功率等指标
    - _Requirements: 6.2_

- [ ] 9. 创建数据迁移工具
  - [ ] 9.1 实现 reindexAllChunks() 方法
    - 批量更新现有 knowledge_chunks 的 tsvector
    - 支持增量迁移
    - _Requirements: 3.4, 6.3_
  - [ ]* 9.2 编写属性测试：搜索往返一致性
    - **Property 7: Search Round-Trip Consistency**
    - **Validates: Requirements 1.2, 3.2**

- [ ] 10. Final Checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.
