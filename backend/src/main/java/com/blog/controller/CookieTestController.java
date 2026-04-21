package com.blog.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CookieTestController {

  @GetMapping("/set-cookie-test")
  public ResponseEntity<String> setCookieTest() {
    ResponseCookie cookie = ResponseCookie.from("test_cookie", "test_value")
        .path("/")
        .httpOnly(true)
        .sameSite("Lax")
        .build();

    return ResponseEntity.ok()
        .header("Set-Cookie", cookie.toString())
        .body("ok");
  }
}
