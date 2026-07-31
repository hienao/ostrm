package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.config.PathConfiguration;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskManifestServiceTest {

  @TempDir Path temporaryDirectory;

  @Test
  void detectsOnlyDirectoriesWhoseEntriesChanged() {
    PathConfiguration paths = new PathConfiguration();
    paths.setData(temporaryDirectory.toString());
    TaskManifestService service = new TaskManifestService(new ObjectMapper(), paths);

    List<OpenlistApiService.OpenlistFile> initial =
        List.of(
            file("/media/movie/a.mkv", 10L, "2026-01-01"),
            file("/media/movie/a.srt", 2L, "2026-01-01"),
            file("/media/other/b.mkv", 20L, "2026-01-01"));

    assertTrue(service.detectChanges(1L, "/media", "config-a", initial).firstRun());
    service.save(1L, "/media", "config-a", initial);

    TaskManifestService.ChangeSet unchanged =
        service.detectChanges(1L, "/media", "config-a", initial);
    assertFalse(unchanged.firstRun());
    assertTrue(unchanged.changedDirectories().isEmpty());

    List<OpenlistApiService.OpenlistFile> changed =
        List.of(
            file("/media/movie/a.mkv", 11L, "2026-01-02"),
            file("/media/movie/a.srt", 2L, "2026-01-01"),
            file("/media/other/b.mkv", 20L, "2026-01-01"));
    TaskManifestService.ChangeSet changeSet =
        service.detectChanges(1L, "/media", "config-a", changed);

    assertTrue(changeSet.changedDirectories().contains("/media/movie"));
    assertFalse(changeSet.changedDirectories().contains("/media/other"));
  }

  @Test
  void configurationChangeForcesFullProcessing() {
    PathConfiguration paths = new PathConfiguration();
    paths.setData(temporaryDirectory.toString());
    TaskManifestService service = new TaskManifestService(new ObjectMapper(), paths);
    List<OpenlistApiService.OpenlistFile> files =
        List.of(file("/media/movie/a.mkv", 10L, "2026-01-01"));
    service.save(2L, "/media", "config-a", files);

    TaskManifestService.ChangeSet changeSet =
        service.detectChanges(2L, "/media", "config-b", files);

    assertTrue(changeSet.firstRun());
    assertTrue(changeSet.changedDirectories().contains("/media/movie"));
  }

  private OpenlistApiService.OpenlistFile file(String path, long size, String modified) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setPath(path);
    file.setName(path.substring(path.lastIndexOf('/') + 1));
    file.setType("file");
    file.setSize(size);
    file.setModified(modified);
    file.setSign("sign");
    return file;
  }
}
