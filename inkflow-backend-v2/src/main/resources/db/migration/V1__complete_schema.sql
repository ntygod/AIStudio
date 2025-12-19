-- ============================================================
-- InkFlow 2.0 - 完整数据库架构
-- PostgreSQL 18+ with pgvector and zhparser extensions
-- 生成日期: 2025-12-18
-- ============================================================

-- ============================================================
-- 扩展启用
-- ============================================================
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- zhparser 中文分词扩展，基于 SCWS (Simple Chinese Word Segmentation)
CREATE EXTENSION IF NOT EXISTS zhparser;

-- ============================================================
-- zhparser 中文全文搜索配置
-- Requirements: 1.1, 2.1, 2.2
-- ============================================================

-- 创建中文全文搜索配置，使用 zhparser 作为解析器
DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese CASCADE;
CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser);

-- 配置词典映射
-- zhparser 词性标注: a(形容词), b(区别词), c(连词), d(副词), e(叹词), f(方位词)
-- g(语素), h(前缀), i(成语), j(简称), k(后缀), l(习惯用语), m(数词), n(名词)
-- o(拟声词), p(介词), q(量词), r(代词), s(处所词), t(时间词), u(助词), v(动词)
-- w(标点符号), x(非语素字), y(语气词), z(状态词)
ALTER TEXT SEARCH CONFIGURATION chinese ADD MAPPING FOR 
    a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z 
    WITH simple;

-- 配置 zhparser 参数
-- multi_short: 短词复合，提高召回率
-- multi_duality: 散字二元复合
-- punctuation_ignore: 忽略标点符号
ALTER DATABASE novel_db SET zhparser.multi_short = on;
ALTER DATABASE novel_db SET zhparser.multi_duality = off;
ALTER DATABASE novel_db SET zhparser.multi_zmain = off;
ALTER DATABASE novel_db SET zhparser.multi_zall = off;
ALTER DATABASE novel_db SET zhparser.punctuation_ignore = on;

-- ============================================================
-- 1. 用户认证模块
-- ============================================================

-- 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    display_name VARCHAR(100),
    avatar_url TEXT,
    bio VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN NOT NULL DEFAULT false,
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_users_email ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_username ON users(username) WHERE deleted_at IS NULL;

-- 刷新令牌表
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ============================================================
-- 2. 项目管理模块
-- ============================================================

