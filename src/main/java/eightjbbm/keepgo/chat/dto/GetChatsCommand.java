package eightjbbm.keepgo.chat.dto;

/// @param userId 회원 ID
/// @param cursor 검색에 기준이 될 커서
/// @param size 1회 검색 시 반환할 결과의 최댓값
public record GetChatsCommand(
        Long userId,
        Long cursor,
        Integer size
) {
}
