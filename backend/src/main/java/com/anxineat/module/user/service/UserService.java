package com.anxineat.module.user.service;

import com.anxineat.common.BizException;
import com.anxineat.common.ResultCode;
import com.anxineat.dto.MemberDTO;
import com.anxineat.dto.RequestDTO;
import com.anxineat.module.point.entity.PointRecord;
import com.anxineat.module.point.mapper.PointRecordMapper;
import com.anxineat.module.point.service.PointService;
import com.anxineat.module.system.service.SysConfigService;
import com.anxineat.module.user.entity.MemberLevel;
import com.anxineat.module.user.entity.User;
import com.anxineat.module.user.mapper.MemberLevelMapper;
import com.anxineat.module.user.mapper.UserMapper;
import com.anxineat.security.JwtUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户与会员服务。
 *
 * <p>登录采用「先微信静默登录、后补绑手机号」的时序：open_id 与 phone 各自唯一、均可为空，
 * 通过 bind-phone 补齐。dev 模式下手机号验证码固定为 1234（无真实短信网关）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final MemberLevelMapper memberLevelMapper;
    private final PointRecordMapper pointRecordMapper;
    private final PointService pointService;
    private final JwtUtil jwtUtil;
    private final SysConfigService config;

    @Value("${anxineat.wx.miniapp.app-id:}")
    private String wxAppId;
    @Value("${anxineat.wx.miniapp.app-secret:}")
    private String wxAppSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 真实微信登录是否已配置（appid 与 appsecret 均非空）。
     * 通过 /api/config/client 的 wxRealLogin 暴露给小程序：未配置时前端自动回落 mock 登录，
     * 避免「静默 wx.login 成功但 code2Session 失败」导致的登录死循环。
     */
    public boolean isRealWxLoginEnabled() {
        return wxAppId != null && !wxAppId.isBlank()
                && wxAppSecret != null && !wxAppSecret.isBlank();
    }

    @Transactional
    public MemberDTO.LoginVO login(RequestDTO.LoginRequest req) {
        User user;
        if (req.getCode() != null && req.getCode().startsWith("mock:")) {
            // 本地联调：code 的 mock: 之后部分直接当 openid
            user = upsertByOpenId(req.getCode().substring("mock:".length()), req.getNickname(), req.getAvatar());
        } else if (req.getCode() != null && !req.getCode().isBlank()) {
            // 真实微信登录：code 换 openid
            if (wxAppId.isBlank() || wxAppSecret.isBlank()) {
                throw new BizException(ResultCode.BIZ_ERROR,
                        "微信小程序 appid/appsecret 未配置，无法使用真实登录（本地联调可用 mock: 前缀 code）");
            }
            String openId = code2Session(req.getCode());
            user = upsertByOpenId(openId, req.getNickname(), req.getAvatar());
        } else if (req.getPhone() != null && !req.getPhone().isBlank()) {
            verifyCode(req.getVerifyCode());
            user = userMapper.selectByPhone(req.getPhone());
            if (user == null) {
                user = new User();
                user.setPhone(req.getPhone());
                user.setNickname("用户" + req.getPhone().substring(Math.max(0, req.getPhone().length() - 4)));
                user.setMemberLevelId(lowestLevelId());
                user.setStatus(1);
                user.setPointBalance(0L);
                user.setPointTotal(0L);
                user.setTotalAmount(0L);
                userMapper.insert(user);
            }
        } else {
            throw new BizException(ResultCode.PARAM_ERROR, "登录参数缺失");
        }
        return buildLoginVO(user);
    }

    private User upsertByOpenId(String openId, String nickname, String avatar) {
        User user = userMapper.selectByOpenId(openId);
        if (user == null) {
            user = new User();
            user.setOpenId(openId);
            user.setNickname(nickname);
            user.setAvatar(avatar);
            user.setMemberLevelId(lowestLevelId());
            user.setStatus(1);
            user.setPointBalance(0L);
            user.setPointTotal(0L);
            user.setTotalAmount(0L);
            userMapper.insert(user);
        }
        return user;
    }

    private MemberDTO.LoginVO buildLoginVO(User user) {
        String token = jwtUtil.createUserToken(user.getId());
        MemberDTO.LoginVO vo = new MemberDTO.LoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        return vo;
    }

    /**
     * 调用微信 code2Session 用登录 code 换取 openid。
     * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
     */
    private String code2Session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wxAppId
                + "&secret=" + wxAppSecret + "&js_code=" + code + "&grant_type=authorization_code";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(java.time.Duration.ofSeconds(8)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BizException(ResultCode.BIZ_ERROR, "微信登录接口调用失败：" + resp.statusCode());
            }
            Map<?, ?> map = objectMapper.readValue(resp.body(), Map.class);
            Object errcode = map.get("errcode");
            if (errcode != null && !"0".equals(String.valueOf(errcode))) {
                throw new BizException(ResultCode.BIZ_ERROR, "微信登录失败：" + map.get("errmsg"));
            }
            Object openid = map.get("openid");
            if (openid == null) {
                throw new BizException(ResultCode.BIZ_ERROR, "微信登录未返回 openid");
            }
            return String.valueOf(openid);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("code2Session error, code={}", code, e);
            throw new BizException(ResultCode.BIZ_ERROR, "微信登录调用异常：" + e.getMessage());
        }
    }

    @Transactional
    public MemberDTO.UserVO bindPhone(Long userId, RequestDTO.BindPhoneRequest req) {
        verifyCode(req.getVerifyCode());
        User existing = userMapper.selectByPhone(req.getPhone());
        if (existing != null && !Objects.equals(existing.getId(), userId)) {
            throw new BizException(ResultCode.BIZ_ERROR, "该手机号已绑定其他账号");
        }
        User u = new User();
        u.setId(userId);
        u.setPhone(req.getPhone());
        userMapper.updateById(u);
        return toUserVO(userMapper.selectById(userId));
    }

    public MemberDTO.UserVO profile(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toUserVO(u);
    }

    @Transactional
    public MemberDTO.SigninVO signin(Long userId) {
        long awarded = pointService.signin(userId);
        MemberDTO.SigninVO vo = new MemberDTO.SigninVO();
        vo.setAwarded(awarded);
        vo.setBalance(pointService.balance(userId));
        vo.setDate(LocalDate.now());
        return vo;
    }

    public IPage<MemberDTO.PointRecordVO> pointRecords(Long userId, int page, int size) {
        IPage<PointRecord> p = pointRecordMapper.selectUserRecords(new Page<>(page, size), userId);
        List<MemberDTO.PointRecordVO> list = p.getRecords().stream().map(r -> {
            MemberDTO.PointRecordVO v = new MemberDTO.PointRecordVO();
            v.setId(r.getId());
            v.setType(r.getType());
            v.setTypeLabel(PointRecord.label(r.getType()));
            v.setAmount(r.getAmount());
            v.setBalanceAfter(r.getBalanceAfter());
            v.setRemark(r.getRemark());
            v.setCreatedAt(r.getCreatedAt());
            return v;
        }).toList();
        Page<MemberDTO.PointRecordVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(list);
        return vo;
    }

    // ==================== 内部 ====================

    private void verifyCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "请输入验证码");
        }
        if (config.getBool("dev.mode") || "MOCK".equalsIgnoreCase(config.get("pay.channel", "MOCK"))) {
            if (!"1234".equals(code)) {
                throw new BizException(ResultCode.PARAM_ERROR, "验证码错误（dev 模式固定为 1234）");
            }
            return;
        }
        throw new BizException(ResultCode.BIZ_ERROR, "当前环境未接入短信网关，无法校验验证码");
    }

    private Long lowestLevelId() {
        List<MemberLevel> all = memberLevelMapper.selectAllAsc();
        return all.isEmpty() ? null : all.get(0).getId();
    }

    private MemberLevel levelOf(Long levelId) {
        if (levelId != null) {
            MemberLevel ml = memberLevelMapper.selectById(levelId);
            if (ml != null) {
                return ml;
            }
        }
        List<MemberLevel> all = memberLevelMapper.selectAllAsc();
        return all.isEmpty() ? null : all.get(0);
    }

    public MemberDTO.UserVO toUserVO(User u) {
        MemberDTO.UserVO vo = new MemberDTO.UserVO();
        vo.setId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setPhone(u.getPhone());
        vo.setMemberLevelId(u.getMemberLevelId());
        vo.setPointBalance(u.getPointBalance() == null ? 0L : u.getPointBalance());
        vo.setTotalAmount(u.getTotalAmount() == null ? 0L : u.getTotalAmount());

        MemberLevel level = levelOf(u.getMemberLevelId());
        vo.setLevelName(level == null ? "普通会员" : level.getName());
        vo.setLevelDiscount(level == null || level.getDiscount() == null ? 10000 : level.getDiscount());
        vo.setPointRate(level == null || level.getPointRate() == null ? 100 : level.getPointRate());

        long total = u.getTotalAmount() == null ? 0L : u.getTotalAmount();
        List<MemberLevel> higher = memberLevelMapper.selectAllAsc().stream()
                .filter(l -> l.getMinAmount() != null && l.getMinAmount() > total)
                .sorted(java.util.Comparator.comparing(MemberLevel::getMinAmount))
                .toList();
        if (higher.isEmpty()) {
            vo.setNextLevelName("");
            vo.setNextLevelMinAmount(0L);
            vo.setToNextLevelAmount(0L);
        } else {
            MemberLevel next = higher.get(0);
            vo.setNextLevelName(next.getName());
            vo.setNextLevelMinAmount(next.getMinAmount());
            vo.setToNextLevelAmount(Math.max(0, next.getMinAmount() - total));
        }
        return vo;
    }
}
