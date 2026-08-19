package com.infrastruct.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.ToNumberPolicy;
import com.infrastruct.spi.CurrentResourceState;
import com.infrastruct.spi.CurrentResources;
import com.infrastruct.spi.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 마지막으로 apply 된 실제 상태({@link CurrentResources})를 JSON 파일로 저장하고 다시 복원하는 내부 모듈.
 *
 * <p>파이프라인에서의 자리: compare 직전에 {@link #load()} 로 이전 상태를 읽어 오고, apply 가 끝난 뒤 Applier 가 새로 만들어 준
 * {@link CurrentResources} 를 저장한다.
 */
public final class CurrentStateStore {

    /** 상태 파일의 관례적 이름. 경로 조합은 호출부(파이프라인)의 몫이다. */
    public static final String DEFAULT_FILE_NAME = "infras.state.json";

    /** 이 구현이 읽고 쓸 수 있는 스키마 버전. */
    private static final int SCHEMA_VERSION = 1;

    private static final Gson GSON =
            new GsonBuilder()
                    .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                    .setPrettyPrinting()
                    .create();

    private final Path stateFile;

    /**
     * 읽고 쓸 상태 파일 경로를 고정한다.
     *
     * <p>경로를 밖에서 받는 이유: 무인자 생성자를 두면 테스트가 레포 루트에 상태 파일을 만든다. 파일이 아직 없어도 된다 — {@link #load()} 가 그 경우를
     * "최초 실행"으로 다룬다.
     *
     * @param stateFile 읽고 쓸 상태 파일 경로 (존재하지 않아도 된다)
     * @throws NullPointerException {@code stateFile} 이 {@code null} 인 경우
     */
    public CurrentStateStore(Path stateFile) {
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile");
    }

    /**
     * 저장된 상태 파일을 읽어 {@link CurrentResources} 로 복원한다.
     *
     * <p>파일이 아직 없으면 "최초 실행"으로 보고 빈 상태를 돌려준다 — 이것이 이 클래스에서 유일하게 예외를 삼키는 자리다. 나머지 실패(읽기 실패, 깨진 JSON,
     * 모르는 스키마)는 전부 예외로 드러낸다. 빈 값으로 뭉개면 프레임워크가 이미 떠 있는 자원을 전부 다시 만들기 때문이다.
     *
     * @return 복원된 현재 상태. 파일이 없으면 원소 없는 {@link CurrentResources}
     * @throws StateStoreException 파일을 읽을 수 없거나, 내용이 이 구현이 아는 스키마가 아닌 경우
     */
    public CurrentResources load() {
        String json;
        try {
            json = Files.readString(stateFile, StandardCharsets.UTF_8);
        } catch (NoSuchFileException ignored) {
            // 최초 실행 — 저장된 상태가 없다는 도메인 사실이므로 예외가 아니다.
            return new CurrentResources(List.of());
        } catch (IOException e) {
            throw new StateStoreException("상태 파일을 읽지 못했다: " + stateFile, e);
        }

        StateFile parsed;
        try {
            parsed = GSON.fromJson(json, StateFile.class);
        } catch (JsonParseException e) {
            throw new StateStoreException("상태 파일의 JSON 을 해석하지 못했다: " + stateFile, e);
        }
        if (parsed == null) { // 빈 파일이거나 "null" 리터럴
            throw new StateStoreException("상태 파일이 비어 있다: " + stateFile);
        }

        if (parsed.version() != SCHEMA_VERSION) {
            throw new StateStoreException(
                    "모르는 상태 파일 버전이다(version="
                            + parsed.version()
                            + ", 지원="
                            + SCHEMA_VERSION
                            + "): "
                            + stateFile);
        }

        List<ResourceEntry> entries = requireField(parsed.resources(), "resources");
        List<CurrentResourceState> states = new ArrayList<>(entries.size());
        for (ResourceEntry entry : entries) {
            states.add(toState(entry));
        }
        return new CurrentResources(states);
    }

    /**
     * 현재 상태를 JSON 파일로 저장한다.
     *
     * <p>임시 파일에 먼저 쓰고 {@link StandardCopyOption#ATOMIC_MOVE} 로 옮긴다 — 쓰는 도중에 죽어도 기존 상태 파일이 반쪽짜리 내용으로
     * 덮이지 않는다. 임시 파일을 대상과 같은 폴더에 만드는 이유는 같은 파일시스템이어야 원자적 이동이 성립하기 때문이다.
     *
     * @param resources 저장할 현재 상태
     * @throws NullPointerException {@code resources} 가 {@code null} 인 경우
     * @throws StateStoreException 저장할 수 없는 상태이거나(enum 이 아닌 Kind) 파일을 쓰지 못한 경우
     */
    public void save(CurrentResources resources) {
        Objects.requireNonNull(resources, "resources");

        List<ResourceEntry> entries = resources.resources().stream().map(this::toEntry).toList();
        String json = GSON.toJson(new StateFile(SCHEMA_VERSION, entries));

        Path target = stateFile.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null) {
            throw new StateStoreException("상태 파일 경로에 부모 디렉터리가 없다: " + stateFile);
        }
        Path temp = null;
        try {
            Files.createDirectories(directory);
            // 임시 파일이 대상과 같은 폴더에 있어야 같은 파일시스템이 보장돼 ATOMIC_MOVE 가 성립한다.
            temp = Files.createTempFile(directory, "infras.state", ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            temp = null; // 옮겼으니 지울 것이 없다.
        } catch (IOException e) {
            throw new StateStoreException("상태 파일을 저장하지 못했다: " + stateFile, e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * 실패 경로에서 임시 파일을 남기지 않는다. 지우다 또 실패해도 원래 예외를 덮지 않는다.
     *
     * @param path 지울 임시 파일. 이미 옮겨졌으면 {@code null} 이 온다
     */
    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일이 남는 것보다 원래 실패를 그대로 보여주는 편이 낫다.
        }
    }

    /**
     * 상태 파일에서 빠질 수 없는 값인지 확인한다.
     *
     * @param <T> 확인할 값의 타입
     * @param value 상태 파일에서 읽어 온 값
     * @param name 사용자에게 보여줄 필드 이름
     * @return {@code null} 이 아닌 {@code value}
     * @throws StateStoreException {@code value} 가 {@code null} 인 경우
     */
    private <T> T requireField(T value, String name) {
        if (value == null) {
            throw new StateStoreException("상태 파일 항목에 '" + name + "' 가 없다: " + stateFile);
        }
        return value;
    }

    private CurrentResourceState toState(ResourceEntry entry) {
        requireField(entry, "resources[]");
        String kindType = requireField(entry.kindType(), "kindType");
        String kindValue = requireField(entry.kindValue(), "kindValue");
        String logicalId = requireField(entry.logicalId(), "logicalId");
        Map<String, Object> config = requireField(entry.config(), "config");
        List<String> dependencies = requireField(entry.dependencies(), "dependencies");

        return new CurrentResourceState(
                restoreKind(kindType, kindValue),
                logicalId,
                normalizeConfig(config),
                dependencies,
                Set.of(), // CurrentResourceState 는 requiredFields 를 쓰지 않는다
                entry.physicalId()); // null 허용
    }

    private ResourceEntry toEntry(CurrentResourceState state) {
        Kind kind = state.kind();
        if (!(kind instanceof Enum<?> constant)) {
            throw new StateStoreException(
                    "Kind 는 enum 이어야 저장할 수 있다("
                            + (kind == null ? "null" : kind.getClass().getName())
                            + ", logicalId="
                            + state.logicalId()
                            + "): "
                            + stateFile);
        }
        return new ResourceEntry(
                // getClass() 가 아니다 — 상수별 본문을 가진 enum 은 익명 하위 클래스라 이름이 Outer$1 이 된다.
                constant.getDeclaringClass().getName(),
                kind.value(),
                state.logicalId(),
                state.physicalId(),
                state.config(),
                state.dependencies());
    }

    private Kind restoreKind(String kindType, String kindValue) {
        Class<?> type;
        try {
            type = Class.forName(kindType);
        } catch (ClassNotFoundException e) {
            throw new StateStoreException(
                    "kindType 클래스를 찾을 수 없다(" + kindType + "): " + stateFile, e);
        }
        if (!type.isEnum() || !Kind.class.isAssignableFrom(type)) {
            throw new StateStoreException(
                    "kindType 이 Kind 를 구현한 enum 이 아니다(" + kindType + "): " + stateFile);
        }

        Kind found = null;
        for (Object constant : type.getEnumConstants()) {
            Kind candidate = (Kind) constant;
            if (kindValue.equals(candidate.value())) {
                if (found != null) {
                    // 어느 쪽을 골라도 틀릴 수 있으므로 조용히 첫 번째를 고르지 않는다.
                    throw new StateStoreException(
                            "kindValue 가 여러 enum 상수와 겹친다("
                                    + kindType
                                    + "."
                                    + kindValue
                                    + "): "
                                    + stateFile);
                }
                found = candidate;
            }
        }
        if (found != null) {
            return found;
        }
        throw new StateStoreException(
                "kindValue 에 해당하는 enum 상수가 없다(" + kindType + "." + kindValue + "): " + stateFile);
    }

    /**
     * 복원된 {@code config} 값을 정규 타입으로 통일한다 — 정수는 {@code Long}, 소수는 {@code Double}.
     *
     * <p>JSON 에는 int/long 구분이 없으므로 되살릴 수 없다. 값의 크기로 추측해 {@code Integer} 로 좁히면 {@code long sizeGb =
     * 100} 이 {@code Integer 100} 으로 돌아와, {@code Comparator} 의 {@code Objects.equals} 가 매번 UPDATE 를
     * 만들어 낸다(apply 해도 다시 같은 값이 저장되므로 사라지지 않는다). 그래서 없는 구분을 복원하는 대신 한쪽으로 통일한다.
     *
     * <p>Gson 의 {@code LONG_OR_DOUBLE} 이 이미 {@code Long}/{@code Double} 을 주므로 여기서 숫자를 손댈 일은 없다. 이
     * 메서드가 실제로 하는 일은 {@code null} 값을 걸러내는 것뿐이다.
     *
     * <p>짝이 되는 규칙: {@code DesiredStateCreator} 도 config 를 채울 때 같은 정규 타입을 써야 한다. 한쪽만 지키면 반대 방향으로 유령
     * diff 가 생긴다.
     *
     * @param raw Gson 이 돌려준 config 맵
     * @return 정규 타입으로 통일된 새 맵
     * @throws StateStoreException 값이 {@code null} 인 항목이 있는 경우
     */
    private Map<String, Object> normalizeConfig(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object value = e.getValue();
            if (value == null) {
                // Map.copyOf 가 맥락 없는 NPE 를 던지기 전에 키 이름과 경로를 담아 먼저 막는다.
                throw new StateStoreException(
                        "config 값이 null 이다(key=" + e.getKey() + "): " + stateFile);
            }
            normalized.put(e.getKey(), value);
        }
        return normalized;
    }

    /** 상태 파일 전체. */
    private record StateFile(int version, List<ResourceEntry> resources) {}

    /** 자원 하나. 필드 이름이 곧 JSON 키다. */
    private record ResourceEntry(
            String kindType,
            String kindValue,
            String logicalId,
            String physicalId,
            Map<String, Object> config,
            List<String> dependencies) {}
}
