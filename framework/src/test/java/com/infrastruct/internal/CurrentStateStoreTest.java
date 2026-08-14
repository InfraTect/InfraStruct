package com.infrastruct.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.infrastruct.spi.CurrentResources;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentStateStoreTest {

    @Test
    void canBeInstantiatedWithNoArgs() {
        assertThat(new CurrentStateStore()).isNotNull();
    }

    @Test
    void loadReturnsEmptyCurrentResourcesWhenNothingStored() {
        CurrentResources loaded = new CurrentStateStore().load();

        assertThat(loaded).isNotNull();
        assertThat(loaded.resources()).isEmpty();
    }

    @Test
    void saveReturnsWithoutThrowing() {
        CurrentStateStore store = new CurrentStateStore();

        assertThatCode(() -> store.save(new CurrentResources(List.of())))
                .doesNotThrowAnyException();
    }
}
