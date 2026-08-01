package com.hienao.openlist2strm.notification;

/** 已按渠道无关规则生成的标题和纯文本正文。 */
public record RenderedNotification(String title, String body, String type) {}
