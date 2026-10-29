package eightjbbm.keepgo.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// [코드 규칙](../../../../../../../docs/code-conventions.md) 중 구조로 검증할 수 있는 항목을 고정한다.
/// 알려진 예외는 FreezingArchRule이 `src/test/resources/archunit_store`에 기록하며, 새로 생긴 위반만 실패시킨다.
@AnalyzeClasses(packages = "eightjbbm.keepgo", importOptions = ImportOption.DoNotIncludeTests.class)
@DisplayName("아키텍처 규칙")
class ArchitectureRulesTest {

    /// 필드뿐 아니라 메서드·생성자 파라미터에 붙은 `@Value`까지 찾는다.
    private static final ArchCondition<JavaClass> NOT_USE_VALUE_ANNOTATION = new ArchCondition<>("use @Value") {
        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            javaClass.getCodeUnits().forEach(unit -> {
                if (unit.isAnnotatedWith(Value.class)) {
                    events.add(SimpleConditionEvent.violated(unit, unit.getFullName() + " uses @Value"));
                }
                if (unit instanceof JavaCodeUnit codeUnit) {
                    codeUnit.getParameters().stream()
                            .filter(parameter -> parameter.isAnnotatedWith(Value.class))
                            .forEach(parameter -> events.add(SimpleConditionEvent.violated(
                                    parameter, parameter.getOwner().getFullName() + " uses @Value")));
                }
            });
        }
    };

    @ArchTest
    static final ArchRule controllerDoesNotDependOnRepository = FreezingArchRule.freeze(noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .as("Controller는 Repository에 의존하지 않는다"));

    @ArchTest
    static final ArchRule noValueAnnotation = FreezingArchRule.freeze(
            classes().should(NOT_USE_VALUE_ANNOTATION).as("설정값은 @Value가 아니라 @ConfigurationProperties로 읽는다"));

    @ArchTest
    static final ArchRule noFieldInjection = FreezingArchRule.freeze(
            noFields().should().beAnnotatedWith(Autowired.class).as("필드 주입을 쓰지 않는다"));

    @ArchTest
    static final ArchRule transactionalOnlyOnServiceMethod = FreezingArchRule.freeze(methods()
            .that()
            .areAnnotatedWith(Transactional.class)
            .should()
            .beDeclaredInClassesThat()
            .areAnnotatedWith(Service.class)
            .as("@Transactional은 @Service 메서드에만 붙인다"));

    @ArchTest
    static final ArchRule noClassLevelTransactional = FreezingArchRule.freeze(
            noClasses().should().beAnnotatedWith(Transactional.class).as("클래스 단위 @Transactional을 쓰지 않는다"));

    @ArchTest
    static final ArchRule entityWithoutSetter = FreezingArchRule.freeze(noMethods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Entity.class)
            .should()
            .haveNameMatching("set[A-Z].*")
            .as("엔티티에 setter를 만들지 않는다"));
}
