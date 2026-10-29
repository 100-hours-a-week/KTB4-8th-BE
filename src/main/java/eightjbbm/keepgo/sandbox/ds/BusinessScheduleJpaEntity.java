package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessScheduleJpaEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String timezone;

    private boolean estimated;

    @OneToMany(mappedBy = "businessSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessScheduleWeeklyJpaEntity> weeklyHours = new ArrayList<>();

    ;

    @OneToMany(mappedBy = "businessSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessScheduleExceptionJpaEntity> exceptions = new ArrayList<>();

    ;

    public BusinessScheduleJpaEntity(BusinessSchedule domain) {
        this.timezone = domain.getTimezone().getId();
        this.estimated = domain.isEstimated();
        domain.getWeeklyHours()
                .forEach((dayOfWeek, intervals) -> intervals.forEach(interval ->
                        this.weeklyHours.add(new BusinessScheduleWeeklyJpaEntity(this, dayOfWeek, interval))));

        domain.getExceptions()
                .forEach((date, intervals) -> intervals.forEach(
                        interval -> exceptions.add(new BusinessScheduleExceptionJpaEntity(this, date, interval))));
    }

    public BusinessSchedule toDomain() {
        Map<DayOfWeek, List<TimeInterval>> weeklyHoursMap = weeklyHours.stream()
                .collect(Collectors.groupingBy(
                        BusinessScheduleWeeklyJpaEntity::getDayOfWeek,
                        Collectors.mapping(BusinessScheduleWeeklyJpaEntity::getTimeInterval, Collectors.toList())));

        Map<LocalDate, List<TimeInterval>> exceptionMap = exceptions.stream()
                .collect(Collectors.groupingBy(
                        BusinessScheduleExceptionJpaEntity::getDate,
                        Collectors.mapping(BusinessScheduleExceptionJpaEntity::getTimeInterval, Collectors.toList())));

        return new BusinessSchedule(ZoneId.of(timezone), estimated, weeklyHoursMap, exceptionMap);
    }
}
