package eightjbbm.keepgo.sandbox.ds;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Address(@Column(name = "address") String address) {
    public String toSg() {
        String[] split = this.address.split(" ");
        return split[0] + " " + split[1];
    }

    public String toRoad() {
        return this.address;
    }
}
