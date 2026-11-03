package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public R<MemberDTO.LoginVO> login(@RequestBody RequestDTO.LoginRequest req) {
        return R.ok(userService.login(req));
    }
}
