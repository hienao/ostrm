package com.hienao.openlist2strm.constant;

/**
 * 应用程序常量定义
 *
 * @author hienao
 * @since 2024-01-01
 */
public final class AppConstants {

  private AppConstants() {
    // 私有构造函数，防止实例化
  }

  /** 应用名称 */
  public static final String APP_NAME = "OStrm";

  /** 应用版本 */
  public static final String APP_VERSION = "1.0";

  /** HTTP User-Agent 请求头值 */
  public static final String USER_AGENT = APP_NAME + "/" + APP_VERSION;
}
