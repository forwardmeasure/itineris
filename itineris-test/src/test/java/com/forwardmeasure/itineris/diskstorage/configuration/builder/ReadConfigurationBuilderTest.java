// Copyright 2019 Itineris Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.forwardmeasure.itineris.diskstorage.configuration.builder;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_STALE_CONFIG;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_UPGRADE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.IDS_STORE_NAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INITIAL_ITINERIS_GRAPH_VERSION;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INITIAL_STORAGE_VERSION;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.LOCK_LOCAL_MEDIATOR_GROUP;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.TIMESTAMP_PROVIDER;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.TITAN_COMPATIBLE_VERSIONS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.forwardmeasure.itineris.core.ItinerisConfigurationException;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigElement;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigNamespace;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigOption;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.ReadConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.KCVSConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.builder.KCVSConfigurationBuilder;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProviders;
import com.forwardmeasure.itineris.graphdb.configuration.ItinerisConstants;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for building ReadConfiguration
 */
@ExtendWith(MockitoExtension.class)
public class ReadConfigurationBuilderTest {

    @Mock
    private ReadConfiguration localConfig;

    @Mock
    private BasicConfiguration localBasicConfiguration;

    @Mock
    private ModifiableConfiguration overwrite;

    @Mock
    private KeyColumnValueStoreManager storeManager;

    @Mock
    private ModifiableConfigurationBuilder modifiableConfigurationBuilder;

    @Mock
    private StoreFeatures features;

    @Mock
    private ModifiableConfiguration globalWrite;

    @Mock
    private KCVSConfigurationBuilder kcvsConfigurationBuilder;

    @Mock
    private KCVSConfiguration keyColumnValueStoreConfiguration;

    @Mock
    private ReadConfiguration readConfiguration;

    @Mock
    private TimestampProviders timestampProviders;

    private final ReadConfigurationBuilder readConfigurationBuilder = new ReadConfigurationBuilder();

    public static Stream<ConfigOption.Type> managedConfigOptionTypes() {
        return ImmutableSet.of(
                ConfigOption.Type.FIXED,
                ConfigOption.Type.GLOBAL_OFFLINE,
                ConfigOption.Type.GLOBAL).stream();
    }

    public static Stream<ConfigOption.Type> notManagedConfigOptionTypes() {
        final Set<ConfigOption.Type> managedConfigTypeSet = managedConfigOptionTypes().collect(Collectors.toSet());
        return Arrays.stream(ConfigOption.Type.values()).filter(c -> !managedConfigTypeSet.contains(c));
    }

    @BeforeEach
    public void setUp() {
        when(kcvsConfigurationBuilder.buildStandaloneGlobalConfiguration(storeManager, localBasicConfiguration))
                .thenReturn(keyColumnValueStoreConfiguration);
        when(modifiableConfigurationBuilder.buildGlobalWrite(keyColumnValueStoreConfiguration))
                .thenReturn(globalWrite);
    }

    @Test
    public void shouldOverwriteStoreManagerName() {
        when(storeManager.getFeatures()).thenReturn(features);

        buildConfiguration();

        verify(overwrite).set(eq(LOCK_LOCAL_MEDIATOR_GROUP), any());
    }

    @Test
    public void shouldNotOverwriteStoreManagerName() {
        when(localBasicConfiguration.has(LOCK_LOCAL_MEDIATOR_GROUP)).thenReturn(true);
        when(storeManager.getFeatures()).thenReturn(features);

        buildConfiguration();

        verify(overwrite, never()).set(eq(LOCK_LOCAL_MEDIATOR_GROUP), any());
    }

    @Test
    public void shouldFreezeModifiableConfiguration() {
        when(storeManager.getFeatures()).thenReturn(features);

        buildConfiguration();

        verify(globalWrite).freezeConfiguration();
    }

    @Test
    public void shouldNotSetupTimestampProviderOnExistedOne() {
        when(localBasicConfiguration.has(any()))
                .thenAnswer(invocation -> TIMESTAMP_PROVIDER.equals(invocation.getArguments()[0]));

        buildConfiguration();

        verify(globalWrite, never()).set(eq(TIMESTAMP_PROVIDER), any());
    }

