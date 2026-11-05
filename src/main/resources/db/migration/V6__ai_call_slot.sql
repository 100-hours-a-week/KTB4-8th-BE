-- AI 서버로 나가는 전역 동시 호출 상한. 여러 인스턴스가 같은 슬롯 행을 조건부 UPDATE로 임대한다.
-- 행은 기동 시 AiCallSlotInitializer가 설정값에 맞춰 만들고, 비어 있는 초과 슬롯만 삭제한다.
create table ai_call_slot (
    slot_type varchar(20) not null,
    slot_no integer not null,
    holder varchar(64) null,
    leased_until datetime(6) null,
    primary key (slot_type, slot_no)
) engine=InnoDB;
