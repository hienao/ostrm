package com.hienao.openlist2strm.exception;

import com.hienao.openlist2strm.notification.NotificationIssue;

/** 携带具体目录或文件重命名操作上下文的业务异常。 */
public class RenameOperationException extends BusinessException {

  private final NotificationIssue issue;

  public RenameOperationException(NotificationIssue issue) {
    super(issue.reason());
    this.issue = issue;
  }

  public RenameOperationException(NotificationIssue issue, Throwable cause) {
    super(issue.reason(), cause);
    this.issue = issue;
  }

  public NotificationIssue getIssue() {
    return issue;
  }

  public RenameOperationException withMedia(String title, Integer tmdbId) {
    return new RenameOperationException(
        NotificationIssue.builder()
            .category(issue.category())
            .reasonCode(issue.reasonCode())
            .scope(issue.scope())
            .sourcePath(issue.sourcePath())
            .targetName(issue.targetName())
            .mediaTitle(title)
            .tmdbId(tmdbId)
            .reason(issue.reason())
            .build(),
        this);
  }
}
