package com.hienao.openlist2strm.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.service.OpenlistApiService;
import org.junit.jupiter.api.Test;

class SingleFileHandlerSkipTest {

  @Test
  void fileDiscoverySkipsRemoteTraversalForSingleFileContext() {
    OpenlistApiService openlistApiService = mock(OpenlistApiService.class);
    FileDiscoveryHandler handler = new FileDiscoveryHandler(openlistApiService);

    ProcessingResult result = handler.process(singleFileContext());

    assertEquals(ProcessingResult.SUCCESS, result);
    verifyNoInteractions(openlistApiService);
  }

  @Test
  void orphanCleanupSkipsDirectoryTraversalForSingleFileContext() {
    OpenlistApiService openlistApiService = mock(OpenlistApiService.class);
    OrphanCleanupHandler handler = new OrphanCleanupHandler(openlistApiService);

    ProcessingResult result = handler.process(singleFileContext());

    assertEquals(ProcessingResult.SKIPPED, result);
    verifyNoInteractions(openlistApiService);
  }

  private FileProcessingContext singleFileContext() {
    OpenlistApiService.OpenlistFile currentFile = new OpenlistApiService.OpenlistFile();
    currentFile.setPath("/movies/example.mkv");
    return FileProcessingContext.builder().currentFile(currentFile).build();
  }
}
