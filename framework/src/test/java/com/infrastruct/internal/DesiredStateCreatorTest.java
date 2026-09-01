package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infrastruct.fixture.desired.AllowPort;
import com.infrastruct.fixture.desired.AllowPortHandler;
import com.infrastruct.fixture.desired.DependencyAddingHandler;
import com.infrastruct.fixture.desired.DesiredKind;
import com.infrastruct.fixture.desired.EncryptHandler;
import com.infrastruct.fixture.desired.Encrypted;
import com.infrastruct.fixture.desired.IdentityStampingHandler;
import com.infrastruct.fixture.desired.KindChangingHandler;
import com.infrastruct.fixture.desired.LogicalIdChangingHandler;
import com.infrastruct.fixture.desired.NoDefaultCtorHandler;
import com.infrastruct.fixture.desired.NullReturningHandler;
import com.infrastruct.fixture.desired.ThrowingHandler;
import com.infrastruct.spi.DesiredResourceState;
import com.infrastruct.spi.DesiredResources;
import com.infrastruct.spi.ScannedResourceState;
import com.infrastruct.spi.ScannedResources;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link DesiredStateCreator} 는 스캔 결과의 매크로 어노테이션을 전부 풀어 원하는 상태로 옮긴다. */
class DesiredStateCreatorTest {

    /** 픽스처: 인자가 붙은 어노테이션 인스턴스를 얻기 위한 대상. 기본값(22)이 아닌 값이라야 반증력이 있다. */
    @AllowPort(port = 8080)
    private static final class Port8080Holder {}

    private static ScannedResourceState scannedResource(
            String logicalId, List<CapturedAnnotation> annotations) {
        return new ScannedResourceState(
                DesiredKind.VPC, logicalId, Map.of(), List.of(), Set.of(), annotations);
    }

    /** 픽스처: 같은 키를 나중에 덮어쓸 두 번째 인스턴스. */
    @AllowPort(port = 443)
    private static final class Port443Holder {}

    /** 픽스처: 멤버 없는 어노테이션 인스턴스를 얻기 위한 대상. */
    @Encrypted private static final class EncryptedHolder {}

    private static Annotation encrypted() {
        return EncryptedHolder.class.getAnnotation(Encrypted.class);
    }

    private static Annotation allowPort443() {
        return Port443Holder.class.getAnnotation(AllowPort.class);
    }

    private static Annotation allowPort8080() {
        return Port8080Holder.class.getAnnotation(AllowPort.class);
    }

    @Test
    void canBeInstantiatedWithNoArgs() {
        assertThat(new DesiredStateCreator()).isNotNull();
    }

    @Test
    void createRejectsNullInput() {
        // 뒤 단계로 넘길 값을 만들 수 없는 실패라 첫 자리에서 멈춘다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        // 스텁 시절부터 성립하던 성질이라 red 를 만들 수 없었다 (spec §10-1). 회귀 방지로만 둔다.
        assertThat(new DesiredStateCreator().create(new ScannedResources(List.of())).resources())
                .isEmpty();
    }

    @Test
    void movesResourcesOneToOneKeepingOrderAndFields() {
        ScannedResourceState vpc =
                new ScannedResourceState(
                        DesiredKind.VPC,
                        "vpc.myVpc",
                        Map.of("cidrBlock", "10.0.0.0/16"),
                        List.of(),
                        Set.of("cidrBlock"),
                        List.of());
        ScannedResourceState securityGroup =
                new ScannedResourceState(
                        DesiredKind.SECURITY_GROUP,
                        "sg.mySg",
                        Map.of(),
                        List.of("vpc.myVpc"),
                        Set.of("vpc"),
                        List.of());

        DesiredResources created =
                new DesiredStateCreator().create(new ScannedResources(List.of(vpc, securityGroup)));

        // 이 단계는 자원을 더하거나 빼지 않는다 — 순서 그대로 1:1 로 옮긴다.
        assertThat(created.resources())
                .extracting(DesiredResourceState::logicalId)
                .containsExactly("vpc.myVpc", "sg.mySg");

        DesiredResourceState movedVpc = created.resources().get(0);
        assertThat(movedVpc.kind()).isSameAs(DesiredKind.VPC);
        assertThat(movedVpc.config()).containsOnly(Map.entry("cidrBlock", "10.0.0.0/16"));
        assertThat(movedVpc.requiredFields()).containsExactly("cidrBlock");
        assertThat(created.resources().get(1).dependencies()).containsExactly("vpc.myVpc");
    }

