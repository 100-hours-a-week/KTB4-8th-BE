package eightjbbm.keepgo.recommendation.entity;

import eightjbbm.keepgo.sandbox.ds.Address;
import eightjbbm.keepgo.sandbox.ds.BusinessHours;
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
    private Address address;
    private Coordinate coordinate;
    @Nullable
    private BusinessHours businessHours;

    @OneToOne
    @JoinColumn(name = "image_id")
    private File attachedImage;

    public OutingGuide(
            String category,
            String name,
            String description,
            String address,
            Coordinate coordinate,
            @Nullable BusinessHours businessHours) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.address = new Address(address);
        this.coordinate = coordinate;
        this.businessHours = businessHours;
    }

    public abstract boolean isAvailable(Instant instant);
}
