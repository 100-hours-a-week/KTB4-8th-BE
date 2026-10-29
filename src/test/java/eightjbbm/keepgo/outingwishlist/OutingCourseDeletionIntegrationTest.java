package eightjbbm.keepgo.outingwishlist;

import static org.junit.jupiter.api.Assertions.*;

import com.querydsl.jpa.impl.JPAQueryFactory;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlist;
import eightjbbm.keepgo.outingwishlist.adapter.out.persistence.OutingWishlistQueryRepository;
import eightjbbm.keepgo.outingwishlist.application.domain.model.OutingWishlistEntry;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistPageReader;
import eightjbbm.keepgo.outingwishlist.application.domain.service.OutingWishlistService;
import eightjbbm.keepgo.outingwishlist.application.domain.service.PageTokenCodec;
import eightjbbm.keepgo.outingwishlist.application.domain.service.dto.*;
import eightjbbm.keepgo.outingwishlist.common.OutingCourseNotFoundException;
import eightjbbm.keepgo.recommendation.entity.OutingCourse;
import eightjbbm.keepgo.recommendation.entity.OutingPlace;
import eightjbbm.keepgo.recommendation.repository.OutingCourseRepository;
import eightjbbm.keepgo.util.exception.NotFoundException;
import jakarta.persistence.Entity;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_SERVER_URL", matches = "jdbc:mysql://[^/]+/")
class OutingCourseDeletionIntegrationTest {
    private SessionFactory factory;
    private Session session;
    private OutingWishlistService service;
    private String serverUrl;
    private String username;
    private String password;
    private String schema;
    private Long memberId;
    private Long otherMemberId;
    private Long courseId;

    @BeforeEach
    void setUp() throws Exception {
        serverUrl = System.getenv("MYSQL_TEST_SERVER_URL");
        if (serverUrl == null || !serverUrl.matches("jdbc:mysql://[^/]+/")) {
            throw new IllegalStateException("Set MYSQL_TEST_SERVER_URL to a disposable MySQL server");
        }
        username = System.getenv().getOrDefault("MYSQL_TEST_USER", "root");
        password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        schema = "keepgo_wishlist_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(serverUrl, username, password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }
        var configuration = new Configuration()
                .setProperty("hibernate.connection.url", serverUrl + schema)
                .setProperty("hibernate.connection.username", username)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty(
                        "hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("eightjbbm.keepgo")) {
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        }
        factory = configuration.buildSessionFactory();
        session = factory.openSession();
        var repositories = new JpaRepositoryFactory(session);
        var queryRepository = new OutingWishlistQueryRepository(new JPAQueryFactory(session));
        var pageTokenCodec = new PageTokenCodec();
        service = new OutingWishlistService(
                repositories.getRepository(MemberRepository.class),
                repositories.getRepository(OutingCourseRepository.class),
                repositories.getRepository(OutingWishlist.class),
                queryRepository,
                new OutingWishlistPageReader(queryRepository, pageTokenCodec));
        transaction(() -> {
            var member = Member.create("소유자");
            var other = Member.create("다른 회원");
            var course = new OutingCourse("서울 산책", 2, 90, "서울", List.of());
            session.persist(member);
            session.persist(other);
            session.persist(course);
            memberId = member.getId();
            otherMemberId = other.getId();
            courseId = course.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) session.close();
        if (factory != null) factory.close();
        if (schema != null) {
            try (var connection = DriverManager.getConnection(serverUrl, username, password);
                    var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE " + schema);
            }
        }
    }

    @Test
    void deletedCourseDisappearsFromListAndDetailAndCannotBeDeletedAgain() {
        var entryId = transaction(() -> service.addOutingCourse(new AddOutingCourseCommand(memberId, courseId))
                .addedOutingWishlistEntryId());
        assertEquals(
                entryId,
                transaction(() -> service.getOutingCourseList(new GetOutingCourseListCommand(memberId, null, null)))
                        .courses()
                        .getFirst()
                        .courseId());
        transaction(() -> {
            service.removeOutingCourse(memberId, entryId);
            return null;
        });
        assertTrue(transaction(() -> service.getOutingCourseList(new GetOutingCourseListCommand(memberId, null, null)))
                .courses()
                .isEmpty());
        assertThrows(
                NotFoundException.class,
                () -> transaction(
                        () -> service.getOutingCourseDetail(new GetOutingCourseDetailCommand(memberId, entryId))));
        assertThrows(
                OutingCourseNotFoundException.class,
                () -> transaction(() -> {
                    service.removeOutingCourse(memberId, entryId);
                    return null;
                }));
        // 코스 자체는 보존되므로 다시 위시리스트에 추가할 수 있다.
        assertNotNull(transaction(() -> service.addOutingCourse(new AddOutingCourseCommand(memberId, courseId))));
    }

    @Test
    void anotherMemberCannotDeleteOwnersCourse() {
        var entryId = transaction(() -> service.addOutingCourse(new AddOutingCourseCommand(memberId, courseId))
                .addedOutingWishlistEntryId());
        assertThrows(
                OutingCourseNotFoundException.class,
                () -> transaction(() -> {
                    service.removeOutingCourse(otherMemberId, entryId);
                    return null;
                }));
        assertEquals(
                entryId,
                transaction(() -> service.getOutingCourseDetail(new GetOutingCourseDetailCommand(memberId, entryId)))
                        .courseId());
    }

    @Test
    void courseDeletionCannotDeletePlaceEntry() {
        var entryId = transaction(() -> {
            var place = new OutingPlace("공원", "산책 공원", "설명", "", "서울특별시 종로구 세종대로 1", null, null);
            session.persist(place);
            var entry = OutingWishlistEntry.from(session.getReference(Member.class, memberId), place);
            session.persist(entry);
            return entry.getId();
        });
        assertThrows(
                OutingCourseNotFoundException.class,
                () -> transaction(() -> {
                    service.removeOutingCourse(memberId, entryId);
                    return null;
                }));
        assertEquals(
                entryId,
                transaction(() -> service.getPlaceDetail(new GetPlaceDetailCommand(memberId, entryId)))
                        .placeId());
    }

    private <T> T transaction(Supplier<T> action) {
        var transaction = session.beginTransaction();
        try {
            var result = action.get();
            transaction.commit();
            return result;
        } catch (RuntimeException exception) {
            if (transaction.isActive()) transaction.rollback();
            throw exception;
        } finally {
            session.clear();
        }
    }
}
