package eightjbbm.keepgo.outingwishlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import eightjbbm.keepgo.outingwishlist.adapter.in.web.OutingWishlistController;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OutingWishlistControllerTest {
    @Test
    void successfulCourseDeletionReturnsNoContent() {
        var service = mock(OutingWishlistService.class);
        var controller = new OutingWishlistController(service);
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("7")
                .build();

        var response = controller.removeOutingCourse(42L, jwt);

        assertEquals(204, response.getStatusCode().value());
        verify(service).removeOutingCourse(7L, 42L);
    }

    @Test
    void successfulPlaceDeletionReturnsNoContent() {
        var service = mock(OutingWishlistService.class);
        var controller = new OutingWishlistController(service);
        var jwt = jwt();

        var response = controller.removePlace(42L, jwt);

        assertEquals(204, response.getStatusCode().value());
        verify(service).removePlace(7L, 42L);
    }

    @Test
    void successfulEventDeletionReturnsNoContent() {
        var service = mock(OutingWishlistService.class);
        var controller = new OutingWishlistController(service);
        var jwt = jwt();

        var response = controller.removeEvent(42L, jwt);

        assertEquals(204, response.getStatusCode().value());
        verify(service).removeEvent(7L, 42L);
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("7")
                .build();
    }
}