    @Test
    public void shouldSetupDefaultTimestampProvider() {
        when(storeManager.getFeatures()).thenReturn(features);

        buildConfiguration();

        verify(globalWrite).set(TIMESTAMP_PROVIDER, TIMESTAMP_PROVIDER.getDefaultValue());
    }

    @Test
    public void shouldSetupBackendPreferenceTimestampProvider() {
        when(storeManager.getFeatures()).thenReturn(features);
        when(features.hasTimestamps()).thenReturn(true);
        when(features.getPreferredTimestamps()).thenReturn(timestampProviders);

        buildConfiguration();

        verify(globalWrite).set(TIMESTAMP_PROVIDER, timestampProviders);
    }

    @Test
    public void shouldSetupItinerisAndStorageVersions() {
        when(storeManager.getFeatures()).thenReturn(features);

        buildConfiguration();

        verify(globalWrite).set(INITIAL_ITINERIS_GRAPH_VERSION, ItinerisConstants.VERSION);
        verify(globalWrite).set(INITIAL_STORAGE_VERSION, ItinerisConstants.STORAGE_VERSION);
    }

    @Test
    public void shouldNotFreezeModifiableConfiguration() {
        when(globalWrite.isFrozen()).thenReturn(true);
        globalWriteInitialItinerisVersionMock();

        buildConfiguration();

        verify(globalWrite, never()).freezeConfiguration();
    }

    @Test
    public void shouldBuildReadConfiguration() {
        when(storeManager.getFeatures()).thenReturn(features);
        when(keyColumnValueStoreConfiguration.asReadConfiguration()).thenReturn(readConfiguration);

        ReadConfiguration result = buildConfiguration();

        assertEquals(readConfiguration, result);
    }

    @Test
    public void shouldSetupVersionsWithDisallowedUpgradeFromNoInitialStorageVersion() {
        frozenGlobalWriteWithAllowUpgradeMock(true);
        globalWriteInitialItinerisVersionMock();

        buildConfiguration();

        verifySetupVersionsWithDisallowedUpgrade();
    }

    @Test
    public void shouldSetupVersionsWithDisallowedUpgradeFromInitialStorageVersion() {
        frozenGlobalWriteWithAllowUpgradeMock(true);
        when(globalWrite.has(INITIAL_STORAGE_VERSION)).thenReturn(true);
        concreteStorageVersionMock(Integer.parseInt(ItinerisConstants.STORAGE_VERSION) - 1);

        buildConfiguration();

        verifySetupVersionsWithDisallowedUpgrade();
    }

    @Test
    public void shouldThrowExceptionWhenStorageVersionLessThenInternalStorageVersion() {
        frozenGlobalWriteWithAllowUpgradeMock(true);
        when(globalWrite.has(INITIAL_STORAGE_VERSION)).thenReturn(true);
        concreteStorageVersionMock(Integer.parseInt(ItinerisConstants.STORAGE_VERSION) + 1);

        ItinerisException exception = assertThrows(ItinerisException.class, this::buildConfiguration);

        assertEquals(
                String.format(ReadConfigurationBuilder.BACKLEVEL_STORAGE_VERSION_EXCEPTION,
                        globalWrite.get(INITIAL_STORAGE_VERSION), ItinerisConstants.STORAGE_VERSION, null),
                exception.getMessage());
    }

    @Test
    public void shouldNotSetupVersionsWithDisallowedUpgradeOnEqualStorageVersions() {
        frozenGlobalWriteWithAllowUpgradeMock(true);
        when(globalWrite.has(INITIAL_STORAGE_VERSION)).thenReturn(true);
        concreteStorageVersionMock(Integer.parseInt(ItinerisConstants.STORAGE_VERSION));

        buildConfiguration();

        verify(globalWrite, never()).set(ALLOW_UPGRADE, false);
    }

