package com.ae.ae_SpringServer.api;

import com.ae.ae_SpringServer.component.CommunityDiscoveryClient;
import com.ae.ae_SpringServer.config.BaseResponse;
import com.ae.ae_SpringServer.config.security.JwtProvider;
import com.ae.ae_SpringServer.domain.User;
import com.ae.ae_SpringServer.dto.request.SignupRequestDto;
import com.ae.ae_SpringServer.dto.request.UserNicknameRequestDto;
import com.ae.ae_SpringServer.dto.request.UserSocialLoginRequestDto;
import com.ae.ae_SpringServer.dto.request.UserUpdateRequestDto;
import com.ae.ae_SpringServer.dto.request.v3.SignupRequestDtoV3;
import com.ae.ae_SpringServer.dto.request.v3.UserInfoResponseDtoV3;
import com.ae.ae_SpringServer.dto.request.v3.UserUpdateRequestDtoV3;
import com.ae.ae_SpringServer.dto.response.*;
import com.ae.ae_SpringServer.event.RabbitMQProducer;
import com.ae.ae_SpringServer.service.UserService;
import com.nimbusds.jose.shaded.json.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;

import static com.ae.ae_SpringServer.config.BaseResponseStatus.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chaebbi/user")
public class UserApiController {
    private final UserService userService;
    private final CommunityDiscoveryClient client;
    private final JwtProvider jwtProvider;

    private final RabbitMQProducer producer;

