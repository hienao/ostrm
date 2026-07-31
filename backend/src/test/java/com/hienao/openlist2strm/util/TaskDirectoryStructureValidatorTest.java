package com.hienao.openlist2strm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hienao.openlist2strm.entity.MediaLibraryType;
import org.junit.jupiter.api.Test;

class TaskDirectoryStructureValidatorTest {

  @Test
  void calculatesRelativePathIncludingFileName() {
    assertEquals(
        "2001太空漫游 (1968) {tmdbid-62}/2001太空漫游 (1968) {tmdbid-62}.mkv",
        TaskDirectoryStructureValidator.calculateRelativePath(
            "/115/meida/movie",
            "/115/meida/movie/2001太空漫游 (1968) {tmdbid-62}/2001太空漫游 (1968) {tmdbid-62}.mkv"));
    assertEquals(
        "Panda.Plan.2026.mkv",
        TaskDirectoryStructureValidator.calculateRelativePath(
            "/115/meida/movie", "/115/meida/movie/Panda.Plan.2026.mkv"));
  }

  @Test
  void validatesMovieHierarchy() {
    assertFalse(validate("Inception (2010)/Inception.mkv", MediaLibraryType.MOVIE));
    assertTrue(validate("Inception.mkv", MediaLibraryType.MOVIE));
    assertTrue(validate("Inception (2010)/Extras/Inception.mkv", MediaLibraryType.MOVIE));
  }

  @Test
  void validatesTvHierarchyAndSeasonDirectory() {
    assertFalse(validate("Breaking Bad/Season 05/S05E14.mkv", MediaLibraryType.TV));
    assertFalse(validate("Breaking Bad/S05/S05E14.mkv", MediaLibraryType.TV));
    assertFalse(validate("Breaking Bad/第5季/S05E14.mkv", MediaLibraryType.TV));
    assertTrue(validate("Breaking Bad/S05E14.mkv", MediaLibraryType.TV));
    assertTrue(validate("Breaking Bad/全集/S05E14.mkv", MediaLibraryType.TV));
  }

  @Test
  void animeAllowsAbsoluteEpisodeFilesWithOrWithoutSeasonDirectory() {
    assertFalse(validate("海贼王/[1071].mkv", MediaLibraryType.ANIME));
    assertFalse(validate("海贼王/Season 01/[01].mkv", MediaLibraryType.ANIME));
    assertTrue(validate("[1071].mkv", MediaLibraryType.ANIME));
    assertTrue(validate("海贼王/全集/[01].mkv", MediaLibraryType.ANIME));
  }

  private boolean validate(String path, MediaLibraryType libraryType) {
    return TaskDirectoryStructureValidator.validate(path, libraryType).isPresent();
  }
}
