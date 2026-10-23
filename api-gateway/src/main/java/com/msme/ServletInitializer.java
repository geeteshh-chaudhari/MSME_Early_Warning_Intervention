package com.msme;

import org.springframework.boot.builder.SpringApplicationBuilder;


/** Legacy builder helper. The reactive gateway runs through ApiGatewayApplication, not a servlet container. */
public class ServletInitializer {

	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(ApiGatewayApplication.class);
	}

}