    //[POST] 4-1 카카오 로그인
    // 로그인 시에, kakaoprofile로 받은 정보가 db에 있으면 jwt 토큰 발급(status코드는 온보딩 안띄우게). db에 없으면 new user로 저장시키고 jwt 토큰발급(온보딩 띄우게)
    @PostMapping("/kakao-login")
    public BaseResponse<LoginResponseDto> loginByKakao(
            @RequestBody UserSocialLoginRequestDto socialLoginRequestDto) {
        String token = socialLoginRequestDto.getAccessToken();

        RestTemplate restTemplate = new RestTemplate();
        URI uri = URI.create("https://kapi.kakao.com/v2/user/me");
        HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        MultiValueMap<String, Object> pa = new LinkedMultiValueMap<>();
        HttpEntity<MultiValueMap<String, Object>> restRequest = new HttpEntity<>(pa, headers);
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            public boolean hasError(ClientHttpResponse response) throws IOException {
                HttpStatus statusCode = response.getStatusCode();
                return statusCode.series() == HttpStatus.Series.SERVER_ERROR;
            }
        });
        ResponseEntity<JSONObject> apiResponse = restTemplate.postForEntity(uri, restRequest, JSONObject.class);
        JSONObject responseBody = apiResponse.getBody();

        String id = String.valueOf(responseBody.get("id"));

        Optional<User> user = userService.findByKakaoId(id);
        boolean isKakaoEmpty = user.isEmpty();

        if(!isKakaoEmpty) {
            // 로그인 후 온보딩이 아직 입력 안된 상태
            if(user.get().getHeight() == null || user.get().getWeight() == null) {
                return new BaseResponse<>(new LoginResponseDto(user.get().getId(), jwtProvider.createToken(user.get()), true));
            }
            return new BaseResponse<>(new LoginResponseDto(user.get().getId(), jwtProvider.createToken(user.get()), false));
        } else {
            User u = User.createUser(id);
            userService.create(u);
            return new BaseResponse<>(new LoginResponseDto(u.getId(), jwtProvider.createToken(u), true));
        }
    }

    //[POST] 4-2 : 애플로그인 api
    @PostMapping("/apple-login")
    public BaseResponse<LoginResponseDto> loginByApple(@RequestBody UserSocialLoginRequestDto socialLoginRequestDto){
        return new BaseResponse<>(userService.login(socialLoginRequestDto));

    }

    // [POST] 3-3  회원 등록 (version 3 )
    @PostMapping("/signup")
    public BaseResponse<UserResponseDto> signup(@AuthenticationPrincipal HashMap<String,String> user, @RequestBody SignupRequestDtoV3 signupRequestDto) {
        String userId = user.get("userIdx");
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User jwtUser = userService.findOne(Long.valueOf(userId));
        if (jwtUser == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        if(signupRequestDto.getNickname().isEmpty() || signupRequestDto.getNickname().equals("")) {
            return new BaseResponse<>(POST_USER_NO_NAME);
        }
        if(signupRequestDto.getNickname().length() > 45) {
            return new BaseResponse<>(POST_USER_LONG_NAME);
        }
        if(signupRequestDto.getAge() < 1) {
            return new BaseResponse<>(POST_USER_MINUS_AGE);
        }
        if(signupRequestDto.getGender() != 0 && signupRequestDto.getGender() != 1) {
            return new BaseResponse<>(POST_USER_INVALID_GENDER);
        }

        if(signupRequestDto.getHeight().isEmpty() || signupRequestDto.getHeight().equals("")) {
            return new BaseResponse<>(POST_USER_NO_HEIGHT);
        }

        if(Integer.parseInt(signupRequestDto.getHeight()) < 0) {
            return new BaseResponse<>(POST_USER_MINUS_HEIGHT);
        }

        if(signupRequestDto.getWeight().isEmpty() || signupRequestDto.getWeight().equals("")) {
            return new BaseResponse<>(POST_USER_NO_WEIGHT);
        }

        if(Integer.parseInt(signupRequestDto.getWeight()) < 0) {
            return new BaseResponse<>(POST_USER_MINUS_WEIGHT);
        }
        if(signupRequestDto.getActivity() != 25 && signupRequestDto.getActivity() != 33 && Integer.valueOf(signupRequestDto.getActivity()) != 40) {
            return new BaseResponse<>(POST_USER_INVALID_ACTIVITY);
        }
        int icon = userService.signupNickname(Long.valueOf(userId), signupRequestDto);
        User u = userService.findOne(Long.valueOf(userId));
        return new BaseResponse<>(
                new UserResponseDto(userId, jwtProvider.createTokenNewNickname(
                        u, signupRequestDto.getNickname(), String.valueOf(icon))));
    }

    // [GET] 3-1 회원 정보 조회 for version3
    @GetMapping("/userinfo")
    public BaseResponse<UserInfoResponseDtoV3> info(@AuthenticationPrincipal HashMap<String,String> user) {
        String userId = user.get("userIdx");
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User jwtUser = userService.findOne(Long.valueOf(userId));
        if (jwtUser == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        return new BaseResponse<>(
                new UserInfoResponseDtoV3(
                        jwtUser.getNickname(), jwtUser.getGender(), jwtUser.getAge(), jwtUser.getHeight(), jwtUser.getWeight(),
                        jwtUser.getIcon(), jwtUser.getActivity()));

    }

    // [PUT] 3-2 회원 정보 수정 for version3
    @PutMapping("/info-update")
    public BaseResponse<UserResponseDto>  update(@AuthenticationPrincipal HashMap<String,String> user,
                                                 @RequestBody UserUpdateRequestDtoV3 userUpdateRequestDto) {
        String userId = user.get("userIdx");
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User jwtUser = userService.findOne(Long.valueOf(user.get("userIdx")));
        if (jwtUser == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userUpdateRequestDto.getAge() < 1) {
            return new BaseResponse<>(PUT_USER_MINUS_AGE);
        }

        if(userUpdateRequestDto.getHeight().isEmpty() || userUpdateRequestDto.getHeight().equals("")) {
            return new BaseResponse<>(PUT_USER_NO_HEIGHT);
        }

        if(Integer.parseInt(userUpdateRequestDto.getHeight()) < 0) {
            return new BaseResponse<>(PUT_USER_MINUS_HEIGHT);
        }

        if(userUpdateRequestDto.getWeight().isEmpty() || userUpdateRequestDto.getWeight().equals("")) {
            return new BaseResponse<>(PUT_USER_NO_WEIGHT);
        }

        if(Integer.parseInt(userUpdateRequestDto.getWeight()) < 0) {
            return new BaseResponse<>(PUT_USER_MINUS_WEIGHT);
        }
        if(Integer.valueOf(userUpdateRequestDto.getActivity()) != 25 && Integer.valueOf(userUpdateRequestDto.getActivity()) != 33 && Integer.valueOf(userUpdateRequestDto.getActivity()) != 40) {
            return new BaseResponse<>(PUT_USER_INVALID_ACTIVITY);
        }

        client.updateNickname(userId, userUpdateRequestDto.getNickname());

        User u =userService.updateV3(Long.valueOf(userId), userUpdateRequestDto);
        return new BaseResponse<>(
                new UserResponseDto(userId, jwtProvider.createTokenNewNickname(
                        u, userUpdateRequestDto.getNickname(), String.valueOf(u.getIcon()))));
    }

    // [DELETE] 3-4 회원 탈퇴
    @DeleteMapping
    public BaseResponse<String> deleteUser(@AuthenticationPrincipal HashMap<String,String> user) {
        String userId = user.get("userIdx");
        if(userId.equals("INVALID JWT")){
            return new BaseResponse<>(INVALID_JWT);
        }
        if(userId == null) {
            return new BaseResponse<>(EMPTY_JWT);
        }
        User jwtUser = userService.findOne(Long.valueOf(userId));
        if(jwtUser == null) {
            return new BaseResponse<>(INVALID_JWT);
        }
        // rabbiMQ에 탈퇴한 사용자 userIdx를 보냄
        producer.sendMessage(userId);

        userService.delete(Long.valueOf(userId));


        return new BaseResponse<>("회원 탈퇴 되었습니다.");
    }

    // [POST] 3-5 닉네임 중복확인
    @PostMapping("/check-nickname")
    public BaseResponse<UserNicknameResponseDto> nicknameCheck(@RequestBody UserNicknameRequestDto request) {
        if(request.getNickname().isEmpty() || request.getNickname().equals("")) {
            return new BaseResponse<>(POST_EMPTY_NICKNAME);
        }

        Long isExist = userService.nicknameCheck(request.getNickname().trim());
        if(isExist > 0) {
            return new BaseResponse<>(new UserNicknameResponseDto(true, "이미 존재하는 닉네임입니다."));
        } else {
            return new BaseResponse<>(new UserNicknameResponseDto(false, "사용해도 되는 닉네임입니다."));
        }
    }





}
