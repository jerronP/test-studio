package com.recorder.controller;

import com.recorder.model.AIAgentRequest;
import com.recorder.service.AIAgentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/ai-agent")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AIAgentController {

    @Inject
    AIAgentService aiAgentService;

    @POST
    @Path("/execute")
    public Response executePrompt(AIAgentRequest request) {
        return aiAgentService.processPrompt(request);
    }
}
