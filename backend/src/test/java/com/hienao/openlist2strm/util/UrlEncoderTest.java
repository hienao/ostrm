package com.hienao.openlist2strm.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class UrlEncoderTest {

  @Test
  void encodesOpenlistDownloadPathAndPreservesSignedQuery() {
    String rawUrl =
        "http://192.168.88.229:5244/d/115/meida/movie/"
            + "临时劫案 (2024) {tmdbid-991197}/临时劫案 (2024) {tmdbid-991197}.nfo"
            + "?sign=vhDa_M9JsSD5r8kTtZZ9AJFQzKun391omDM-V_EEmPo=:0";

    String encodedUrl = UrlEncoder.encodeUrlSmart(rawUrl);

    assertEquals(
        "http://192.168.88.229:5244/d/115/meida/movie/"
            + "%E4%B8%B4%E6%97%B6%E5%8A%AB%E6%A1%88%20(2024)%20%7Btmdbid-991197%7D/"
            + "%E4%B8%B4%E6%97%B6%E5%8A%AB%E6%A1%88%20(2024)%20%7Btmdbid-991197%7D.nfo"
            + "?sign=vhDa_M9JsSD5r8kTtZZ9AJFQzKun391omDM-V_EEmPo%3D%3A0",
        encodedUrl);
    assertDoesNotThrow(() -> URI.create(encodedUrl));
  }

  @Test
  void encodingIsIdempotentForAlreadyEncodedUrls() {
    String encodedUrl =
        "https://openlist.example.com/d/%E7%94%B5%E5%BD%B1/"
            + "%E6%B5%B7%E6%8A%A5%20poster%20%7Btmdbid-1%7D.jpg"
            + "?sign=abc_def%3D%3A0";

    assertEquals(encodedUrl, UrlEncoder.encodeUrlSmart(encodedUrl));
  }
}