    @Test
    void passesAnnotationInstanceToHandlerSoArgumentValuesLand() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "sg.mySg",
                                        List.of(
                                                new CapturedAnnotation(
                                                        allowPort8080(),
                                                        AllowPortHandler.class)))));

        DesiredResources created = new DesiredStateCreator().create(scanned);

        // 타입만으로는 부족하다 — 어노테이션 인스턴스가 와야 port() 를 읽을 수 있다.
        assertThat(created.resources().get(0).config()).containsOnly(Map.entry("port", 8080));
    }

    @Test
    void appliesAnnotationsInScanOrderSoTheLaterOneWins() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "sg.mySg",
                                        List.of(
                                                new CapturedAnnotation(
                                                        allowPort8080(), AllowPortHandler.class),
                                                new CapturedAnnotation(
                                                        allowPort443(), AllowPortHandler.class)))));

        DesiredResources created = new DesiredStateCreator().create(scanned);

        // 순서는 스캐너가 정한 순서 그대로 — 같은 키를 건드리면 나중 것이 이긴다.
        assertThat(created.resources().get(0).config()).containsOnly(Map.entry("port", 443));
    }

    @Test
    void reusesOneHandlerInstanceAcrossResources() {
        CapturedAnnotation stamping =
                new CapturedAnnotation(encrypted(), IdentityStampingHandler.class);
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource("vpc.myVpc", List.of(stamping)),
                                scannedResource("sg.mySg", List.of(stamping))));

        DesiredResources created = new DesiredStateCreator().create(scanned);

        // 핸들러는 상태 없는 함수로 본다 — create() 한 번 안에서 Class 당 인스턴스 하나면 된다.
        assertThat(created.resources().get(0).config().get("handlerId"))
                .isEqualTo(created.resources().get(1).config().get("handlerId"));
    }

    @Test
    void rejectsHandlerWithoutPublicNoArgConstructor() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        encrypted(),
                                                        NoDefaultCtorHandler.class)))));

        // 프로바이더의 선언 실수다 — 메시지만 읽고 어느 자원의 어느 핸들러인지 알 수 있어야 한다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(scanned))
                .isInstanceOf(DesiredStateException.class)
                .hasMessageContaining("vpc.myVpc")
                .hasMessageContaining(NoDefaultCtorHandler.class.getName())
                .hasCauseInstanceOf(ReflectiveOperationException.class);
    }

    @Test
    void rejectsHandlerDeclaringADifferentAnnotationType() {
        // EncryptHandler 는 Encrypted 를 받는데 AllowPort 인스턴스를 짝지었다. CapturedAnnotation 이
        // 둘을 따로 들고 있어 컴파일은 통과한다 — 런타임에 엔진이 잡아야 한다.
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        allowPort8080(), EncryptHandler.class)))));

        // 핸들러 안에서 ClassCastException 이 터지면 사용자가 원인을 못 찾는다. 부르기 전에 막는다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(scanned))
                .isInstanceOf(DesiredStateException.class)
                .hasMessageContaining("vpc.myVpc")
                .hasMessageContaining(EncryptHandler.class.getName())
                .hasMessageContaining(AllowPort.class.getName())
                .hasMessageContaining(Encrypted.class.getName());
    }

    @Test
    void rejectsHandlerReturningNull() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        encrypted(),
                                                        NullReturningHandler.class)))));

        // 반환값이 다음 핸들러의 입력이다 — null 이면 이어 붙일 것이 없다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(scanned))
                .isInstanceOf(DesiredStateException.class)
                .hasMessageContaining("vpc.myVpc")
                .hasMessageContaining(NullReturningHandler.class.getName())
                .hasNoCause();
    }

    @Test
    void locksLogicalIdButLetsHandlersAddDependencies() {
        ScannedResources renaming =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        encrypted(),
                                                        LogicalIdChangingHandler.class)))));

        // logicalId 는 뒤 단계가 상태를 짝지을 때 쓰는 키다. 여기서 바뀌면 "삭제 + 생성"으로 보인다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(renaming))
                .isInstanceOf(DesiredStateException.class)
                .hasMessageContaining("vpc.myVpc")
                .hasMessageContaining(LogicalIdChangingHandler.class.getName());

        ScannedResources adding =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        encrypted(),
                                                        DependencyAddingHandler.class)))));

        // 반대로 의존 관계를 더하는 것은 매크로의 정상적인 일이라 막지 않는다.
        DesiredResources created = new DesiredStateCreator().create(adding);
        assertThat(created.resources().get(0).dependencies()).containsExactly("vpc.added");
    }

    @Test
    void locksKindToo() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        encrypted(), KindChangingHandler.class)))));

        // 자원의 종류가 바뀌면 프로바이더가 전혀 다른 자원을 만들게 된다 — logicalId 와 같은 이유로 잠근다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(scanned))
                .isInstanceOf(DesiredStateException.class)
                .hasMessageContaining("vpc.myVpc")
                .hasMessageContaining(KindChangingHandler.class.getName());
    }

    @Test
    void wrapsExceptionThrownByHandlerKeepingTheCause() {
        ScannedResources scanned =
                new ScannedResources(
                        List.of(
                                scannedResource(
                                        "vpc.myVpc",
                                        List.of(
                                                new CapturedAnnotation(
                                                        encrypted(), ThrowingHandler.class)))));

        // 핸들러가 던진 예외만으로는 어느 자원 얘기인지 알 수 없다. 맥락은 여기서만 붙일 수 있다.
        assertThatThrownBy(() -> new DesiredStateCreator().create(scanned))
                .isInstanceOf(DesiredStateException.class)
                .hasMessageContaining("vpc.myVpc")
                .hasMessageContaining(ThrowingHandler.class.getName())
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("handler boom");
    }
}
