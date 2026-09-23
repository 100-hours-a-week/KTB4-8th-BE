package eightjbbm.keepgo.recommendation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@DiscriminatorValue("EVENT")
@NoArgsConstructor
public class OutingEvent extends OutingGuide{
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private OutingPlace place;
    private Instant startAt;
    private Instant endAt;

    public OutingEvent(String category, String name, String description, OutingPlace place, Instant startAt, Instant endAt) {
        super(category, name, description);
        this.place = place;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
