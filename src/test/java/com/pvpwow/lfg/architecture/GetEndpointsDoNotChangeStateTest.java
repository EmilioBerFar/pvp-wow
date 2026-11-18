package com.pvpwow.lfg.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * No GET changes state. The rule that lets CSRF be off.
 *
 * <p>CSRF protection is disabled in this application, and the ONLY reason
 * that is safe is that the session cookie is {@code SameSite=Lax} and no
 * state-changing route is a GET. The second half of that sentence is the
 * fragile one: a cookie attribute is a browser's promise, but a route is
 * ours, and a route can change by accident. One {@code @GetMapping} that
 * saves something and the promise breaks.
 *
 * <p>So the promise is checked by the build. This is a STRUCTURAL rule, not a
 * behavioural one: ArchUnit reads the bytecode and the annotations, and it
 * answers "could this method possibly write?" - not "did it write?". It cannot
 * follow a call through a service into a repository and know what happens
 * there. That is the honest limit of a static rule, and it is why the
 * runtime half exists too: the read paths run in a {@code READ ONLY}
 * transaction, and PostgreSQL refuses the write.
 *
 * <p>The rule has two halves, and the second is the one that bites:
 *
 * <ol>
 *   <li>A GET handler must not CALL a method of a port that can write.</li>
 *   <li>A GET handler must not even DEPEND ON a port that can write, because
 *       a dependency is one refactor away from a call.</li>
 * </ol>
 *
 * <p>HOW A PORT IS RECOGNISED AS A WRITING PORT: by its methods. The writing
 * operations of this project are named the way writing operations are named -
 * {@code save}, {@code publish}, {@code delete}, {@code expire}, {@code
 * complete}, {@code withdraw}, {@code close} - and the reading ones start with
 * {@code find}, {@code has}, {@code current}, {@code already}, {@code pending}
 * or {@code count}. The list is here, in one place, so adding a writing
 * method with a new name is a decision somebody has to make in this file
 * rather than a silent hole.
 */
class GetEndpointsDoNotChangeStateTest {

    /**
     * The names that mean "this method writes". A port method with one of
     * these is a writing method.
     */
    private static final Set<String> WRITING_METHOD_NAMES = Set.of(
            "save", "publish", "delete", "expire", "complete", "withdraw",
            "close", "remove", "update", "insert", "create", "apply");

    /**
     * The names that mean "this method only reads". A port method with one of
     * these is never a writing method, whatever else it looks like.
     */
    private static final Set<String> READING_METHOD_NAMES = Set.of(
            "find", "has", "current", "already", "pending", "count", "search",
            "get", "exists", "is");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.pvpwow.lfg");

