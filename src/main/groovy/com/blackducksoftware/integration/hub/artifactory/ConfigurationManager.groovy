package com.blackducksoftware.integration.hub.artifactory

import javax.annotation.PostConstruct

import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.util.DefaultPropertiesPersister

import com.blackducksoftware.integration.hub.artifactory.inspect.HubClient

@Component
class ConfigurationManager {
    @Autowired
    HubClient hubClient

    @Autowired
    RestTemplateContainer restTemplateContainer

    @Autowired
    ArtifactoryRestClient artifactoryRestClient

    @Autowired
    ConfigurationProperties configurationProperties

    File userSpecifiedProperties

    @PostConstruct
    void init() {
        File configDirectory = new File (configurationProperties.currentUserDirectory, "config")
        if (!configDirectory.exists()) {
            configDirectory.mkdirs()
        }
        userSpecifiedProperties = new File (configDirectory, "application.properties")
        if (!userSpecifiedProperties.exists()) {
            persistValues()
        }
    }

    boolean needsHubConfigUpdate() {
        StringUtils.isBlank(configurationProperties.hubUrl) || StringUtils.isBlank(configurationProperties.hubUsername) || StringUtils.isBlank(configurationProperties.hubPassword)
    }

    boolean needsArtifactoryUpdate() {
        StringUtils.isBlank(configurationProperties.artifactoryUrl) || StringUtils.isBlank(configurationProperties.artifactoryUsername) || StringUtils.isBlank(configurationProperties.artifactoryPassword) || StringUtils.isBlank(configurationProperties.hubArtifactoryMode) || StringUtils.isBlank(configurationProperties.hubArtifactoryWorkingDirectoryPath)
    }

    boolean needsArtifactoryInspectUpdate() {
        StringUtils.isBlank(configurationProperties.hubArtifactoryInspectRepoKey)
    }

    void updateHubConfigValues(Console console, PrintStream out) {
        out.println('Updating Hub Server Config - just hit enter to make no change to a value:')

        configurationProperties.hubUrl = setValueFromInput(console, out, "Hub Server Url", configurationProperties.hubUrl)
        configurationProperties.hubTimeout = setValueFromInput(console, out, "Hub Server Timeout", configurationProperties.hubTimeout)
        configurationProperties.hubUsername = setValueFromInput(console, out, "Hub Server Username", configurationProperties.hubUsername)
        configurationProperties.hubPassword = setPasswordFromInput(console, out, "Hub Server Password", configurationProperties.hubPassword)

        persistValues()

        boolean ok = false
        try {
            hubClient.testHubConnection()
            out.println 'Your Hub configuration is valid and a successful connection to the Hub was established.'
            ok = true
        } catch (Exception e) {
            out.println("Your Hub configuration is not valid: ${e.message}")
        }

        if (!ok) {
            out.println("You may need to manually edit the 'config/application.properties' file to provide proxy details. If you wish to re-enter the Hub configuration, enter 'y', otherwise, just press <enter> to continue.")
            String userValue = StringUtils.trimToEmpty(console.readLine())
            if ('y' == userValue) {
                updateHubConfigValues(console, out)
            }
        }
    }

    void updateArtifactoryValues(Console console, PrintStream out) {
        configurationProperties.artifactoryUsername = setValueFromInput(console, out, "Artifactory Username", configurationProperties.artifactoryUsername)
        configurationProperties.artifactoryPassword = setPasswordFromInput(console, out, "Artifactory Password", configurationProperties.artifactoryPassword)
        configurationProperties.artifactoryUrl = setValueFromInput(console, out, "Artifactory Url", configurationProperties.artifactoryUrl)
        configurationProperties.hubArtifactoryMode = setValueFromInput(console, out, "Hub Artifactory Mode (inspect or scan)", configurationProperties.hubArtifactoryMode)
        configurationProperties.hubArtifactoryWorkingDirectoryPath = setValueFromInput(console, out, "Local Working Directory", configurationProperties.hubArtifactoryWorkingDirectoryPath)

        restTemplateContainer.init()
        persistValues()

        boolean ok = false
        try {
            String response = artifactoryRestClient.checkSystem()
            if ("OK" == response) {
                out.println("Your Artifactory configuration is valid and a successful connection to the Artifactory server was established.")
                ok = true
            } else {
                out.println("A successful connection could not be established to the Artifactory server. The response was: ${response}")
            }
        } catch (Exception e) {
            out.println("A successful connection could not be established to the Artifactory server: ${e.message}")
        }

        if (!ok) {
            out.println("You may need to manually edit the 'config/application.properties' file but if you wish to re-enter the Artifactory configuration, enter 'y', otherwise, just press <enter> to continue.")
            String userValue = StringUtils.trimToEmpty(console.readLine())
            if ('y' == userValue) {
                updateArtifactoryValues(console, out)
            }
        }
    }

