package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_schedule_weekly")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessScheduleWeeklyJpaEntity {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_schedule_id")
    private BusinessScheduleJpaEntity businessSchedule;

    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Embedded
    private TimeInterval timeInterval;

    public BusinessScheduleWeeklyJpaEntity(
            BusinessScheduleJpaEntity businessHours, DayOfWeek dayOfWeek, TimeInterval timeInterval) {
        this.businessSchedule = businessHours;
        this.dayOfWeek = dayOfWeek;
        this.timeInterval = timeInterval;
    }
}
