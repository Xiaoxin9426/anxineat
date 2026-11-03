package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.OrderDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.point.entity.PointRecord;
import com.anxineat.module.user.service.UserService;
import com.anxineat.security.AuthContext;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public R<MemberDTO.UserVO> profile() {
        return R.ok(userService.profile(AuthContext.requireUserId()));
    }

    @PostMapping("/bind-phone")
    public R<MemberDTO.UserVO> bindPhone(@RequestBody RequestDTO.BindPhoneRequest req) {
        return R.ok(userService.bindPhone(AuthContext.requireUserId(), req));
    }

    @GetMapping("/point/records")
    public R<IPage<MemberDTO.PointRecordVO>> pointRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(userService.pointRecords(AuthContext.requireUserId(), page, size));
    }

    @PostMapping("/signin")
    public R<MemberDTO.SigninVO> signin() {
        return R.ok(userService.signin(AuthContext.requireUserId()));
    }
}
