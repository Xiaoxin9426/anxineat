package com.anxineat.controller;

import com.anxineat.common.R;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.user.service.AddressService;
import com.anxineat.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public R<List<MemberDTO.AddressVO>> list() {
        return R.ok(addressService.list(AuthContext.requireUserId()));
    }

    @PostMapping
    public R<MemberDTO.AddressVO> create(@RequestBody RequestDTO.AddressRequest req) {
        return R.ok(addressService.create(AuthContext.requireUserId(), req));
    }

    @PutMapping("/{id}")
    public R<MemberDTO.AddressVO> update(@PathVariable Long id, @RequestBody RequestDTO.AddressRequest req) {
        return R.ok(addressService.update(AuthContext.requireUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        addressService.delete(AuthContext.requireUserId(), id);
        return R.ok();
    }

    @PostMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(AuthContext.requireUserId(), id);
        return R.ok();
    }
}
