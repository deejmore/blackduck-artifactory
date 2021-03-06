package com.synopsys.integration.blackduck.artifactory.automation

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.ArtifactResolver
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.ArtifactoryConfigurationService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.RepositoryManager
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.artifacts.ArtifactDeploymentApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.artifacts.ArtifactRetrievalApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.artifacts.PropertiesApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.plugins.PluginsApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.repositories.RepositoriesApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.searches.ArtifactSearchesAPIService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.system.ImportExportApiService
import com.synopsys.integration.blackduck.artifactory.automation.artifactory.api.system.SystemApiService
import com.synopsys.integration.blackduck.artifactory.automation.docker.DockerService
import com.synopsys.integration.blackduck.artifactory.automation.plugin.BlackDuckPluginApiService
import com.synopsys.integration.blackduck.artifactory.automation.plugin.BlackDuckPluginManager
import com.synopsys.integration.blackduck.artifactory.automation.plugin.BlackDuckPluginService
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfig
import com.synopsys.integration.blackduck.configuration.BlackDuckServerConfigBuilder
import com.synopsys.integration.blackduck.service.BlackDuckServicesFactory
import com.synopsys.integration.log.Slf4jIntLogger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.ConfigurableEnvironment
import java.io.File
import java.time.Duration

@SpringBootConfiguration
class ApplicationConfiguration {
    private val logger = Slf4jIntLogger(LoggerFactory.getLogger(this::class.java))

    @Bean
    fun configManager(@Autowired environment: ConfigurableEnvironment): ConfigManager {
        return ConfigManager(environment)
    }

    @Bean
    fun blackDuckServerConfig(@Autowired configManager: ConfigManager): BlackDuckServerConfig {
        logger.info("Verifying Black Duck server config.")
        return BlackDuckServerConfigBuilder()
                .setUrl(configManager.getRequired(ConfigProperty.BLACKDUCK_URL))
                .setUsername(configManager.getRequired(ConfigProperty.BLACKDUCK_USERNAME))
                .setPassword(configManager.getRequired(ConfigProperty.BLACKDUCK_PASSWORD))
                .setTrustCert(configManager.getRequired(ConfigProperty.BLACKDUCK_TRUST_CERT))
                .build()
    }

    @Bean
    fun artifactoryConfiguration(@Autowired configManager: ConfigManager): ArtifactoryConfiguration {
        val artifactoryBaseUrl = configManager.getRequired(ConfigProperty.ARTIFACTORY_BASEURL)
        val artifactoryPort = configManager.getRequired(ConfigProperty.ARTIFACTORY_PORT)
        val artifactoryUrl = "$artifactoryBaseUrl:$artifactoryPort/artifactory"
        val artifactoryUsername = configManager.getRequired(ConfigProperty.ARTIFACTORY_USERNAME)
        val artifactoryPassword = configManager.getRequired(ConfigProperty.ARTIFACTORY_PASSWORD)
        val artifactoryVersion = configManager.getRequired(ConfigProperty.ARTIFACTORY_VERSION)
        val manageArtifactory = configManager.getRequired(ConfigProperty.MANAGE_ARTIFACTORY).toBoolean()
        val licenseFile = File(configManager.getRequired(ConfigProperty.ARTIFACTORY_LICENSE_PATH))
        val pluginZipFile = File(configManager.getRequired(ConfigProperty.PLUGIN_ZIP_PATH))
        val pluginLoggingLevel = configManager.getRequired(ConfigProperty.PLUGIN_LOGGING_LEVEL)
        val configImportDirectory = configManager.getRequired(ConfigProperty.CONFIG_IMPORT_DIRECTORY)

        return ArtifactoryConfiguration(artifactoryUrl, artifactoryPort, artifactoryUsername, artifactoryPassword, artifactoryVersion, manageArtifactory, licenseFile, pluginZipFile, pluginLoggingLevel, configImportDirectory)
    }

    @Bean
    fun dockerService(@Autowired artifactoryConfiguration: ArtifactoryConfiguration): DockerService {
        val imageTag = "artifactory-automation-${artifactoryConfiguration.version}"
        return DockerService(imageTag)
    }

    @Bean
    fun blackDuckPluginService(@Autowired dockerService: DockerService): BlackDuckPluginService {
        return BlackDuckPluginService(dockerService)
    }

    @Bean
    fun fuelManager(@Autowired artifactoryConfiguration: ArtifactoryConfiguration): FuelManager {
        val fuelManager = FuelManager()

        // It is helpful to have a longer timeout for debugging.
        fuelManager.timeoutInMillisecond = Duration.ofMinutes(1).toMillis().toInt()
        fuelManager.timeoutReadInMillisecond = fuelManager.timeoutInMillisecond

        fuelManager.basePath = artifactoryConfiguration.url
        fuelManager.addRequestInterceptor {
            {
                logger.info("Making ${it.method} request to ${it.url}")
                it.authentication().basic(artifactoryConfiguration.username, artifactoryConfiguration.password)
            }
        }

        return fuelManager
    }

