/**
 * blackduck-artifactory-common
 *
 * Copyright (c) 2019 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.blackduck.artifactory.configuration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.blackduck.artifactory.configuration.model.ConfigValidationReport;
import com.synopsys.integration.blackduck.artifactory.configuration.model.PropertyGroupReport;
import com.synopsys.integration.blackduck.artifactory.configuration.model.PropertyValidationResult;
import com.synopsys.integration.blackduck.artifactory.modules.ModuleConfig;
import com.synopsys.integration.blackduck.artifactory.modules.ModuleManager;
import com.synopsys.integration.builder.BuilderStatus;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ConfigValidationService {
    private final IntLogger logger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));

    private static final int LINE_CHARACTER_LIMIT = 100;
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String BLOCK_SEPARATOR = LINE_SEPARATOR + StringUtils.repeat("-", LINE_CHARACTER_LIMIT) + LINE_SEPARATOR;

    private final ModuleManager moduleManager;
    private final PluginConfig pluginConfig;
    private final File versionFile;

    public ConfigValidationService(final ModuleManager moduleManager, final PluginConfig pluginConfig, final File versionFile) {
        this.moduleManager = moduleManager;
        this.pluginConfig = pluginConfig;
        this.versionFile = versionFile;
    }

    public ConfigValidationReport validateConfig() {
        final BuilderStatus generalBuilderStatus = new BuilderStatus();
        final PropertyGroupReport generalPropertyReport = new PropertyGroupReport("General Settings", generalBuilderStatus);
        pluginConfig.validate(generalPropertyReport);

        final List<PropertyGroupReport> propertyGroupReports = new ArrayList<>();
        for (final ModuleConfig moduleConfig : moduleManager.getAllModuleConfigs()) {
            final BuilderStatus propertyGroupBuilderStatus = new BuilderStatus();
            final PropertyGroupReport propertyGroupReport = new PropertyGroupReport(moduleConfig.getModuleName(), propertyGroupBuilderStatus);
            moduleConfig.validate(propertyGroupReport);
            propertyGroupReports.add(propertyGroupReport);
        }

        return new ConfigValidationReport(generalPropertyReport, propertyGroupReports);
    }

    public String generateStatusCheckMessage(final ConfigValidationReport configValidationReport, final boolean includeValid) {
        final String pluginVersion = getPluginVersion();
        final StringBuilder statusCheckMessage = new StringBuilder(BLOCK_SEPARATOR + String.format("Status Check: Plugin Version - %s", pluginVersion) + BLOCK_SEPARATOR);

        final PropertyGroupReport generalPropertyReport = configValidationReport.getGeneralPropertyReport();
        final String configErrorMessage = generalPropertyReport.hasError() ? "CONFIGURATION ERROR" : "";
        statusCheckMessage.append(String.format("General Settings: %s", configErrorMessage)).append(LINE_SEPARATOR);
        appendPropertyGroupReport(statusCheckMessage, generalPropertyReport, includeValid);
        statusCheckMessage.append(BLOCK_SEPARATOR);

        for (final PropertyGroupReport modulePropertyReport : configValidationReport.getModulePropertyReports()) {
            final Optional<ModuleConfig> moduleConfigsByName = moduleManager.getFirstModuleConfigByName(modulePropertyReport.getPropertyGroupName());
            final boolean enabled = moduleConfigsByName.isPresent() && moduleConfigsByName.get().isEnabled();
            appendPropertyReportForModule(statusCheckMessage, modulePropertyReport, enabled, includeValid);
            statusCheckMessage.append(BLOCK_SEPARATOR);
        }

        return statusCheckMessage.toString();
    }

    private void appendPropertyReportForModule(final StringBuilder statusCheckMessage, final PropertyGroupReport propertyGroupReport, final boolean enabled, final boolean includeValid) {
        final String moduleName = propertyGroupReport.getPropertyGroupName();
        final String state = enabled ? "Enabled" : "Disabled";
        final String configErrorMessage = propertyGroupReport.hasError() ? "CONFIGURATION ERROR" : "";
        final String moduleLine = String.format("%s [%s] %s", moduleName, state, configErrorMessage);
        statusCheckMessage.append(moduleLine).append(LINE_SEPARATOR);

        appendPropertyGroupReport(statusCheckMessage, propertyGroupReport, includeValid);
    }

    private void appendPropertyGroupReport(final StringBuilder statusCheckMessage, final PropertyGroupReport propertyGroupReport, final boolean includeValid) {
        for (final PropertyValidationResult propertyReport : propertyGroupReport.getPropertyReports()) {
            final Optional<String> errorMessage = propertyReport.getErrorMessage();

            final String mark = errorMessage.isPresent() ? "X" : "✔";
            final String property = propertyReport.getConfigurationProperty().getKey();
            final String reportSuffix = errorMessage.isPresent() ? String.format(LINE_SEPARATOR + "      * %s", errorMessage.get()) : "";
            final String reportLine = String.format("[%s] - %s %s", mark, property, reportSuffix);

            if (includeValid || errorMessage.isPresent()) {
                statusCheckMessage.append(wrapLine(reportLine)).append(LINE_SEPARATOR);
            }
        }

        if (!propertyGroupReport.getBuilderStatus().isValid()) {
            final String otherMessages = wrapLine(String.format("Other Messages: %s", propertyGroupReport.getBuilderStatus().getFullErrorMessage()));
            statusCheckMessage.append(otherMessages).append(LINE_SEPARATOR);
        }
    }

    private String wrapLine(final String line) {
        return WordUtils.wrap(line, LINE_CHARACTER_LIMIT, LINE_SEPARATOR + "        ", false);
    }

    private String getPluginVersion() {
        String version = "Unknown";
        try {
            version = FileUtils.readFileToString(versionFile, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            logger.debug("Failed to load plugin version.", e);
        }

        return version;
    }
}
