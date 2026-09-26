package com.cb.auditagent.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class DocRouter {

    @Bean
    public RouterFunction<ServerResponse> docRoute(DocHandler docHandler) {
        return RouterFunctions.route(GET("/api/docs"), docHandler::listDocs)
                .andRoute(GET("/api/docs/{filename}"), docHandler::getDoc);
    }
}
