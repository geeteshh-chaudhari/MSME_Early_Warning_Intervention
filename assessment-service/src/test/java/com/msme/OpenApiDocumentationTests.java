package com.msme;
import java.util.List;
import com.msme.config.OpenApiConfig;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenApiDocumentationTests {
    @Test void loginIsPublicAndBusinessOperationsKeepBearerSecurity() {
        var config = new OpenApiConfig();
        var api = config.serviceOpenApi("test-service");
        var login = new Operation().operationId("login");
        var create = new Operation().operationId("createMsme")
            .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
        api.setPaths(new Paths()
            .addPathItem("/api/auth/login", new PathItem().post(login))
            .addPathItem("/api/msmes", new PathItem().post(create)));
        config.documentOperations().customise(api);
        assertEquals("/", api.getServers().get(0).getUrl());
        assertEquals("bearer", api.getComponents().getSecuritySchemes().get("bearerAuth").getScheme());
        assertTrue(api.getSecurity().get(0).containsKey("bearerAuth"));
        assertTrue(login.getSecurity().isEmpty());
        assertNull(create.getSecurity()); // inherits the global requirement
        assertEquals(List.of("RM"), create.getExtensions().get("x-roles"));
        assertTrue(create.getResponses().containsKey("201"));
        assertEquals("Create Msme", create.getSummary());
    }
}
