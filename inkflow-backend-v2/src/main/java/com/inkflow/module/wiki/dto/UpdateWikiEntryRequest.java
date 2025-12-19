package com.inkflow.module.wiki.dto;

/**
 * 更新知识条目请求
 */
public record UpdateWikiEntryRequest(
    String title,
    String type,
    String content,
    String[] aliases,
    String[] tags,
    String timeVersion
) {}
