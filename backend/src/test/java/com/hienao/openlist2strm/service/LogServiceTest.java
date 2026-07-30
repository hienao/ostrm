package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hienao.openlist2strm.dto.LogReadResponse;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class LogServiceTest {

  @TempDir Path tempDir;

  private LogService logService;

  @BeforeEach
  void setUp() {
    logService = new LogService();
    ReflectionTestUtils.setField(logService, "logPath", tempDir.toString());
  }

  @Test
  void clearKeepsAnAlreadyOpenWriterUsable() throws Exception {
    Path logFile = tempDir.resolve("backend.log");

    try (OutputStream writer = new FileOutputStream(logFile.toFile(), true)) {
      writer.write("before\n".getBytes(StandardCharsets.UTF_8));
      writer.flush();

      logService.clearLogFile("backend");
      writer.write("after\n".getBytes(StandardCharsets.UTF_8));
      writer.flush();
    }

    assertEquals("after\n", Files.readString(logFile));
  }

  @Test
  void readsInitialTailThenOnlyAppendedLines() throws Exception {
    Path logFile = tempDir.resolve("backend.log");
    Files.writeString(logFile, "one\ntwo\nthree\n");

    LogReadResponse initial = logService.readLogChunk("backend", null, null, 2);

    assertEquals(java.util.List.of("two", "three"), initial.getLines());
    assertFalse(initial.isReset());

    Files.writeString(logFile, "four\nfive\n", StandardOpenOption.APPEND);
    LogReadResponse incremental =
        logService.readLogChunk("backend", initial.getCursor(), initial.getFileKey(), 100);

    assertEquals(java.util.List.of("four", "five"), incremental.getLines());
    assertFalse(incremental.isReset());
    assertFalse(incremental.isHasMore());
  }

  @Test
  void resetsCursorAfterLogIsCleared() throws Exception {
    Path logFile = tempDir.resolve("backend.log");
    Files.writeString(logFile, "before\n");
    LogReadResponse initial = logService.readLogChunk("backend", null, null, 100);

    logService.clearLogFile("backend");
    Files.writeString(logFile, "after-one\nafter-two\n", StandardOpenOption.APPEND);
    LogReadResponse response =
        logService.readLogChunk("backend", initial.getCursor(), initial.getFileKey(), 100);

    assertTrue(response.isReset());
    assertEquals(java.util.List.of("after-one", "after-two"), response.getLines());
  }

  @Test
  void rejectsUnboundedLineRequests() {
    assertThrows(
        IllegalArgumentException.class, () -> logService.readLogChunk("backend", null, null, 5001));
    assertThrows(IllegalArgumentException.class, () -> logService.getLogLines("backend", 0));
  }
}
