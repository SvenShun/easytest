package com.testhub.core.feature.internal;

import com.testhub.common.security.PublicApi;
import com.testhub.core.feature.script.ScriptProject;
import com.testhub.core.feature.script.ScriptService;
import com.testhub.core.feature.script.ScriptTicketService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket-authenticated script bundle download. Deliberately {@link PublicApi}:
 * device agents have no JWT and no service token, the signed ticket is the
 * credential (15 minute lifetime).
 */
@RestController
@RequestMapping("/internal/v1/scripts")
public class InternalScriptController {

    private final ScriptService scriptService;
    private final ScriptTicketService ticketService;

    public InternalScriptController(ScriptService scriptService, ScriptTicketService ticketService) {
        this.scriptService = scriptService;
        this.ticketService = ticketService;
    }

    @PublicApi
    @GetMapping("/{scriptId}/bundle")
    public ResponseEntity<byte[]> bundle(@PathVariable String scriptId,
                                         @RequestParam(value = "ticket", required = false) String ticket) {
        ticketService.verify(scriptId, ticket);
        ScriptProject script = scriptService.require(scriptId);
        byte[] content = scriptService.readBundle(scriptId);
        String filename = script.getName() == null ? scriptId + ".zip" : script.getName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentLength(content.length)
                .body(content);
    }
}
