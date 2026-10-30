package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.common.domain.Address;
import eightjbbm.keepgo.common.domain.BusinessSchedule;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.file.File;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@NoArgsConstructor
@DiscriminatorColumn(name = "type")
public abstract class OutingGuide {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String category;
    private String description;
    private String locationDetail;

    @Nullable
    private Address address;

    private Coordinate coordinate;

    @Nullable
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_schedule", columnDefinition = "json")
    private BusinessSchedule businessSchedule;

    @OneToOne
    @JoinColumn(name = "image_id")
    private File attachedImage;

    public OutingGuide(
            String category,
            String name,
            String description,
            String locationDetail,
            String address,
            Coordinate coordinate,
            @Nullable BusinessSchedule businessSchedule) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.locationDetail = locationDetail;
        this.address = new Address(address);
        this.coordinate = coordinate;
        this.businessSchedule = businessSchedule;
    }

    public abstract boolean isAvailable(Instant instant);

    /// 나들이 가이드의 유형. 같은 이름·주소라도 장소와 행사는 서로 다른 가이드다.
    public abstract GuideType guideType();

    /// (유형, 이름, 도로명주소)를 정확히 비교한다. 모든 필드가 같은 경우에만 같은 가이드다.
    public boolean isSameAs(OutingGuide outingGuide) {
        return this.guideType() == outingGuide.guideType()
                && Objects.equals(this.name, outingGuide.getName())
                && Objects.equals(this.address, outingGuide.getAddress());
    }
}
