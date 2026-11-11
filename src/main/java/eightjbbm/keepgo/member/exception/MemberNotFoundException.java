package eightjbbm.keepgo.member.exception;

import eightjbbm.keepgo.util.exception.NotFoundException;
import java.net.URI;

public class MemberNotFoundException extends NotFoundException {
    public MemberNotFoundException() {
        getBody().setProperty("code", "MEMBER_NOT_FOUND");
        getBody().setType(URI.create("/problems/member-not-found"));
        getBody().setTitle("회원을 찾을 수 없습니다.");
    }
}
