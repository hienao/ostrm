/*
 * OStrm - Stream Management System
 * Copyright (C) 2024 OStrm Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.mapper.OpenlistConfigMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * openlist配置服务类
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenlistConfigService {

  private static final String CONFIG_ID_NULL_ERROR = "配置ID不能为空";

  private final OpenlistConfigMapper openlistConfigMapper;

  /**
   * 根据ID查询配置
   *
   * @param id 主键ID
   * @return 配置信息
   */
  public OpenlistConfig getById(Long id) {
    if (id == null) {
      throw new BusinessException(CONFIG_ID_NULL_ERROR);
    }
    OpenlistConfig config = openlistConfigMapper.selectById(id);
    if (config != null) {
      log.info("获取OpenList配置 - ID: {}, strmBaseUrl: '{}'", config.getId(), config.getStrmBaseUrl());

      // 测试：打印所有字段值以确认数据库映射正确
      if (config.getId() != null && config.getId() == 1L) { // 只对第一个配置打印详细信息
        log.info("配置详细信息 - ID: {}, baseUrl: {}, token: [已隐藏], basePath: {}, username: {}, isActive: {}, strmBaseUrl: '{}'",
                 config.getId(), config.getBaseUrl(), config.getBasePath(), config.getUsername(), config.getIsActive(), config.getStrmBaseUrl());
      }
    }
    return config;
  }

  /**
   * 根据用户名查询配置
   *
   * @param username 用户名
   * @return 配置信息
   */
  public OpenlistConfig getByUsername(String username) {
    if (!StringUtils.hasText(username)) {
      throw new BusinessException("用户名不能为空");
    }
    return openlistConfigMapper.selectByUsername(username);
  }

  /**
   * 查询所有启用的配置
   *
   * @return 配置列表
   */
  public List<OpenlistConfig> getActiveConfigs() {
    return openlistConfigMapper.selectActiveConfigs();
  }

  /**
   * 查询所有配置
   *
   * @return 配置列表
   */
  public List<OpenlistConfig> getAllConfigs() {
    return openlistConfigMapper.selectAll();
  }

  /**
   * 创建配置
   *
   * @param config 配置信息
   * @return 创建的配置
   */
  @Transactional(rollbackFor = Exception.class)
  public OpenlistConfig createConfig(OpenlistConfig config) {
    validateConfig(config);

    // 检查用户名是否已存在
    OpenlistConfig existingConfig = openlistConfigMapper.selectByUsername(config.getUsername());
    if (existingConfig != null) {
      throw new BusinessException("用户名已存在: " + config.getUsername());
    }

    // 设置默认值
    if (config.getBasePath() == null) {
      config.setBasePath("/");
    }
    if (config.getIsActive() == null) {
      config.setIsActive(true);
    }

    log.info("创建OpenList配置 - strmBaseUrl: '{}'", config.getStrmBaseUrl());
    int result = openlistConfigMapper.insert(config);
    if (result <= 0) {
      throw new BusinessException("创建配置失败");
    }

    log.info("创建openlist配置成功，用户名: {}, ID: {}", config.getUsername(), config.getId());
    return config;
  }

  /**
   * 更新配置
   *
   * @param config 配置信息
   * @return 更新的配置
   */
  @Transactional(rollbackFor = Exception.class)
  public OpenlistConfig updateConfig(OpenlistConfig config) {
    if (config.getId() == null) {
      throw new BusinessException(CONFIG_ID_NULL_ERROR);
    }

    // 检查配置是否存在
    OpenlistConfig existingConfig = openlistConfigMapper.selectById(config.getId());
    if (existingConfig == null) {
      throw new BusinessException("配置不存在，ID: " + config.getId());
    }

    validateConfig(config);

    // 记录更新前的配置信息
    log.info("更新OpenList配置 - ID: {}, 原strmBaseUrl: '{}', 新strmBaseUrl: '{}'",
             config.getId(), existingConfig.getStrmBaseUrl(), config.getStrmBaseUrl());

    // 如果更新了用户名，检查是否与其他配置冲突
    if (StringUtils.hasText(config.getUsername())
        && !config.getUsername().equals(existingConfig.getUsername())) {
      OpenlistConfig conflictConfig = openlistConfigMapper.selectByUsername(config.getUsername());
      if (conflictConfig != null && !conflictConfig.getId().equals(config.getId())) {
        throw new BusinessException("用户名已存在: " + config.getUsername());
      }
    }

    int result = openlistConfigMapper.updateById(config);
    if (result <= 0) {
      throw new BusinessException("更新配置失败");
    }

    log.info("更新openlist配置成功，ID: {}", config.getId());
    return openlistConfigMapper.selectById(config.getId());
  }

  /**
   * 删除配置
   *
   * @param id 配置ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteConfig(Long id) {
    if (id == null) {
      throw new BusinessException(CONFIG_ID_NULL_ERROR);
    }

    OpenlistConfig existingConfig = openlistConfigMapper.selectById(id);
    if (existingConfig == null) {
      throw new BusinessException("配置不存在，ID: " + id);
    }

    int result = openlistConfigMapper.deleteById(id);
    if (result <= 0) {
      throw new BusinessException("删除配置失败");
    }

    log.info("删除openlist配置成功，ID: {}", id);
  }

  /**
   * 启用/禁用配置
   *
   * @param id 配置ID
   * @param isActive 是否启用
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateActiveStatus(Long id, Boolean isActive) {
    if (id == null) {
      throw new BusinessException(CONFIG_ID_NULL_ERROR);
    }
    if (isActive == null) {
      throw new BusinessException("启用状态不能为空");
    }

    OpenlistConfig existingConfig = openlistConfigMapper.selectById(id);
    if (existingConfig == null) {
      throw new BusinessException("配置不存在，ID: " + id);
    }

    int result = openlistConfigMapper.updateActiveStatus(id, isActive);
    if (result <= 0) {
      throw new BusinessException("更新配置状态失败");
    }

    log.info("更新openlist配置状态成功，ID: {}, 状态: {}", id, isActive ? "启用" : "禁用");
  }

  /**
   * 验证配置参数
   *
   * @param config 配置信息
   */
  private void validateConfig(OpenlistConfig config) {
    if (config == null) {
      throw new BusinessException("配置信息不能为空");
    }
    if (!StringUtils.hasText(config.getBaseUrl())) {
      throw new BusinessException("openlist网址不能为空");
    }
    if (!StringUtils.hasText(config.getToken())) {
      throw new BusinessException("用户令牌不能为空");
    }
    if (!StringUtils.hasText(config.getUsername())) {
      throw new BusinessException("用户名不能为空");
    }

    // 验证URL格式
    if (!config.getBaseUrl().startsWith("http://") && !config.getBaseUrl().startsWith("https://")) {
      throw new BusinessException("openlist网址格式不正确，必须以http://或https://开头");
    }
  }
}
