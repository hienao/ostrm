package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.config.PathConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SystemConfigServiceCacheTest {

  @TempDir Path temporaryDirectory;

  @Test
  void readsOnceAndAtomicallyReplacesSnapshotOnSave() throws Exception {
    PathConfiguration paths = new PathConfiguration();
    paths.setConfig(temporaryDirectory.toString());
    ObjectMapper objectMapper = new ObjectMapper();
    Files.writeString(
        temporaryDirectory.resolve("systemconf.json"),
        objectMapper.writeValueAsString(Map.of("scraping", Map.of("enabled", true))));
    SystemConfigService service = new SystemConfigService(objectMapper, paths);

    Map<String, Object> first = service.getSystemConfig();
    Files.writeString(
        temporaryDirectory.resolve("systemconf.json"),
        objectMapper.writeValueAsString(Map.of("scraping", Map.of("enabled", false))));

    assertEquals(first, service.getSystemConfig());
    assertThrows(UnsupportedOperationException.class, () -> first.put("unsafe", true));

    service.saveSystemConfig(Map.of("scraping", Map.of("enabled", false)));

    assertEquals(false, service.getScrapingConfig().get("enabled"));
  }
}