    @Bean
    fun systemApiService(@Autowired fuelManager: FuelManager): SystemApiService {
        return SystemApiService(fuelManager)
    }

    @Bean
    fun blackDuckPluginApiService(@Autowired fuelManager: FuelManager): BlackDuckPluginApiService {
        return BlackDuckPluginApiService(fuelManager)
    }

    @Bean
    fun repositoriesApiService(@Autowired fuelManager: FuelManager): RepositoriesApiService {
        return RepositoriesApiService(fuelManager)
    }

    @Bean
    fun propertiesApiService(@Autowired fuelManager: FuelManager): PropertiesApiService {
        return PropertiesApiService(fuelManager)
    }

    @Bean
    fun pluginsApiService(@Autowired fuelManager: FuelManager): PluginsApiService {
        return PluginsApiService(fuelManager)
    }

    @Bean
    fun artifactDeploymentApiService(@Autowired fuelManager: FuelManager): ArtifactDeploymentApiService {
        return ArtifactDeploymentApiService(fuelManager)
    }

    @Bean
    fun artifactRetrievalApiService(@Autowired fuelManager: FuelManager): ArtifactRetrievalApiService {
        return ArtifactRetrievalApiService(fuelManager)
    }

    @Bean
    fun artifactsSearchesAPIService(@Autowired fuelManager: FuelManager): ArtifactSearchesAPIService {
        return ArtifactSearchesAPIService(fuelManager)
    }

    @Bean
    fun importExportAPIService(@Autowired fuelManager: FuelManager): ImportExportApiService {
        return ImportExportApiService(fuelManager)
    }

    @Bean
    fun artifactoryConfigurationService(@Autowired artifactoryConfiguration: ArtifactoryConfiguration, @Autowired importExportApiService: ImportExportApiService, @Autowired dockerService: DockerService): ArtifactoryConfigurationService {
        return ArtifactoryConfigurationService(artifactoryConfiguration, importExportApiService, dockerService)
    }

    @Bean
    fun blackDuckServiceFactory(@Autowired blackDuckServerConfig: BlackDuckServerConfig): BlackDuckServicesFactory {
        val logger = Slf4jIntLogger(LoggerFactory.getLogger("BlackDuckServicesFactory"))
        return blackDuckServerConfig.createBlackDuckServicesFactory(logger)
    }

    @Bean
    fun repositoryManager(@Autowired repositoriesApiService: RepositoriesApiService, @Autowired blackDuckPluginManager: BlackDuckPluginManager): RepositoryManager {
        return RepositoryManager(repositoriesApiService, blackDuckPluginManager)
    }

    @Bean
    fun artifactResolver(@Autowired artifactRetrievalApiService: ArtifactRetrievalApiService, @Autowired dockerService: DockerService, @Autowired artifactoryConfiguration: ArtifactoryConfiguration): ArtifactResolver {
        return ArtifactResolver(artifactRetrievalApiService, dockerService, artifactoryConfiguration)
    }

    @Bean
    fun blackDuckVerificationService(@Autowired blackDuckServicesFactory: BlackDuckServicesFactory, @Autowired propertiesApiService: PropertiesApiService, @Autowired artifactSearchesAPIService: ArtifactSearchesAPIService): ComponentVerificationService {
        return ComponentVerificationService(blackDuckServicesFactory, propertiesApiService, artifactSearchesAPIService)
    }

    @Bean
    fun blackDuckPluginManager(
            @Autowired artifactoryConfiguration: ArtifactoryConfiguration,
            @Autowired blackDuckServerConfig: BlackDuckServerConfig,
            @Autowired blackDuckPluginService: BlackDuckPluginService,
            @Autowired blackDuckPluginApiService: BlackDuckPluginApiService,
            @Autowired dockerService: DockerService
    ): BlackDuckPluginManager {
        return BlackDuckPluginManager(
                artifactoryConfiguration,
                blackDuckServerConfig,
                blackDuckPluginService,
                blackDuckPluginApiService,
                dockerService
        )
    }

    @Bean
    fun application(
            @Autowired configManager: ConfigManager,
            @Autowired dockerService: DockerService,
            @Autowired blackDuckServerConfig: BlackDuckServerConfig,
            @Autowired artifactoryConfiguration: ArtifactoryConfiguration,
            @Autowired blackDuckPluginManager: BlackDuckPluginManager,
            @Autowired systemApiService: SystemApiService
    ): Application {
        return Application(
                dockerService,
                blackDuckServerConfig,
                artifactoryConfiguration,
                blackDuckPluginManager,
                systemApiService
        )
    }
}

data class ArtifactoryConfiguration(
        val url: String,
        val port: String,
        val username: String,
        val password: String,
        val version: String,
        val manageArtifactory: Boolean,
        val licenseFile: File,
        val pluginZipFile: File,
        val pluginLoggingLevel: String,
        val configImportDirectory: String
)