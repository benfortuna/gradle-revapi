/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.revapi.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.revapi.config.v1.AcceptedBreakV1;
import com.palantir.gradle.revapi.config.v1.BreakCollectionV1;
import com.palantir.gradle.revapi.config.v2.AcceptedBreakV2;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import one.util.streamex.EntryStream;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableGradleRevapiConfig.class)
public abstract class GradleRevapiConfig {
    private static final String ACCEPTED_BREAKS_V2 = "acceptedBreaksV2";

    protected abstract Map<GroupNameVersion, String> versionOverrides();

    @JsonProperty(value = "acceptedBreaks", access = Access.WRITE_ONLY)
    protected abstract Optional<BreakCollectionV1> acceptedBreaksDeserOnly();

    @JsonProperty(value = ACCEPTED_BREAKS_V2, access = Access.WRITE_ONLY)
    protected abstract Optional<Set<BreakCollection>> acceptedBreaksV2DeserOnly();

    /** Overridden by immutables. */
    @Value.Default
    @JsonProperty(value = ACCEPTED_BREAKS_V2, access = Access.READ_ONLY)
    protected Set<BreakCollection> acceptedBreaksV2() {
        Set<BreakCollection> upgradedBreaks = acceptedBreaksDeserOnly()
                .orElseGet(BreakCollectionV1::empty)
                .upgrade();

        return ImmutableSet.<BreakCollection>builder()
                .addAll(upgradedBreaks)
                .addAll(acceptedBreaksV2DeserOnly().orElseGet(Collections::emptySet))
                .build();
    }

    public final Optional<String> versionOverrideFor(GroupNameVersion groupNameVersion) {
        return Optional.ofNullable(versionOverrides().get(groupNameVersion));
    }

    public final GradleRevapiConfig addVersionOverride(GroupNameVersion groupNameVersion, String versionOverride) {
        return ImmutableGradleRevapiConfig.builder()
                .from(this)
                .putVersionOverrides(groupNameVersion, versionOverride)
                .build();
    }

    public final Set<AcceptedBreakV1> acceptedBreaksFor(GroupNameVersion groupNameVersion) {
        return Optional.ofNullable(acceptedBreaksDeserOnly().get().get(groupNameVersion.version()))
                .map(projectBreaks -> projectBreaks.forGroupAndName(groupNameVersion.groupAndName()))
                .orElseGet(Collections::emptySet);
    }

    public final GradleRevapiConfig addAcceptedBreaks(
            GroupNameVersion groupNameVersion,
            Set<AcceptedBreakV1> acceptedBreakV1s) {

        PerProject<AcceptedBreakV1> existingAcceptedBreaks =
                acceptedBreaksDeserOnly().get().getOrDefault(groupNameVersion.version(), PerProject.empty());

        PerProject<AcceptedBreakV1> newPerProject = existingAcceptedBreaks.merge(
                groupNameVersion.groupAndName(),
                acceptedBreakV1s);

        Map<Version, PerProject<AcceptedBreakV1>> newAcceptedBreaks = new HashMap<>(acceptedBreaksDeserOnly().get());
        newAcceptedBreaks.put(groupNameVersion.version(), newPerProject);

        return ImmutableGradleRevapiConfig.builder()
                .from(this)
                .acceptedBreaksDeserOnly(newAcceptedBreaks)
                .build();
    }

    public static GradleRevapiConfig empty() {
        return ImmutableGradleRevapiConfig.builder().build();
    }

    public static ObjectMapper newYamlObjectMapper() {
        return configureObjectMapper(new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)));
    }

    public static ObjectMapper newJsonObjectMapper() {
        return configureObjectMapper(new ObjectMapper())
                .enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    private static ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        return objectMapper
                .registerModule(new GuavaModule())
                .registerModule(new Jdk8Module());
    }
}
