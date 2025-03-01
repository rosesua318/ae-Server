package com.ae.community.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CodeAndMessage {

    // 유저 관련 1000 ~
    EMPTY_USER("1000", "해당 유저가 없습니다"),
    EMPTY_PATHVARIABLE_USERID("1001", "userIdx pathvariable이 없습니다"),
    INVALID_JWT_USERIDX("1002","유효하지 않은 JWT입니다.(userIdx)" ),
    EMPTY_JWT("1003", "권한이 없는 유저의 접근입니다. token을 확인하세요"),
    NOT_CORRECT_JWT_AND_PATH_VARIABLE("1004", ".Path variable의 userID와 jwt id 가 다릅니다"),
    INVALID_JWT_ICON("1005","유효하지 않은 JWT입니다.(icon)" ),
    INVALID_JWT_NICKNAME("1006","유효하지 않은 JWT입니다.(nickname)" ),

    // 게시글 관련 1100 ~
    EMPTY_CONTENT("1100", "게시글 내용이 비어있습니다"),
    EMPTY_TITLE("1101", "게시글 제목이 비어있습니다"),
    INVALID_POST_ID("1102", "유효하지 않은 postIdx 입니다."),
    EMPTY_GROUPNAME("1003", "게시판 종류가 비어있습니다."),
    EMPTY_PATHVARIABLE_GROUPNAME("1004", "게시판 종류 path variable이 비어있습니다"),

    // 이미지 관련 1200 ~
    NO_MATCHING_IMAGES("1200", "imagesIdx에 해당하는 것이 없습니다."),
    EMPTY_MULTIPARTFILE("1201", "이미지 파일이 비었습니다"),

    // 댓글 관련 1300 ~
    COMMENT_NO_CONTENT("1300", "댓글 내용이 비어있습니다."),
    COMMENT_LONG_CONTENT("1301", "댓글 내용이 너무 깁니다."),
    INVALID_COMMENT("1302", "유효하지 않은 comment 입니다."),

    // 좋아요 관련 1400 ~
    INVALID_THUMBUP("1400", "유효하지 않은 thumbup 입니다."),

    // 스크랩 관련 1600 ~
    INVALID_SCRAP("1600", "유효하지 않은 scrap 입니다."),

    // S3 Util
    FILE_CONVERT_ERROR("1200", "파일 변환에 실패했습니다."),
    FILE_EXTENSION_ERROR("1201", "파일 확장자 인식에 실패했습니다.");



    private final String code;
    private final String message;

}
