package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Embeddable
@Getter
@RequiredArgsConstructor
public class Address {
    @Column(name = "address")
    private final String address;

    public String toSg() {
        String[] split = this.address.split(" ");
        return split[0] + " " + split[1];
    }

    public String toRoad() {
        return this.address;
    }
}
