package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.sandbox.ds.Address;
import eightjbbm.keepgo.sandbox.ds.BusinessSchedule;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.file.File;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Entity
@Getter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@NoArgsConstructor
@DiscriminatorColumn(name = "type")
public abstract class OutingGuide {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private String category;
    private String description;
    private String locationDetail;
    private Address address;
    private Coordinate coordinate;
    @Nullable
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

    public boolean isSameAs(OutingGuide outingGuide) {
        return this.address.equals(outingGuide.getAddress())
                && this.name.equals(outingGuide.getName());
    }
}