    @Test
    public void shouldThrowExceptionOnDisallowedUpgradeAndNonEqualStorageVersions() {
        frozenGlobalWriteWithAllowUpgradeMock(false);

        ItinerisException exception = assertThrows(ItinerisException.class, this::buildConfiguration);

        String storageVersion = (globalWrite.has(INITIAL_STORAGE_VERSION)) ? globalWrite.get(INITIAL_STORAGE_VERSION)
                : "1";

        assertEquals(
                String.format(ReadConfigurationBuilder.INCOMPATIBLE_STORAGE_VERSION_EXCEPTION,
                        storageVersion, ItinerisConstants.STORAGE_VERSION, null),
                exception.getMessage());
    }

    @Test
    public void shouldSetTitanIDStoreNameWhenKeystoreNotExists() {
        when(globalWrite.isFrozen()).thenReturn(true);
        allowUpgradeWithItinerisIDStoreNameMock();
        globalWriteTitanCompatibleVersionMock();

        buildConfiguration();

        verify(overwrite).set(IDS_STORE_NAME, ItinerisConstants.TITAN_ID_STORE_NAME);
    }

    @Test
    public void shouldNotSetTitanIDStoreNameWhenKeystoreExists() {
        when(globalWrite.isFrozen()).thenReturn(true);
        allowUpgradeWithItinerisIDStoreNameMock();
        globalWriteTitanCompatibleVersionMock();
        when(keyColumnValueStoreConfiguration.get(IDS_STORE_NAME.getName(), IDS_STORE_NAME.getDatatype()))
                .thenReturn("test_value");

        buildConfiguration();

        verify(overwrite, never()).set(IDS_STORE_NAME, ItinerisConstants.TITAN_ID_STORE_NAME);
    }

    @ParameterizedTest
    @MethodSource("managedConfigOptionTypes")
    public void shouldThrowExceptionOnDisallowedManagedOverwritesWithDiscrepanciesOptions(
            ConfigOption.Type managedType) {
        when(globalWrite.isFrozen()).thenReturn(true);
        upgradeWithStaleConfigMock(false);
        globalWriteInitialItinerisVersionMock();
        ConfigOption childOption = includeChildOptionIntoLocalBasicConfiguration(managedType);

        ItinerisConfigurationException exception = assertThrows(ItinerisConfigurationException.class,
                this::buildConfiguration);

        Set<String> optionsWithDiscrepancies = Collections.singleton(childOption.getName());
        final String template = "Local settings present for one or more globally managed options: [%s].  These options are controlled through the %s interface; local settings have no effect.";
        String expectedExceptionMessage = String.format(template, Joiner.on(", ").join(optionsWithDiscrepancies),
                ManagementSystem.class.getSimpleName());

        assertEquals(expectedExceptionMessage, exception.getMessage());
    }

    @Test
    public void shouldPassOnDisallowedManagedOverwritesWithNoOptions() {
        when(globalWrite.isFrozen()).thenReturn(true);
        upgradeWithStaleConfigMock(false);
        globalWriteInitialItinerisVersionMock();

        assertDoesNotThrow(this::buildConfiguration);
    }

    @ParameterizedTest
    @MethodSource("notManagedConfigOptionTypes")
    public void shouldPassOnDisallowedManagedOverwritesWithNoDiscrepanciesOptions(ConfigOption.Type notManagedType) {
        when(globalWrite.isFrozen()).thenReturn(true);
        upgradeWithStaleConfigMock(false);
        globalWriteInitialItinerisVersionMock();
        includeChildOptionIntoLocalBasicConfiguration(notManagedType);

        assertDoesNotThrow(this::buildConfiguration);
    }

    @ParameterizedTest
    @MethodSource("managedConfigOptionTypes")
    public void shouldPassOnAllowedManagedOverwritesWithDiscrepanciesOptions(ConfigOption.Type managedType) {
        when(globalWrite.isFrozen()).thenReturn(true);
        upgradeWithStaleConfigMock(true);
        globalWriteInitialItinerisVersionMock();
        includeChildOptionIntoLocalBasicConfiguration(managedType);

        assertDoesNotThrow(this::buildConfiguration);
    }

