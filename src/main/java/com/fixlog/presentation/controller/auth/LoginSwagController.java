package com.fixlog.presentation.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/login/swag")
public class LoginSwagController {

    public static final String SWAG_LOGIN_FLAG = "swag_login";

    @GetMapping
    public void loginSwag(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.getSession().setAttribute(SWAG_LOGIN_FLAG, true);
        response.sendRedirect("/fixlog/oauth2/authorization/google");
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam String accessToken, @RequestParam String refreshToken) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <title>Swagger 토큰 인증</title>
                  <style>
                    body { font-family: sans-serif; max-width: 600px; margin: 60px auto; padding: 0 20px; }
                    .token-box { background: #f4f4f4; border-radius: 8px; padding: 16px; word-break: break-all; font-size: 13px; }
                    button { margin-top: 12px; padding: 10px 20px; font-size: 14px; cursor: pointer; border: none; border-radius: 6px; }
                    .btn-copy { background: #4CAF50; color: white; }
                    .btn-swag { background: #1b85dc; color: white; margin-left: 8px; }
                    .status { margin-top: 10px; color: green; font-size: 13px; display: none; }
                  </style>
                </head>
                <body>
                  <h2>✅ 로그인 성공</h2>
                  <p><strong>Access Token</strong></p>
                  <div class="token-box" id="tokenBox">%s</div>
                  <br>
                  <button class="btn-copy" onclick="copyToken()">토큰 복사</button>
                  <button class="btn-swag" onclick="goSwagger()">Swagger로 이동</button>
                  <div class="status" id="status">✔ 복사되었습니다!</div>
                  <script>
                    const accessToken = "%s";
                    // localStorage에 Swagger 인증 정보 저장 시도
                    try {
                      const authData = JSON.stringify({
                        "Bearer Authentication": {
                          "name": "Bearer Authentication",
                          "schema": { "type": "http", "scheme": "bearer", "bearerFormat": "JWT" },
                          "value": accessToken
                        }
                      });
                      localStorage.setItem("authorized", authData);
                    } catch(e) {}

                    function copyToken() {
                      navigator.clipboard.writeText(accessToken).then(() => {
                        document.getElementById("status").style.display = "block";
                      });
                    }

                    function goSwagger() {
                      window.location.href = "/fixlog/swagger-ui.html";
                    }
                  </script>
                </body>
                </html>
                """.formatted(accessToken, accessToken);
    }
}
