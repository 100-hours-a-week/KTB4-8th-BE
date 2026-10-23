package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "business_schedule_exception")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessScheduleExceptionJpaEntity {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_schedule_id")
    private BusinessScheduleJpaEntity businessSchedule;

    private LocalDate date;

    @Embedded
    private TimeInterval timeInterval;

    public BusinessScheduleExceptionJpaEntity(
            BusinessScheduleJpaEntity businessHours,
            LocalDate date,
            TimeInterval timeInterval
    ) {
        this.businessSchedule = businessHours;
        this.date = date;
        this.timeInterval = timeInterval;
    }
}