    void updateArtifactoryInspectValues(Console console, PrintStream out) {
        configurationProperties.hubArtifactoryProjectName = setValueFromInput(console, out, "Hub Artifactory Project Name (optional)", configurationProperties.hubArtifactoryProjectName)
        configurationProperties.hubArtifactoryProjectVersionName = setValueFromInput(console, out, "Hub Artifactory Project Version Name (optional)", configurationProperties.hubArtifactoryProjectVersionName)
        configurationProperties.hubArtifactoryInspectRepoKey = setValueFromInput(console, out, "Artifactory Repository To Inspect", configurationProperties.hubArtifactoryInspectRepoKey)

        persistValues()

        boolean ok = false
        try {
            Map jsonResponse = artifactoryRestClient.getInfoForPath(configurationProperties.hubArtifactoryInspectRepoKey, "")
            if (jsonResponse != null && jsonResponse.children != null && jsonResponse.children.size() > 0) {
                ok = true
            }
        } catch (Exception e) {
            out.println("Could not get information for the ${configurationProperties.hubArtifactoryInspectRepoKey} repo: ${e.message}")
        }

        if (!ok) {
            out.println("No information for the ${configurationProperties.hubArtifactoryInspectRepoKey} repo could be found. You may need to manually edit the 'config/application.properties' file but if you wish to re-enter the Artifactory inspect configuration, enter 'y', otherwise, just press <enter> to continue.")
            String userValue = StringUtils.trimToEmpty(console.readLine())
            if ('y' == userValue) {
                updateArtifactoryInspectValues(console, out)
            }
        }
    }

    private persistValues() {
        Properties properties = new Properties()
        properties.setProperty("hub.url", configurationProperties.hubUrl)
        properties.setProperty("hub.timeout", configurationProperties.hubTimeout)
        properties.setProperty("hub.username", configurationProperties.hubUsername)
        properties.setProperty("hub.password", configurationProperties.hubPassword)
        properties.setProperty("hub.proxy.host", configurationProperties.hubProxyHost)
        properties.setProperty("hub.proxy.port", configurationProperties.hubProxyPort)
        properties.setProperty("hub.proxy.ignored.proxy.hosts", configurationProperties.hubProxyIgnoredProxyHosts)
        properties.setProperty("hub.proxy.username", configurationProperties.hubProxyUsername)
        properties.setProperty("hub.proxy.password", configurationProperties.hubProxyPassword)
        properties.setProperty("artifactory.url", configurationProperties.artifactoryUrl)
        properties.setProperty("artifactory.username", configurationProperties.artifactoryUsername)
        properties.setProperty("artifactory.password", configurationProperties.artifactoryPassword)
        properties.setProperty("hub.artifactory.mode", configurationProperties.hubArtifactoryMode)
        properties.setProperty("hub.artifactory.working.directory.path", configurationProperties.hubArtifactoryWorkingDirectoryPath)
        properties.setProperty("hub.artifactory.project.name", configurationProperties.hubArtifactoryProjectName)
        properties.setProperty("hub.artifactory.project.version.name", configurationProperties.hubArtifactoryProjectVersionName)
        properties.setProperty("hub.artifactory.date.time.pattern", configurationProperties.hubArtifactoryDateTimePattern)
        properties.setProperty("hub.artifactory.inspect.repo.key", configurationProperties.hubArtifactoryInspectRepoKey)
        properties.setProperty("hub.artifactory.inspect.latest.updated.cutoff", configurationProperties.hubArtifactoryInspectLatestUpdatedCutoff)
        properties.setProperty("hub.artifactory.scan.repos.to.search", configurationProperties.hubArtifactoryScanReposToSearch)
        properties.setProperty("hub.artifactory.scan.name.patterns", configurationProperties.hubArtifactoryScanNamePatterns)
        properties.setProperty("hub.artifactory.scan.latest.modified.cutoff", configurationProperties.hubArtifactoryScanLatestModifiedCutoff)

        def defaultPropertiesPersister = new DefaultPropertiesPersister()
        new FileOutputStream(userSpecifiedProperties).withStream {
            defaultPropertiesPersister.store(properties, it, null)
        }
    }

    private String setValueFromInput(Console console, PrintStream out, String propertyName, String oldValue) {
        out.print("Enter ${propertyName} (current value=\"${oldValue}\"): ")
        String userValue = StringUtils.trimToEmpty(console.readLine())
        if (StringUtils.isNotBlank(userValue)) {
            userValue
        } else {
            oldValue
        }
    }

    private String setPasswordFromInput(Console console, PrintStream out, String propertyName, String oldValue) {
        out.print("Enter ${propertyName}: ")
        char[] password = console.readPassword()
        if (null == password || password.length == 0) {
            oldValue
        } else {
            String passwordString = StringUtils.trimToEmpty(new String(password))
            if (StringUtils.isNotBlank(passwordString)) {
                passwordString
            } else {
                oldValue
            }
        }
    }
}