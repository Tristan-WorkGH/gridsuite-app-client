import org.junit.jupiter.api.Test;
import org.springdoc.core.utils.Constants;
import org.springdoc.webflux.api.OpenApiWebfluxResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

//@DisableElasticsearch @DisableCloudStream @DisableJpa ==> @MockBean(...)
// @TestPropertySource(properties = {"test.disable.data-jpa=true", "test.disable.cloud-stream=true", "test.disable.data-elasticsearch=true"})
@TestPropertySource(locations = "file:../springdoc-config.properties")
@SpringBootTest
public class SwaggerDumpTest {
    @Test
    void dumpOpenApi(@Autowired final OpenApiWebfluxResource openApiResource) throws IOException {
        final Path openapiFile = Paths.get(".", "target", "openapi.json");
        Files.deleteIfExists(openapiFile);
        Files.write(openapiFile, openApiResource.openapiJson(MockServerHttpRequest.get(Constants.DEFAULT_API_DOCS_URL).build(), Constants.DEFAULT_API_DOCS_URL, Locale.ENGLISH).block());
    }
}