    @Test
    void noGetHandlerCanReachAWritingPort() {
        DescribedPredicate<JavaClass> inTheInboundEdge =
                new DescribedPredicate<>("reside in the inbound edge") {
                    @Override
                    public boolean test(JavaClass input) {
                        return areInTheInboundEdge(input);
                    }
                };

        classes()
                .that(inTheInboundEdge)
                .should(onlyReachReadingPorts())
                .because("CSRF is disabled because the session cookie is SameSite=Lax "
                        + "and no state-changing route is a GET. A GET that can write "
                        + "breaks that promise: the Lax hole is top-level GET navigation, "
                        + "and a cross-site <img src> would be enough to trigger it. The "
                        + "runtime half of the promise is that the read paths run in a "
                        + "READ ONLY transaction and PostgreSQL refuses the write; this is "
                        + "the structural half, and it catches the mistake before the "
                        + "code ever runs")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theRuleActuallyFindsTheWritingPorts() {
        // A structural rule that finds nothing passes for the wrong reason.
        // This test fails if the list of writing names stops matching reality,
        // which is the day the rule above starts passing by accident.
        List<String> writingMethods = PRODUCTION_CLASSES.stream()
                .filter(this::isAWritingPort)
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(this::isAWritingMethod)
                .map(method -> method.getOwner().getSimpleName() + "." + method.getName())
                .sorted()
                .toList();

        assertThat(writingMethods)
                .as("the writing ports the rule knows about")
                .isNotEmpty();
    }

    @Test
    void everyGetHandlerIsActuallyRecognised() {
        // The mirror of the test above, for the other half of the rule: if the
        // GET handlers stopped being recognised, the rule would pass without
        // looking at a single one of them.
        long handlers = PRODUCTION_CLASSES.stream()
                .filter(this::areInTheInboundEdge)
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(this::isAGetHandler)
                .count();

        assertThat(handlers)
                .as("the GET handlers the rule watches")
                .isGreaterThanOrEqualTo(3);
    }

    private boolean areInTheInboundEdge(JavaClass javaClass) {
        return javaClass.getPackageName().startsWith("com.pvpwow.lfg.adapters.in");
    }

    /**
     * A port is a writing port when it has at least one writing method.
     */
    private boolean isAWritingPort(JavaClass javaClass) {
        return javaClass.getMethods().stream().anyMatch(this::isAWritingMethod);
    }

    /**
     * A method writes when its name says so and its name does not say it only
     * reads. The reading names win, because they are unambiguous.
     */
    private boolean isAWritingMethod(JavaMember method) {
        String name = method.getName();
        if (READING_METHOD_NAMES.stream().anyMatch(name::startsWith)) {
            return false;
        }
        return WRITING_METHOD_NAMES.stream().anyMatch(name::startsWith);
    }

    /**
     * The condition: no call to a writing method, and no dependency on a
     * writing port.
     */
    private ArchCondition<JavaClass> onlyReachReadingPorts() {
        return new ArchCondition<>("only reach reading ports") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : getHandlers(item)) {
                    for (JavaCall<?> call : method.getCallsFromSelf()) {
                        JavaMember target = resolveTarget(call);
                        if (target != null && isAWritingMethod(target)) {
                            events.add(SimpleConditionEvent.violated(call,
                                    "GET handler " + method.getFullName()
                                            + " calls the writing method "
                                            + target.getFullName()
                                            + " - a GET that changes state breaks the "
                                            + "SameSite=Lax promise CSRF relies on"));
                        }
                    }
                    for (JavaClass dependency : dependenciesOf(item, method)) {
                        if (isAWritingPort(dependency)) {
                            events.add(SimpleConditionEvent.violated(method,
                                    "GET handler " + method.getFullName()
                                            + " depends on the writing port "
                                            + dependency.getSimpleName()
                                            + " - a dependency is one refactor away from "
                                            + "a call"));
                        }
                    }
                }
            }
        };
    }

    /**
     * The ports a handler can reach: the ones its own signature names, and the
     * ones its CONTROLLER holds. The constructor is where the ports arrive,
     * so looking only at the method signature would miss every real case.
     */
    private List<JavaClass> dependenciesOf(JavaClass controller, JavaMethod method) {
        List<JavaClass> dependencies = new java.util.ArrayList<>();
        dependencies.addAll(method.getRawParameterTypes());
        // The constructor is where the ports arrive.
        controller.tryGetConstructor()
                .ifPresent(constructor ->
                        dependencies.addAll(constructor.getRawParameterTypes()));
        // And a field is where a controller may keep one.
        dependencies.addAll(controller.getFields().stream()
                .flatMap(field -> field.getRawType().getAllInvolvedRawTypes().stream())
                .toList());
        return dependencies;
    }

    /**
     * The method a call points at, when the call points at a method.
     */
    private JavaMember resolveTarget(JavaCall<?> call) {
        AccessTarget target = call.getTarget();
        Optional<? extends JavaMember> member = target.resolveMember();
        return member.orElse(null);
    }

    /**
     * The handlers of a controller: the methods annotated with
     * {@code @GetMapping}, plus the ones whose class-level
     * {@code @RequestMapping} declares {@code method = GET}.
     */
    private List<JavaMethod> getHandlers(JavaClass controller) {
        return controller.getMethods().stream()
                .filter(this::isAGetHandler)
                .toList();
    }

    private boolean isAGetHandler(JavaMethod method) {
        if (method.isAnnotatedWith(GetMapping.class)) {
            return true;
        }
        // The class-level @RequestMapping, if there is one. getAnnotations
        // and not getAnnotationOfType: the latter throws when the annotation
        // is absent, and most classes in the inbound edge do not carry it.
        for (JavaAnnotation<? extends HasDescription> annotation
                : method.getOwner().getAnnotations()) {
            if (!annotation.getRawType().isEquivalentTo(RequestMapping.class)) {
                continue;
            }
            RequestMapping mapping = annotation.as(RequestMapping.class);
            if (mapping.method().length > 0
                    && List.of(mapping.method()).contains(RequestMethod.GET)) {
                return true;
            }
        }
        return false;
    }
}
