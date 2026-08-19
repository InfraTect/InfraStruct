package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infrastruct.spi.CurrentResourceState;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CurrentStateStoreTest {

    /** 픽스처: 프로바이더가 Kind 를 enum 으로 구현하는 방식을 흉내 낸다. */
    enum TestKind implements Kind {
        EC2("EC2"),
        VPC("VPC"),
        S3("s3-bucket"); // name() 과 value() 가 다른 상수

        private final String value;

        TestKind(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    /** 픽스처: value() 가 겹치는 enum — 상수를 하나로 특정할 수 없는 경우. */
    enum DuplicateValueKind implements Kind {
        FIRST,
        SECOND;

        @Override
        public String value() {
            return "SAME";
        }
    }

    /** 픽스처: enum 이 아닌 Kind 구현 — 상태로 저장할 수 없다. */
    static final class NotEnumKind implements Kind {
        @Override
        public String value() {
            return "NOT_ENUM";
        }
    }

    @TempDir Path tempDir;

    private Path stateFile;
    private CurrentStateStore store;

    @BeforeEach
    void setUp() {
        stateFile = tempDir.resolve(CurrentStateStore.DEFAULT_FILE_NAME);
        store = new CurrentStateStore(stateFile);
    }

    /** 자원 하나짜리 픽스처. */
    private static CurrentResourceState resource(String logicalId, String physicalId) {
        return new CurrentResourceState(
                TestKind.EC2,
                logicalId,
                Map.of("instanceType", "t3.micro", "port", 22, "enabled", true),
                List.of("vpc.myVpc"),
                Set.of(),
                physicalId);
    }

    private static CurrentResources resources(CurrentResourceState... states) {
        return new CurrentResources(List.of(states));
    }

    @Test
    void holdsTheStateFilePathGivenToTheConstructor() {
        assertThatCode(() -> new CurrentStateStore(stateFile)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullStateFilePath() {
        assertThatThrownBy(() -> new CurrentStateStore(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultFileNameIsInfrasStateJson() {
        assertThat(CurrentStateStore.DEFAULT_FILE_NAME).isEqualTo("infras.state.json");
    }

    @Test
    void roundTripPreservesEveryField() {
        CurrentResourceState saved = resource("ec2.myEc2", "i-0abc123");

        store.save(resources(saved));
        CurrentResources loaded = store.load();

        assertThat(loaded.resources()).hasSize(1);
        CurrentResourceState restored = loaded.resources().get(0);
        assertThat(restored.logicalId()).isEqualTo("ec2.myEc2");
        assertThat(restored.physicalId()).isEqualTo("i-0abc123");
        assertThat(restored.config()).containsExactlyInAnyOrderEntriesOf(saved.config());
        assertThat(restored.dependencies()).containsExactly("vpc.myVpc");
    }

    @Test
    void loadReturnsEmptyResourcesWhenStateFileDoesNotExist() {
        CurrentResources loaded = store.load();

        assertThat(loaded).isNotNull();
        assertThat(loaded.resources()).isEmpty();
        assertThat(Files.exists(stateFile)).isFalse();
    }

    @Test
    void roundTripRestoresTheSameEnumConstant() {
        store.save(resources(resource("ec2.myEc2", "i-0abc123")));

        CurrentResources loaded = store.load();

        assertThat(loaded.resources().get(0).kind()).isSameAs(TestKind.EC2);
    }

    @Test
    void roundTripUsesValueNotEnumConstantName() {
        CurrentResourceState bucket =
                new CurrentResourceState(
                        TestKind.S3, "s3.myBucket", Map.of(), List.of(), Set.of(), null);

        store.save(resources(bucket));

        assertThat(store.load().resources().get(0).kind()).isSameAs(TestKind.S3);
    }

    /** 저장된 상태 파일을 JSON 트리로 읽는다 — 포맷이 아니라 구조를 검증하기 위해서다. */
    private JsonObject readStateFileAsJson() throws IOException {
        return JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private JsonObject firstEntryAsJson() throws IOException {
        return readStateFileAsJson().getAsJsonArray("resources").get(0).getAsJsonObject();
    }

    private static CurrentResourceState resourceWithConfig(Map<String, Object> config) {
        return new CurrentResourceState(
                TestKind.EC2, "ec2.myEc2", config, List.of(), Set.of(), null);
    }

    @Test
    void roundTripPreservesNullPhysicalId() {
        store.save(resources(resource("ec2.myEc2", null)));

        assertThat(store.load().resources().get(0).physicalId()).isNull();
    }

    @Test
    void physicalIdKeyIsAbsentWhenNull() throws IOException {
        store.save(resources(resource("ec2.myEc2", null)));

        assertThat(firstEntryAsJson().has("physicalId")).isFalse();
    }

    @Test
    void smallIntegerStaysInteger() {
        store.save(resources(resourceWithConfig(Map.of("small", 22))));

        assertThat(store.load().resources().get(0).config().get("small"))
                .isEqualTo(22)
                .isInstanceOf(Integer.class);
    }

    @Test
    void numberBeyondIntRangeBecomesLong() {
        store.save(resources(resourceWithConfig(Map.of("big", 3_000_000_000L))));

        assertThat(store.load().resources().get(0).config().get("big"))
                .isEqualTo(3_000_000_000L)
                .isInstanceOf(Long.class);
    }

    @Test
    void decimalStaysDouble() {
        store.save(resources(resourceWithConfig(Map.of("ratio", 0.5))));

        assertThat(store.load().resources().get(0).config().get("ratio"))
                .isEqualTo(0.5)
                .isInstanceOf(Double.class);
    }

    @Test
    void roundTripIsStable() {
        CurrentResources first = resources(resource("ec2.myEc2", "i-0abc123"));

        store.save(first);
        CurrentResources once = store.load();
        store.save(once);
        CurrentResources twice = store.load();

        assertThat(twice.resources().get(0).config())
                .containsExactlyInAnyOrderEntriesOf(once.resources().get(0).config());
        assertThat(twice.resources().get(0).physicalId())
                .isEqualTo(once.resources().get(0).physicalId());
    }

    @Test
    void roundTrippedConfigIsImmutable() {
        store.save(resources(resource("ec2.myEc2", "i-0abc123")));
        Map<String, Object> config = store.load().resources().get(0).config();

        assertThatThrownBy(() -> config.put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void roundTrippedDependenciesAreImmutable() {
        store.save(resources(resource("ec2.myEc2", "i-0abc123")));
        List<String> dependencies = store.load().resources().get(0).dependencies();

        assertThatThrownBy(() -> dependencies.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiredFieldsAreEmptyAfterRoundTrip() {
        store.save(resources(resource("ec2.myEc2", "i-0abc123")));

        assertThat(store.load().resources().get(0).requiredFields()).isEmpty();
    }

    @Test
    void preservesResourceOrder() {
        store.save(resources(resource("a", null), resource("b", null), resource("c", null)));

        assertThat(store.load().resources())
                .extracting(CurrentResourceState::logicalId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void emptyResourcesRoundTrips() {
        store.save(new CurrentResources(List.of()));

        assertThat(Files.exists(stateFile)).isTrue();
        assertThat(store.load().resources()).isEmpty();
    }

    @Test
    void secondSaveReplacesPreviousState() {
        store.save(resources(resource("a", null)));

        store.save(resources(resource("b", null)));

        assertThat(store.load().resources())
                .extracting(CurrentResourceState::logicalId)
                .containsExactly("b");
    }

    @Test
    void saveCreatesMissingParentDirectories() {
        Path nested = tempDir.resolve("nested/deep").resolve(CurrentStateStore.DEFAULT_FILE_NAME);
        CurrentStateStore nestedStore = new CurrentStateStore(nested);

        nestedStore.save(resources(resource("ec2.myEc2", "i-0abc123")));

        assertThat(Files.exists(nested)).isTrue();
        assertThat(nestedStore.load().resources())
                .extracting(CurrentResourceState::logicalId)
                .containsExactly("ec2.myEc2");
    }

    @Test
    void savedFileMatchesTheDocumentedSchema() throws IOException {
        store.save(resources(resource("ec2.myEc2", "i-0abc123")));

        JsonObject root = readStateFileAsJson();
        assertThat(root.get("version").getAsInt()).isEqualTo(1);
        assertThat(root.getAsJsonArray("resources")).hasSize(1);

        JsonObject entry = firstEntryAsJson();
        assertThat(entry.get("kindType").getAsString()).isEqualTo(TestKind.class.getName());
        assertThat(entry.get("kindValue").getAsString()).isEqualTo("EC2");
        assertThat(entry.has("requiredFields")).isFalse();
    }

    /** 손으로 만든 상태 파일을 그대로 심는다 (실패 계약 테스트용). */
    private void writeStateFile(String json) throws IOException {
        Files.writeString(stateFile, json, StandardCharsets.UTF_8);
    }

    private static String stateJson(int version, String entryJson) {
        return "{\"version\":" + version + ",\"resources\":[" + entryJson + "]}";
    }

    private static String entryJson(String kindType, String kindValue) {
        return "{\"kindType\":\""
                + kindType
                + "\",\"kindValue\":\""
                + kindValue
                + "\",\"logicalId\":\"ec2.myEc2\",\"config\":{},\"dependencies\":[]}";
    }

    @Test
    void loadThrowsOnMalformedJson() throws IOException {
        writeStateFile("{\"version\": 1, \"resources\": [");

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining(stateFile.toString())
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void loadThrowsWhenRootIsNotAnObject() throws IOException {
        writeStateFile("[]");

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsOnEmptyFile() throws IOException {
        writeStateFile("");

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsOnUnknownSchemaVersion() throws IOException {
        writeStateFile("{\"version\":2,\"resources\":[]}");

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("2")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenVersionIsMissing() throws IOException {
        writeStateFile("{\"resources\":[]}");

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenKindClassIsNotFound() throws IOException {
        writeStateFile(stateJson(1, entryJson("com.example.NoSuchKind", "EC2")));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("com.example.NoSuchKind")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenKindTypeIsNotAnEnum() throws IOException {
        writeStateFile(stateJson(1, entryJson("java.lang.String", "EC2")));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenKindTypeIsAnEnumButNotAKind() throws IOException {
        writeStateFile(stateJson(1, entryJson("java.lang.Thread$State", "NEW")));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("java.lang.Thread$State")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenNoConstantMatchesKindValue() throws IOException {
        writeStateFile(stateJson(1, entryJson(TestKind.class.getName(), "RDS")));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("RDS")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenSeveralConstantsShareKindValue() throws IOException {
        writeStateFile(stateJson(1, entryJson(DuplicateValueKind.class.getName(), "SAME")));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("SAME")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenResourcesArrayIsMissing() throws IOException {
        writeStateFile("{\"version\":1}");

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("resources")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenLogicalIdIsMissing() throws IOException {
        writeStateFile(
                stateJson(
                        1,
                        "{\"kindType\":\""
                                + TestKind.class.getName()
                                + "\",\"kindValue\":\"EC2\",\"config\":{},\"dependencies\":[]}"));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("logicalId")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenConfigValueIsNull() throws IOException {
        writeStateFile(
                stateJson(
                        1,
                        "{\"kindType\":\""
                                + TestKind.class.getName()
                                + "\",\"kindValue\":\"EC2\",\"logicalId\":\"ec2.myEc2\","
                                + "\"config\":{\"port\":null},\"dependencies\":[]}"));

        assertThatThrownBy(() -> store.load())
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining("port")
                .hasMessageContaining(stateFile.toString());
    }

    @Test
    void loadThrowsWhenStateFileCannotBeRead() {
        CurrentStateStore directoryStore = new CurrentStateStore(tempDir);

        assertThatThrownBy(directoryStore::load)
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining(tempDir.toString())
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void saveThrowsWhenKindIsNotAnEnum() {
        CurrentResourceState notEnum =
                new CurrentResourceState(
                        new NotEnumKind(), "ec2.myEc2", Map.of(), List.of(), Set.of(), null);

        assertThatThrownBy(() -> store.save(resources(notEnum)))
                .isInstanceOf(StateStoreException.class)
                .hasMessageContaining(NotEnumKind.class.getName())
                .hasMessageContaining("ec2.myEc2")
                .hasMessageContaining(stateFile.toString());
        assertThat(Files.exists(stateFile)).isFalse();
    }
}
