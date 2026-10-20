package eightjbbm.keepgo.recommendation.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@DiscriminatorValue("outing_course")
@NoArgsConstructor
public class OutingCourse {

}