-- 추천 코스 소요 시간은 분 단위로 저장한다. 기존 값도 이미 분 단위였으므로 이름만 맞춘다.
alter table outing_course rename column duration_hours to duration_minutes;
