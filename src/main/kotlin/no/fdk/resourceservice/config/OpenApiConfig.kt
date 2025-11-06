package no.fdk.resourceservice.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Resource Service API")
                    .description(
                        """
                        A comprehensive REST API providing programmatic access to Norway's national data catalog, enabling discovery and reuse of public sector data resources.
                        
                        ## About Data.norge.no
                        This API provides access to the same comprehensive data catalog published on [data.norge.no](https://data.norge.no), operated by the Norwegian Digitalisation Agency (Digitaliseringsdirektoratet). The catalog serves as Norway's central registry for public sector data resources, promoting fair competition and enabling data reuse for both commercial and non-commercial purposes.
                        
                        ## What You'll Find
                        The catalog provides an extensive overview of resources from the Norwegian public sector, including:
                        - **Datasets**: Structured data collections with detailed metadata descriptions
                        - **Concepts**: Controlled vocabularies, taxonomies, and standardized terms used across government
                        - **APIs**: Data services and application programming interfaces with technical specifications
                        - **Information Models**: Data schemas, specifications, and interoperability standards
                        - **Services & Events**: Service descriptions and event information from public sector activities
                                                
                        Each resource includes clear information about data providers, availability, data quality, and relationships between different resources.
                        
                        ## API Capabilities
                        - **JSON Access**: Retrieve resources in JSON format for easy application integration
                        - **RDF Support**: Access resources in multiple semantic web formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)
                        - **Content Negotiation**: Automatic format selection based on Accept headers
                        - **Flexible Querying**: Find resources by unique identifier or URI
                        - **Graph Representations**: Access semantic graph data with configurable formatting (pretty/standard)
                        - **Real-time Access**: Direct programmatic access to the same data available on data.norge.no
                        
                        ## Use Cases
                        - **Data Discovery**: Programmatically search and discover available public sector data
                        - **Application Integration**: Integrate data catalog information into applications and systems
                        - **Semantic Web Projects**: Use RDF endpoints for linked data and semantic web applications
                        - **Metadata Analysis**: Access structured metadata for data governance and quality assessment
                        - **Data Reuse**: Enable fair competition by providing equal access to data resources
                        - **Interoperability**: Use standardized information models and concepts for system integration
                        
                        ## Data Governance
                        Content is provided by the organizations themselves, with each organization responsible for managing their content in the catalogs. The Norwegian Digitalisation Agency is responsible for the operation and development of the platform.
                        
                        For more information about finding and using data, visit [data.norge.no](https://data.norge.no/nb/docs/finding-data) or learn more [about the platform](https://data.norge.no/nb/about).
                        """.trimIndent(),
                    ).version("1.0.0"),
            )
}
