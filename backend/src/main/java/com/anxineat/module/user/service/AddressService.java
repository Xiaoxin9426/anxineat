package com.anxineat.module.user.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.user.entity.Address;
import com.anxineat.module.user.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 收货地址服务。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressMapper addressMapper;

    public List<MemberDTO.AddressVO> list(Long userId) {
        return addressMapper.selectByUser(userId).stream().map(this::toVO).toList();
    }

    @Transactional
    public MemberDTO.AddressVO create(Long userId, RequestDTO.AddressRequest req) {
        Address a = new Address();
        a.setUserId(userId);
        a.setName(req.getName());
        a.setPhone(req.getPhone());
        a.setProvince(req.getProvince());
        a.setCity(req.getCity());
        a.setDistrict(req.getDistrict());
        a.setDetail(req.getDetail());
        a.setTag(req.getTag());
        a.setIsDefault(req.getIsDefault() == null ? 0 : req.getIsDefault());
        a.setStatus(1);
        if (a.getIsDefault() == 1) {
            addressMapper.clearDefault(userId);
        }
        addressMapper.insert(a);
        return toVO(a);
    }

    @Transactional
    public MemberDTO.AddressVO update(Long userId, Long id, RequestDTO.AddressRequest req) {
        Address a = owned(userId, id);
        a.setName(req.getName());
        a.setPhone(req.getPhone());
        a.setProvince(req.getProvince());
        a.setCity(req.getCity());
        a.setDistrict(req.getDistrict());
        a.setDetail(req.getDetail());
        a.setTag(req.getTag());
        if (req.getIsDefault() != null) {
            a.setIsDefault(req.getIsDefault());
            if (req.getIsDefault() == 1) {
                addressMapper.clearDefault(userId);
            }
        }
        addressMapper.updateById(a);
        return toVO(a);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Address a = owned(userId, id);
        a.setStatus(0);
        addressMapper.updateById(a);
    }

    @Transactional
    public void setDefault(Long userId, Long id) {
        owned(userId, id);
        addressMapper.clearDefault(userId);
        Address a = new Address();
        a.setId(id);
        a.setIsDefault(1);
        addressMapper.updateById(a);
    }

    private Address owned(Long userId, Long id) {
        Address a = addressMapper.selectById(id);
        if (a == null || !Objects.equals(a.getUserId(), userId) || Integer.valueOf(0).equals(a.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return a;
    }

    private MemberDTO.AddressVO toVO(Address a) {
        MemberDTO.AddressVO vo = new MemberDTO.AddressVO();
        vo.setId(a.getId());
        vo.setName(a.getName());
        vo.setPhone(a.getPhone());
        vo.setProvince(a.getProvince());
        vo.setCity(a.getCity());
        vo.setDistrict(a.getDistrict());
        vo.setDetail(a.getDetail());
        vo.setTag(a.getTag());
        vo.setIsDefault(a.getIsDefault() == null ? 0 : a.getIsDefault());
        return vo;
    }
}
