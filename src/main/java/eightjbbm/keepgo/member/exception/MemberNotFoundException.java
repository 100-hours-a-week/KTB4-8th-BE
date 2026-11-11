package eightjbbm.keepgo.member.exception;

import eightjbbm.keepgo.util.exception.NotFoundException;

public class MemberNotFoundException extends NotFoundException {
    public MemberNotFoundException() {
        super("MEMBER_NOT_FOUND");
    }
}