-- 项目表
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    cover_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    creation_phase VARCHAR(30) NOT NULL DEFAULT 'IDEA',
    metadata JSONB DEFAULT '{}',
    world_settings JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_projects_user_id ON projects(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_status ON projects(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_metadata ON projects USING GIN (metadata);

-- 分卷表
CREATE TABLE volumes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_volumes_project ON volumes(project_id, order_index) WHERE deleted_at IS NULL;

-- 章节表
CREATE TABLE chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volume_id UUID NOT NULL REFERENCES volumes(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    order_index INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    word_count INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_chapters_volume ON chapters(volume_id, order_index) WHERE deleted_at IS NULL;
CREATE INDEX idx_chapters_project ON chapters(project_id) WHERE deleted_at IS NULL;

-- 剧情块表 (Lexorank排序)
CREATE TABLE story_blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    block_type VARCHAR(30) NOT NULL DEFAULT 'NARRATIVE',
    content TEXT,
    rank VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    word_count INTEGER NOT NULL DEFAULT 0,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_story_blocks_chapter_id ON story_blocks(chapter_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_story_blocks_rank ON story_blocks(chapter_id, rank) WHERE deleted_at IS NULL;

COMMENT ON COLUMN story_blocks.rank IS 'Lexorank排序字符串，支持O(1)时间复杂度的插入操作';

-- ============================================================
-- 3. 角色管理模块
-- ============================================================

-- 角色表
CREATE TABLE characters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    description TEXT,
    personality JSONB DEFAULT '{}',
    relationships JSONB DEFAULT '[]',
    status VARCHAR(50) DEFAULT 'active',
    is_active BOOLEAN DEFAULT true,
    archetype VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_characters_project ON characters(project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_characters_name ON characters(project_id, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_characters_role ON characters(role) WHERE deleted_at IS NULL;
CREATE INDEX idx_characters_personality ON characters USING GIN (personality);
CREATE INDEX idx_characters_relationships ON characters USING GIN (relationships);

COMMENT ON COLUMN characters.relationships IS '角色关系数组，格式: [{targetId, type, description}]';
COMMENT ON COLUMN characters.archetype IS '角色原型: 垫脚石, 老爷爷, 欢喜冤家, 线人, 守门人, 牺牲者, 搞笑担当, 宿敌';

-- 角色原型模板表
CREATE TABLE character_archetypes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    name_cn VARCHAR(100) NOT NULL,
    description TEXT,
    template JSONB DEFAULT '{}',
    examples TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- 预置角色原型
INSERT INTO character_archetypes (name, name_cn, description, template, examples) VALUES
('stepping_stone', '垫脚石', '用于衬托主角成长的角色，通常在早期被主角超越', 
 '{"traits": ["自大", "轻敌", "实力有限"], "function": "衬托主角成长"}', 
 ARRAY['修仙小说中的世家子弟', '武侠中的门派弟子']),
('mentor', '导师', '指导主角的神秘长者，通常拥有强大实力或丰富知识', 
 '{"traits": ["神秘", "睿智", "强大"], "function": "传授技能或知识"}', 
 ARRAY['戒指里的老爷爷', '隐居的绝世高手']),
('love_hate', '欢喜冤家', '与主角有复杂情感关系的角色，常有误会和冲突', 
 '{"traits": ["傲娇", "倔强", "内心善良"], "function": "情感线发展"}', 
 ARRAY['女主角', '竞争对手']),
('informant', '线人', '为主角提供关键信息的角色', 
 '{"traits": ["消息灵通", "神出鬼没"], "function": "推动剧情"}', 
 ARRAY['情报贩子', '神秘商人']),
('gatekeeper', '守门人', '阻挡主角前进的角色，需要被说服或击败', 
 '{"traits": ["固执", "有原则"], "function": "制造障碍"}', 
 ARRAY['关卡守卫', '门派长老']),
('sacrifice', '牺牲者', '为主角或大义牺牲的角色，推动主角成长', 
 '{"traits": ["善良", "无私"], "function": "情感冲击"}', 
 ARRAY['师父', '挚友']),
('comic_relief', '搞笑担当', '负责调节气氛的角色', 
 '{"traits": ["幽默", "乐观", "有时犯傻"], "function": "调节气氛"}', 
 ARRAY['话痨队友', '搞笑宠物']),
('nemesis', '宿敌', '与主角有深刻对立的角色，贯穿全文', 
 '{"traits": ["强大", "执着", "有自己的正义"], "function": "核心冲突"}', 
 ARRAY['反派BOSS', '命运对手']),
('hero', '英雄', '故事的主要推动者，通常经历成长和转变', 
 '{"traits": ["勇敢", "正义", "成长"], "function": "推动主线剧情，承载读者代入感"}', 
 ARRAY['孙悟空', '哈利·波特', '林动']),
('shadow', '阴影', '英雄的对立面，代表需要克服的障碍', 
 '{"traits": ["强大", "威胁", "复杂"], "function": "制造冲突，考验英雄"}', 
 ARRAY['伏地魔', '萨鲁曼', '魂天帝']),
('herald', '使者', '带来变化的催化剂，打破英雄的日常', 
 '{"traits": ["神秘", "紧迫", "变化"], "function": "开启冒险，带来转折"}', 
 ARRAY['海格', '白兔先生', '神秘来客']),
('shapeshifter', '变形者', '立场模糊的角色，增加故事的不确定性', 
 '{"traits": ["多变", "神秘", "魅力"], "function": "制造悬念，增加复杂性"}', 
 ARRAY['斯内普', '双面间谍', '亦正亦邪的角色']),
('threshold_guardian', '门槛守卫', '考验英雄的障碍，守护重要关卡', 
 '{"traits": ["强大", "考验", "转化"], "function": "测试英雄，标志成长"}', 
 ARRAY['三头犬', '守关BOSS', '考验者']),
('trickster', '骗子', '带来混乱和幽默的角色，打破常规', 
 '{"traits": ["机智", "幽默", "混乱"], "function": "调节气氛，揭示真相"}', 
 ARRAY['洛基', '猪八戒', '搞笑担当']),
('ally', '盟友', '支持英雄的伙伴，提供帮助和陪伴', 
 '{"traits": ["忠诚", "互补", "成长"], "function": "支持英雄，丰富团队"}', 
 ARRAY['罗恩', '赫敏', '萧薰儿']);

-- ============================================================
-- 4. 知识库模块
-- ============================================================

-- 知识条目表
CREATE TABLE wiki_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT,
    aliases TEXT[] DEFAULT '{}',
    tags TEXT[] DEFAULT '{}',
    time_version VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_wiki_entries_project ON wiki_entries(project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_wiki_entries_type ON wiki_entries(project_id, type) WHERE deleted_at IS NULL;
CREATE INDEX idx_wiki_entries_title ON wiki_entries(project_id, title) WHERE deleted_at IS NULL;
CREATE INDEX idx_wiki_entries_aliases ON wiki_entries USING GIN (aliases);
CREATE INDEX idx_wiki_entries_tags ON wiki_entries USING GIN (tags);

COMMENT ON COLUMN wiki_entries.time_version IS '时间版本标识，如"第一卷"、"修炼前"等';

-- ============================================================
-- 5. 伏笔追踪模块
-- ============================================================

-- 伏笔表
CREATE TABLE plot_loops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    intro_chapter_id UUID REFERENCES chapters(id),
    intro_chapter_order INTEGER,
    resolution_chapter_id UUID REFERENCES chapters(id),
    resolution_chapter_order INTEGER,
    abandon_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_plot_loops_project ON plot_loops(project_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_plot_loops_status ON plot_loops(project_id, status) WHERE deleted_at IS NULL;

COMMENT ON COLUMN plot_loops.status IS '伏笔状态: OPEN(开放), URGENT(超过10章未回收), CLOSED(已回收), ABANDONED(已放弃)';

-- ============================================================
-- 6. RAG知识块模块 (向量检索)
-- ============================================================

-- 知识块表
CREATE TABLE knowledge_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    parent_id UUID REFERENCES knowledge_chunks(id),
    content TEXT NOT NULL,
    embedding halfvec(1024),
    -- 使用 zhparser chinese 配置进行中文分词，提高中文搜索质量
    text_search tsvector GENERATED ALWAYS AS (to_tsvector('chinese', COALESCE(content, ''))) STORED,
    chunk_level VARCHAR(20) DEFAULT 'parent',
    chunk_order INTEGER DEFAULT 0,
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    is_dirty BOOLEAN DEFAULT false,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_chunks_project ON knowledge_chunks(project_id) WHERE is_active = true;
CREATE INDEX idx_knowledge_chunks_source ON knowledge_chunks(source_type, source_id) WHERE is_active = true;
CREATE INDEX idx_knowledge_chunks_parent ON knowledge_chunks(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_knowledge_chunks_dirty ON knowledge_chunks(is_dirty) WHERE is_dirty = true;
CREATE INDEX idx_knowledge_chunks_version ON knowledge_chunks(source_id, version DESC);

-- HNSW向量索引 (BGE-M3 1024维, halfvec半精度)
CREATE INDEX idx_knowledge_chunks_embedding ON knowledge_chunks 
USING hnsw (embedding halfvec_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- 全文搜索GIN索引
CREATE INDEX idx_knowledge_chunks_text_search ON knowledge_chunks USING GIN (text_search);

COMMENT ON TABLE knowledge_chunks IS 'RAG知识块表，支持向量检索和版本控制';
COMMENT ON COLUMN knowledge_chunks.embedding IS '1024维半精度向量嵌入(BGE-M3)，使用HNSW索引加速余弦相似度搜索';
COMMENT ON COLUMN knowledge_chunks.text_search IS 'Full-text search vector using zhparser chinese configuration for high-quality Chinese segmentation';

-- 嵌入缓存表
CREATE TABLE embedding_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    embedding halfvec(1024) NOT NULL,
    model VARCHAR(100) NOT NULL,
    hit_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_embedding_cache_hash ON embedding_cache(content_hash);
CREATE INDEX idx_embedding_cache_accessed ON embedding_cache(last_accessed_at);

COMMENT ON TABLE embedding_cache IS '嵌入向量缓存表(BGE-M3 1024维半精度)，减少重复内容的API调用';

-- ============================================================
-- 7. 演进时间线模块
-- ============================================================

-- 演进时间线表
CREATE TABLE evolution_timelines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    UNIQUE(entity_type, entity_id)
);

CREATE INDEX idx_evolution_timelines_project ON evolution_timelines(project_id);
CREATE INDEX idx_evolution_timelines_entity ON evolution_timelines(entity_type, entity_id);

COMMENT ON TABLE evolution_timelines IS '演进时间线表，记录角色/设定随剧情发展的变化轨迹';

-- 状态快照表
CREATE TABLE state_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timeline_id UUID NOT NULL REFERENCES evolution_timelines(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapters(id),
    chapter_order INTEGER NOT NULL,
    is_keyframe BOOLEAN DEFAULT false,
    state_data JSONB NOT NULL,
    change_summary TEXT,
    change_type VARCHAR(50) DEFAULT 'update',
    ai_confidence DECIMAL(3, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_state_snapshots_timeline ON state_snapshots(timeline_id, chapter_order);
CREATE INDEX idx_state_snapshots_chapter ON state_snapshots(chapter_id);
CREATE INDEX idx_state_snapshots_keyframe ON state_snapshots(timeline_id, chapter_order) WHERE is_keyframe = true;

COMMENT ON TABLE state_snapshots IS '状态快照表，支持关键帧+增量策略减少存储';
COMMENT ON COLUMN state_snapshots.is_keyframe IS '是否为关键帧。关键帧存储完整状态，增量帧存储JSON diff';

-- 变更记录表
CREATE TABLE change_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES state_snapshots(id) ON DELETE CASCADE,
    field_path VARCHAR(255) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    change_reason TEXT,
    source_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_records_snapshot ON change_records(snapshot_id);

COMMENT ON TABLE change_records IS '变更记录表，记录每个快照中的具体字段变更';


-- ============================================================
-- 8. 对话历史模块
-- ============================================================

-- 对话历史表
CREATE TABLE conversation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    session_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_calls JSONB,
    message_order INTEGER NOT NULL,
    creation_phase VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversation_history_user ON conversation_history(user_id);
CREATE INDEX idx_conversation_history_project ON conversation_history(project_id);
CREATE INDEX idx_conversation_history_session ON conversation_history(session_id, message_order);

COMMENT ON TABLE conversation_history IS 'AI对话历史表，支持ChatMemory持久化';

-- ============================================================
-- 9. Token使用记录模块
-- ============================================================

-- Token使用记录表
CREATE TABLE token_usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    model_name VARCHAR(100) NOT NULL,
    provider VARCHAR(50),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    cost DOUBLE PRECISION,
    operation_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_token_usage_user ON token_usage_records(user_id, created_at);
CREATE INDEX idx_token_usage_project ON token_usage_records(project_id);
CREATE INDEX idx_token_usage_model ON token_usage_records(model_name);
CREATE INDEX idx_token_usage_date ON token_usage_records(created_at);

-- 按日期分区的统计视图
CREATE OR REPLACE VIEW daily_token_usage AS
SELECT 
    user_id,
    DATE(created_at) as usage_date,
    model_name,
    SUM(prompt_tokens) as total_prompt_tokens,
    SUM(completion_tokens) as total_completion_tokens,
    SUM(total_tokens) as total_tokens,
    SUM(cost) as total_cost,
    COUNT(*) as request_count
FROM token_usage_records
GROUP BY user_id, DATE(created_at), model_name;

COMMENT ON TABLE token_usage_records IS 'Token使用记录表，用于成本监控和用量统计';

-- ============================================================
-- 10. 工具调用日志模块
-- ============================================================

-- 工具调用日志表
CREATE TABLE tool_invocation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    request_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    parameters JSONB,
    success BOOLEAN NOT NULL DEFAULT true,
    duration_ms BIGINT,
    result_summary TEXT,
    error_message TEXT,
    error_stack_trace TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_invocation_user ON tool_invocation_logs(user_id);
CREATE INDEX idx_tool_invocation_project ON tool_invocation_logs(project_id);
CREATE INDEX idx_tool_invocation_tool ON tool_invocation_logs(tool_name);
CREATE INDEX idx_tool_invocation_request ON tool_invocation_logs(request_id);
CREATE INDEX idx_tool_invocation_created ON tool_invocation_logs(created_at);
CREATE INDEX idx_tool_invocation_failed ON tool_invocation_logs(success) WHERE success = false;

COMMENT ON TABLE tool_invocation_logs IS 'AI工具调用日志表，用于监控和调试';

-- ============================================================
-- 11. AI服务商配置模块
-- ============================================================

-- AI服务商配置表
CREATE TABLE ai_provider_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_type VARCHAR(50) NOT NULL,
    encrypted_key TEXT,
    key_hint VARCHAR(10),
    base_url VARCHAR(500),
    default_model VARCHAR(100),
    model_mapping JSONB,
    is_default BOOLEAN DEFAULT FALSE,
    is_configured BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    UNIQUE(user_id, provider_type)
);

CREATE INDEX idx_ai_provider_configs_user_id ON ai_provider_configs(user_id);
CREATE INDEX idx_ai_provider_configs_provider_type ON ai_provider_configs(provider_type);

COMMENT ON TABLE ai_provider_configs IS 'AI服务商配置表';

-- 用户级AI提供商偏好配置表 (来自 V11)
CREATE TABLE user_provider_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preferred_provider VARCHAR(50) NOT NULL,
    preferred_model VARCHAR(100),
    reasoning_enabled BOOLEAN DEFAULT false,
    reasoning_provider VARCHAR(50),
    reasoning_model VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    UNIQUE(user_id)
);

CREATE INDEX idx_user_provider_configs_user_id ON user_provider_configs(user_id);
CREATE INDEX idx_user_provider_configs_preferred_provider ON user_provider_configs(preferred_provider);

COMMENT ON TABLE user_provider_configs IS '用户级AI提供商配置表，存储用户的默认AI偏好设置';
COMMENT ON COLUMN user_provider_configs.user_id IS '用户ID，每个用户只有一条配置';
COMMENT ON COLUMN user_provider_configs.preferred_provider IS '用户偏好的AI提供商类型';
COMMENT ON COLUMN user_provider_configs.preferred_model IS '用户偏好的模型名称';
COMMENT ON COLUMN user_provider_configs.reasoning_enabled IS '是否启用推理模型';
COMMENT ON COLUMN user_provider_configs.reasoning_provider IS '推理模型的提供商类型';
COMMENT ON COLUMN user_provider_configs.reasoning_model IS '推理模型名称';

-- ============================================================
-- 12. 风格样本模块
-- ============================================================

-- 风格样本表
CREATE TABLE style_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    chapter_id UUID,
    original_ai TEXT NOT NULL,
    user_final TEXT NOT NULL,
    edit_ratio DOUBLE PRECISION NOT NULL,
    vector halfvec(1024),
    word_count INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_style_samples_project_id ON style_samples(project_id);
CREATE INDEX idx_style_samples_chapter_id ON style_samples(chapter_id);
CREATE INDEX idx_style_samples_vector ON style_samples USING hnsw (vector halfvec_cosine_ops) WITH (m = 16, ef_construction = 128);

COMMENT ON TABLE style_samples IS '风格样本表(BGE-M3 1024维半精度)，用于学习用户写作风格';

-- ============================================================
-- 13. 章节快照模块
-- ============================================================

-- 章节快照表
CREATE TABLE chapter_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    word_count INTEGER,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_chapter_snapshots_chapter_id ON chapter_snapshots(chapter_id);
CREATE INDEX idx_chapter_snapshots_created_at ON chapter_snapshots(created_at DESC);

COMMENT ON TABLE chapter_snapshots IS '章节快照表，存储章节版本历史';

-- ============================================================
-- 14. 限流模块
-- ============================================================

-- 限流配置表 (用户级别)
CREATE TABLE rate_limit_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    bucket_capacity INT NOT NULL DEFAULT 100,
    refill_rate INT NOT NULL DEFAULT 10,
    window_seconds INT NOT NULL DEFAULT 60,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rate_limit_configs_user_id ON rate_limit_configs(user_id);

-- 限流规则表 (端点级别)
CREATE TABLE rate_limit_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_pattern VARCHAR(255) NOT NULL,
    http_method VARCHAR(10),
    bucket_capacity INT NOT NULL DEFAULT 100,
    refill_rate INT NOT NULL DEFAULT 10,
    priority INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rate_limit_rules_pattern ON rate_limit_rules(endpoint_pattern);
CREATE INDEX idx_rate_limit_rules_priority ON rate_limit_rules(priority DESC);

-- ============================================================
-- 15. 会话管理模块
-- ============================================================

-- 用户会话表
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    current_project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    current_phase VARCHAR(50),
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX idx_user_sessions_active ON user_sessions(user_id, is_active) WHERE is_active = true;

-- ============================================================
-- 16. 进度追踪模块
-- ============================================================

-- 进度快照表
CREATE TABLE progress_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    phase VARCHAR(50),
    phase_completion INT DEFAULT 0,
    character_count BIGINT DEFAULT 0,
    wiki_entry_count BIGINT DEFAULT 0,
    volume_count BIGINT DEFAULT 0,
    chapter_count BIGINT DEFAULT 0,
    word_count BIGINT DEFAULT 0,
    plot_loop_count BIGINT DEFAULT 0,
    open_plot_loops BIGINT DEFAULT 0,
    closed_plot_loops BIGINT DEFAULT 0,
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_progress_snapshots_project_id ON progress_snapshots(project_id);
CREATE INDEX idx_progress_snapshots_snapshot_at ON progress_snapshots(project_id, snapshot_at DESC);

-- 阶段转换历史表
CREATE TABLE phase_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_phase VARCHAR(50),
    to_phase VARCHAR(50) NOT NULL,
    reason TEXT,
    triggered_by VARCHAR(50) DEFAULT 'USER',
    transitioned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_phase_transitions_project_id ON phase_transitions(project_id);
CREATE INDEX idx_phase_transitions_time ON phase_transitions(project_id, transitioned_at DESC);


-- ============================================================
-- 17. 一致性检查模块 (整合自 V8)
-- ============================================================

-- 一致性警告表
CREATE TABLE consistency_warnings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_type VARCHAR(50),
    entity_id UUID,
    entity_name VARCHAR(255),
    warning_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    description TEXT NOT NULL,
    suggestion TEXT,
    field_path VARCHAR(255),
    expected_value TEXT,
    actual_value TEXT,
    related_entity_ids JSONB,
    suggested_resolution TEXT,
    resolved BOOLEAN NOT NULL DEFAULT false,
    dismissed BOOLEAN NOT NULL DEFAULT false,
    resolution_method VARCHAR(255),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consistency_warnings_project ON consistency_warnings(project_id);
CREATE INDEX idx_consistency_warnings_entity ON consistency_warnings(entity_type, entity_id);
CREATE INDEX idx_consistency_warnings_unresolved ON consistency_warnings(project_id) WHERE resolved = false;
CREATE INDEX idx_consistency_warnings_type ON consistency_warnings(warning_type);
CREATE INDEX idx_consistency_warnings_severity ON consistency_warnings(severity);

COMMENT ON TABLE consistency_warnings IS '一致性警告表，记录检测到的设定冲突';

-- ============================================================
-- 18. 触发器和函数
-- ============================================================

-- 更新 updated_at 时间戳的函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 实体变更通知函数 (CDC) - 整合自 V12
-- 当实体发生 INSERT/UPDATE/DELETE 时，发送通知到 entity_changes 频道
CREATE OR REPLACE FUNCTION notify_entity_change() RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('entity_changes', json_build_object(
        'table', TG_TABLE_NAME,
        'operation', TG_OP,
        'id', COALESCE(NEW.id, OLD.id),
        'project_id', COALESCE(NEW.project_id, OLD.project_id),
        'timestamp', CURRENT_TIMESTAMP
    )::text);
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION notify_entity_change() IS 'CDC function for entity change notifications via pg_notify';

-- ============================================================
-- 19. 自动更新 updated_at 触发器
-- ============================================================

-- users 表
CREATE TRIGGER trigger_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- projects 表
CREATE TRIGGER trigger_projects_updated_at
BEFORE UPDATE ON projects
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- volumes 表
CREATE TRIGGER trigger_volumes_updated_at
BEFORE UPDATE ON volumes
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- chapters 表
CREATE TRIGGER trigger_chapters_updated_at
BEFORE UPDATE ON chapters
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- story_blocks 表
CREATE TRIGGER trigger_story_blocks_updated_at
BEFORE UPDATE ON story_blocks
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- characters 表
CREATE TRIGGER trigger_characters_updated_at
BEFORE UPDATE ON characters
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- wiki_entries 表
CREATE TRIGGER trigger_wiki_entries_updated_at
BEFORE UPDATE ON wiki_entries
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- plot_loops 表
CREATE TRIGGER trigger_plot_loops_updated_at
BEFORE UPDATE ON plot_loops
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- knowledge_chunks 表
CREATE TRIGGER trigger_knowledge_chunks_updated_at
BEFORE UPDATE ON knowledge_chunks
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- evolution_timelines 表
CREATE TRIGGER trigger_evolution_timelines_updated_at
BEFORE UPDATE ON evolution_timelines
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ai_provider_configs 表
CREATE TRIGGER trigger_ai_provider_configs_updated_at
BEFORE UPDATE ON ai_provider_configs
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- user_provider_configs 表
CREATE TRIGGER trigger_user_provider_configs_updated_at
BEFORE UPDATE ON user_provider_configs
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- style_samples 表
CREATE TRIGGER trigger_style_samples_updated_at
BEFORE UPDATE ON style_samples
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- chapter_snapshots 表
CREATE TRIGGER trigger_chapter_snapshots_updated_at
BEFORE UPDATE ON chapter_snapshots
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- rate_limit_configs 表
CREATE TRIGGER trigger_rate_limit_configs_updated_at
BEFORE UPDATE ON rate_limit_configs
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- rate_limit_rules 表
CREATE TRIGGER trigger_rate_limit_rules_updated_at
BEFORE UPDATE ON rate_limit_rules
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 20. CDC 触发器 (整合自 V12)
-- ============================================================

-- Characters 表触发器
CREATE TRIGGER trigger_character_change
AFTER INSERT OR UPDATE OR DELETE ON characters
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

COMMENT ON TRIGGER trigger_character_change ON characters IS 'Triggers consistency check and evolution snapshot on character changes';

-- Wiki Entries 表触发器
CREATE TRIGGER trigger_wiki_entry_change
AFTER INSERT OR UPDATE OR DELETE ON wiki_entries
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

COMMENT ON TRIGGER trigger_wiki_entry_change ON wiki_entries IS 'Triggers consistency check and evolution snapshot on wiki entry changes';

-- Plot Loops 表触发器
CREATE TRIGGER trigger_plot_loop_change
AFTER INSERT OR UPDATE OR DELETE ON plot_loops
FOR EACH ROW EXECUTE FUNCTION notify_entity_change();

COMMENT ON TRIGGER trigger_plot_loop_change ON plot_loops IS 'Triggers consistency check and evolution snapshot on plot loop changes';

-- ============================================================
-- 21. zhparser 验证函数
-- Requirements: 6.1
-- ============================================================

-- 验证 zhparser 配置状态的函数
CREATE OR REPLACE FUNCTION verify_zhparser_config()
RETURNS TABLE(
    config_name TEXT, 
    config_value TEXT,
    description TEXT
) AS $function$
BEGIN
    -- 检查 zhparser 扩展是否安装
    RETURN QUERY
    SELECT 
        'zhparser_extension_installed'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_extension WHERE extname = 'zhparser'
        ) THEN 'true' ELSE 'false' END,
        'zhparser 扩展是否已安装'::TEXT;
    
    -- 检查 chinese 配置是否存在
    RETURN QUERY
    SELECT 
        'chinese_config_exists'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_ts_config WHERE cfgname = 'chinese'
        ) THEN 'true' ELSE 'false' END,
        'chinese 全文搜索配置是否存在'::TEXT;
    
    -- 获取 zhparser 参数设置
    RETURN QUERY
    SELECT 
        'zhparser.multi_short'::TEXT,
        current_setting('zhparser.multi_short', true),
        '短词复合模式 (on=开启, off=关闭)'::TEXT;
    
    RETURN QUERY
    SELECT 
        'zhparser.punctuation_ignore'::TEXT,
        current_setting('zhparser.punctuation_ignore', true),
        '忽略标点符号 (on=开启, off=关闭)'::TEXT;
    
    -- 测试分词功能
    RETURN QUERY
    SELECT 
        'segmentation_test'::TEXT,
        CASE 
            WHEN EXISTS (
                SELECT 1 FROM pg_ts_config WHERE cfgname = 'chinese'
            ) THEN (
                SELECT string_agg(lexeme, ', ' ORDER BY positions[1])
                FROM unnest(to_tsvector('chinese', '中华人民共和国'))
            )
            ELSE 'chinese config not available'
        END,
        '分词测试结果 (输入: 中华人民共和国)'::TEXT;
END;
$function$ LANGUAGE plpgsql;

COMMENT ON FUNCTION verify_zhparser_config() IS 
    '验证 zhparser 配置状态的函数，返回当前配置参数和分词测试结果';

-- 测试中文分词效果的函数
CREATE OR REPLACE FUNCTION test_chinese_segmentation(input_text TEXT)
RETURNS TABLE(
    token TEXT,
    pos INTEGER
) AS $function$
BEGIN
    RETURN QUERY
    SELECT 
        lexeme::TEXT,
        (positions[1])::INTEGER
    FROM unnest(to_tsvector('chinese', input_text))
    ORDER BY positions[1];
END;
$function$ LANGUAGE plpgsql;

COMMENT ON FUNCTION test_chinese_segmentation(TEXT) IS 
    '测试中文分词效果的函数，返回分词结果和位置';

-- ============================================================
-- 完成
-- ============================================================
COMMENT ON DATABASE inkflow IS 'InkFlow 2.0 - AI原生小说创作平台数据库';
