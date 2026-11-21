package com.aierp.erp.controller.ai;
import com.aierp.common.AjaxResult;import com.aierp.erp.trace.AiTraceService;import com.aierp.security.common.BaseController;import lombok.RequiredArgsConstructor;import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/ai/trace") public class AiTraceController extends BaseController {
 private final AiTraceService service;
 @GetMapping public AjaxResult list(@RequestParam(required=false) String sessionId){return AjaxResult.success(service.list(getUserId(),sessionId));}
 @GetMapping("/{id}") public AjaxResult detail(@PathVariable String id){return AjaxResult.success(service.run(getUserId(),id));}
 @ExceptionHandler(IllegalArgumentException.class) public AjaxResult invalid(IllegalArgumentException ex){return AjaxResult.error(ex.getMessage());}
}
