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

package com.hienao.openlist2strm.controller;

import com.hienao.openlist2strm.config.security.Jwt;
import com.hienao.openlist2strm.dto.ApiResponse;
import com.hienao.openlist2strm.dto.sign.ChangePasswordDto;
import com.hienao.openlist2strm.dto.sign.SignInDto;
import com.hienao.openlist2strm.dto.sign.SignUpDto;
import com.hienao.openlist2strm.service.SignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户认证相关接口")
public class SignController {

  private final SignService signService;

  private final Jwt jwt;

  @ResponseStatus(HttpStatus.OK)
  @PostMapping("/sign-in")
  @Operation(summary = "用户登录", description = "用户登录接口，成功后返回JWT token")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "登录成功",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 200, \"message\": \"登录成功\", \"data\": {\"username\":"
                                    + " \"admin\", \"token\": \"eyJ...\", \"expiresAt\":"
                                    + " 1234567890}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "登录失败",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 400, \"message\": \"用户名或密码错误\", \"data\": null}")))
      })
  ApiResponse<Map<String, Object>> signIn(
      HttpServletRequest request,
      HttpServletResponse response,
      @RequestBody @Valid SignInDto signInDto) {
    String username = signService.signIn(signInDto);
    String token = jwt.makeToken(request, response, username);
    Map<String, Object> data = new HashMap<>();
    data.put("username", username);
    data.put("token", token);
    data.put("expiresAt", jwt.getExpiresAt(token));
    return ApiResponse.success(data, "登录成功");
  }

  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/sign-up")
  @Operation(summary = "用户注册", description = "用户注册接口")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "注册成功",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 200, \"message\": \"注册成功\", \"data\": null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "注册失败",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 400, \"message\": \"用户已存在\", \"data\": null}")))
      })
  ApiResponse<Void> signUp(@RequestBody @Valid SignUpDto signUpDto) {
    signService.signUp(signUpDto);
    return ApiResponse.success(null, "注册成功");
  }

  @ResponseStatus(HttpStatus.OK)
  @GetMapping("/check-user")
  @Operation(summary = "检查用户是否存在", description = "检查用户是否存在的接口")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "用户存在",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 200, \"message\": \"用户存在\", \"data\": {\"exists\":"
                                    + " true}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "用户不存在",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 404, \"message\": \"用户不存在\", \"data\": {\"exists\":"
                                    + " false}}")))
      })
  ApiResponse<Map<String, Object>> checkUser() {
    boolean exists = signService.checkUserExists();
    Map<String, Object> data = new HashMap<>();
    data.put("exists", exists);
    if (exists) {
      return ApiResponse.success(data, "用户存在");
    } else {
      // 创建一个包含data的ApiResponse对象
      return new ApiResponse<>(404, "用户不存在", data);
    }
  }

  @ResponseStatus(HttpStatus.OK)
  @PostMapping("/sign-out")
  @Operation(summary = "用户登出", description = "用户登出接口，需要JWT认证")
  @SecurityRequirement(name = "Bearer Authentication")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "登出成功",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 200, \"message\": \"登出成功\", \"data\": null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未授权",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 401, \"message\": \"未授权访问\", \"data\": null}")))
      })
  ApiResponse<Void> signOut(HttpServletRequest request, HttpServletResponse response) {
    jwt.removeToken(request, response);
    return ApiResponse.success(null, "登出成功");
  }

  @ResponseStatus(HttpStatus.OK)
  @PostMapping("/refresh")
  @Operation(summary = "刷新Token", description = "刷新JWT token接口，需要JWT认证")
  @SecurityRequirement(name = "Bearer Authentication")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token刷新成功",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 200, \"message\": \"Token刷新成功\", \"data\": {\"token\":"
                                    + " \"eyJ...\", \"expiresAt\": 1234567890}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Token无效",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 401, \"message\": \"Token无效，无法刷新\", \"data\": null}")))
      })
  ApiResponse<Map<String, Object>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    String oldToken = jwt.extract(request);
    if (oldToken != null && jwt.verify(oldToken)) {
      String username = jwt.getSubject(oldToken);
      String newToken = jwt.makeToken(request, response, username);
      Map<String, Object> data = new HashMap<>();
      data.put("token", newToken);
      data.put("expiresAt", jwt.getExpiresAt(newToken));
      return ApiResponse.success(data, "Token刷新成功");
    } else {
      return ApiResponse.error(401, "Token无效，无法刷新");
    }
  }

  @ResponseStatus(HttpStatus.OK)
  @GetMapping("/validate")
  @Operation(summary = "验证Token", description = "验证JWT token是否有效，需要JWT认证")
  @SecurityRequirement(name = "Bearer Authentication")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token有效",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 200, \"message\": \"Token有效\", \"data\": {\"valid\":"
                                    + " true, \"username\": \"user123\", \"expiresAt\":"
                                    + " 1234567890}}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Token无效",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value =
                                "{\"code\": 401, \"message\": \"Token无效\", \"data\": {\"valid\":"
                                    + " false}}")))
      })
  ApiResponse<Map<String, Object>> validate(HttpServletRequest request) {
    String token = jwt.extract(request);
    Map<String, Object> data = new HashMap<>();

    if (token != null && jwt.verify(token)) {
      String username = jwt.getSubject(token);
      data.put("valid", true);
      data.put("username", username);
      data.put("expiresAt", jwt.getExpiresAt(token));
      data.put("issuedAt", jwt.getIssuedAt(token));
      return ApiResponse.success(data, "Token有效");
    } else {
      data.put("valid", false);
      return ApiResponse.error(401, "Token无效");
    }
  }

  @ResponseStatus(HttpStatus.OK)
  @PostMapping("/change-password")
  @Operation(summary = "修改密码", description = "修改用户密码接口，需要JWT认证")
  @SecurityRequirement(name = "Bearer Authentication")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "密码修改成功",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 200, \"message\": \"密码修改成功\", \"data\": null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "密码修改失败",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 400, \"message\": \"原密码错误\", \"data\": null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未授权",
            content =
                @Content(
                    mediaType = "application/json",
                    examples =
                        @ExampleObject(
                            value = "{\"code\": 401, \"message\": \"未授权访问\", \"data\": null}")))
      })
  ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordDto changePasswordDto) {
    signService.changePassword(changePasswordDto);
    return ApiResponse.success(null, "密码修改成功");
  }
}
