package com.hienao.openlist2strm.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LogReadResponse {
  private List<String> lines;
  private long cursor;
  private String fileKey;
  private boolean reset;
  private boolean hasMore;
}