    private ReadConfiguration buildConfiguration() {
        return readConfigurationBuilder.buildGlobalConfiguration(localConfig, localBasicConfiguration,
                overwrite, storeManager, modifiableConfigurationBuilder, kcvsConfigurationBuilder);
    }

    private void frozenGlobalWriteWithAllowUpgradeMock(boolean upgradeAllowed) {
        when(globalWrite.isFrozen()).thenReturn(true);

        when(localBasicConfiguration.has(any()))
                .thenAnswer(invocation -> ALLOW_UPGRADE.equals(invocation.getArguments()[0]));

        when(localBasicConfiguration.get(eq(ALLOW_UPGRADE))).thenReturn(upgradeAllowed);
    }

    private void globalWriteInitialItinerisVersionMock() {
        when(globalWrite.get(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if (INITIAL_STORAGE_VERSION.equals(argument)) {
                return ItinerisConstants.STORAGE_VERSION;
            }
            if (INITIAL_ITINERIS_GRAPH_VERSION.equals(argument)) {
                return "";
            }
            return ((ConfigOption) argument).get(null);
        });
    }

    private void globalWriteTitanCompatibleVersionMock() {
        when(globalWrite.get(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if (INITIAL_STORAGE_VERSION.equals(argument)) {
                return ItinerisConstants.STORAGE_VERSION;
            }
            if (TITAN_COMPATIBLE_VERSIONS.equals(argument)) {
                return ItinerisConstants.TITAN_COMPATIBLE_VERSIONS.get(0);
            }
            return null;
        });
    }

    private void upgradeWithStaleConfigMock(boolean allowStaleConfig) {
        when(localBasicConfiguration.has(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            return ALLOW_UPGRADE.equals(argument) || ALLOW_STALE_CONFIG.equals(argument);
        });

        when(localBasicConfiguration.get(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if (ALLOW_UPGRADE.equals(argument)) {
                return true;
            }
            if (ALLOW_STALE_CONFIG.equals(argument)) {
                return allowStaleConfig;
            }
            return null;
        });
    }

    private void allowUpgradeWithItinerisIDStoreNameMock() {
        when(localBasicConfiguration.has(any()))
                .thenAnswer(invocation -> ALLOW_UPGRADE.equals(invocation.getArguments()[0]));

        when(localBasicConfiguration.get(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if (ALLOW_UPGRADE.equals(argument)) {
                return true;
            }
            if (IDS_STORE_NAME.equals(argument)) {
                return ItinerisConstants.ITINERIS_GRAPH_ID_STORE_NAME;
            }
            return null;
        });
    }

    private void concreteStorageVersionMock(int initialStorageVersion) {
        when(globalWrite.get(any())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if (INITIAL_STORAGE_VERSION.equals(argument)) {
                return Integer.toString(initialStorageVersion);
            }
            if (INITIAL_ITINERIS_GRAPH_VERSION.equals(argument)) {
                return "";
            }
            return null;
        });
    }

    private ConfigOption includeChildOptionIntoLocalBasicConfiguration(ConfigOption.Type type) {
        // Parent
        ConfigNamespace root = new ConfigNamespace(null, "root", "root_description");

        // Child option. Included in optionsWithDiscrepancies when the type is managed
        ConfigOption childOption = new ConfigOption(root, "child_name", "child_description", type, String.class);

        ConfigElement.PathIdentifier identifier = ConfigElement.parse(root, "child_name");
        when(localBasicConfiguration.getAll()).thenReturn(Collections.singletonMap(identifier, new Object()));

        return childOption;
    }

    private void verifySetupVersionsWithDisallowedUpgrade() {
        verify(globalWrite).set(INITIAL_ITINERIS_GRAPH_VERSION, ItinerisConstants.VERSION);
        verify(globalWrite).set(TITAN_COMPATIBLE_VERSIONS, ItinerisConstants.VERSION);
        verify(globalWrite).set(INITIAL_STORAGE_VERSION, ItinerisConstants.STORAGE_VERSION);
        verify(globalWrite).set(ALLOW_UPGRADE, false);
    }
}
