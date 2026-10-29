package com.campus.signup.exception;

import com.campus.signup.dto.auth.RegisterRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器的行为验证。用 standalone MockMvc，不启动 Spring 上下文、不连数据库，
 * 顺带把「未知异常不能把内部信息泄露给前端」这条也钉成断言。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException：HTTP 状态与业务码都取自 ErrorCode")
    void businessException() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("参数校验失败：返回 400 + 1001，且提示里带字段名")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/probe/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message", containsString("username")))
                .andExpect(jsonPath("$.message", containsString("password")));
    }

    @Test
    @DisplayName("请求体不是合法 JSON：返回 400 而不是 500")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/probe/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("未知异常：返回 500 + 通用文案，不泄露内部信息")
    void unknownException() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1500))
                .andExpect(jsonPath("$.message", not(containsString("数据库连接字符串泄露风险"))))
                .andExpect(jsonPath("$.message", not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("请求方法不支持：返回 405 + 1007")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/probe/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(1007));
    }

    // ------------------------------------------------------------------

    @RestController
    static class ProbeController {

        @GetMapping("/probe/business")
        String business() {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("数据库连接字符串泄露风险");
        }

        @PostMapping("/probe/valid")
        String valid(@Valid @RequestBody RegisterRequest request) {
            return "ok";
        }
    }
}
