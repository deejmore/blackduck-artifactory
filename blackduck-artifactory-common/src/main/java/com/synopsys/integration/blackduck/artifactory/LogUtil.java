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
package com.synopsys.integration.blackduck.artifactory;

import com.synopsys.integration.log.IntLogger;

public class LogUtil {
    private LogUtil() {
        throw new IllegalStateException("Utility class.");
    }

    public static void start(final IntLogger logger, final String functionName, final TriggerType triggerType) {
        if (triggerType.equals(TriggerType.STARTUP)) {
            logger.info(String.format("Starting %s for %s...", functionName, triggerType.getLogName()));
        } else {
            logger.info(String.format("Starting %s from %s...", functionName, triggerType.getLogName()));
        }
    }

    public static void finish(final IntLogger logger, final String functionName, final TriggerType triggerType) {
        logger.info(String.format("...completed %s %s.", functionName, triggerType.getLogName()));
    }
}
