package com.hienao.openlist2strm.config;

import com.hienao.openlist2strm.constant.AppConstants;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置类
 *
 * @author hienao
 * @since 2024-01-01
 */
@Configuration
public class RestTemplateConfig {

  /**
   * 配置RestTemplate Bean
   *
   * @return RestTemplate实例
   */
  @Bean
  public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

    // 设置连接超时时间（毫秒）
    factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());

    // 设置读取超时时间（毫秒）
    factory.setReadTimeout((int) Duration.ofSeconds(60).toMillis());

    // 启用重定向跟随
    factory.setOutputStreaming(false);

    RestTemplate restTemplate = new RestTemplate(factory);

    // 添加 UTF-8 编码的 StringHttpMessageConverter，解决中文乱码问题
    StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
    stringConverter.setWriteAcceptCharset(false); // 避免在请求头中添加 Accept-Charset
    restTemplate.getMessageConverters().add(0, stringConverter);

    // 添加拦截器
    restTemplate.setInterceptors(getInterceptors());

    return restTemplate;
  }

  /**
   * 获取拦截器列表
   *
   * @return 拦截器列表
   */
  private List<ClientHttpRequestInterceptor> getInterceptors() {
    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();

    // 添加用户代理拦截器
    interceptors.add(
        (request, body, execution) -> {
          request.getHeaders().set("User-Agent", AppConstants.USER_AGENT);
          return execution.execute(request, body);
        });

    return interceptors;
  }
}
