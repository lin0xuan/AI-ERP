package com.aierp.erp.operations;

import com.aierp.common.AjaxResult;
import com.aierp.security.common.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/erp-api/operations")
public class OperationsController {
    private final OperationsService service;
    public static OperationsService.Actor actor() {
        var u=SecurityUtils.getLoginUser();
        return new OperationsService.Actor(u.getUserId(),SecurityUtils.isAdmin(u.getUserId()) || u.getPermissions()!=null && u.getPermissions().contains("*:*:*"),u.getPermissions()==null?Set.of():u.getPermissions());
    }
    @GetMapping("/inventory") public AjaxResult inventory(@RequestParam(defaultValue="1") int page) {return AjaxResult.success(service.inventories(actor(),page));}
    @GetMapping("/ledger/{id}") public AjaxResult ledger(@PathVariable long id,@RequestParam(defaultValue="1") int page) {return AjaxResult.success(service.ledger(actor(),id,page));}
    @GetMapping("/documents") public AjaxResult documents(@RequestParam(defaultValue="1") int page) {return AjaxResult.success(service.documents(actor(),page));}
    @GetMapping("/documents/{id}") public AjaxResult document(@PathVariable String id) {return AjaxResult.success(service.detail(actor(),id));}
    @PostMapping("/documents") public AjaxResult create(@RequestBody OperationsService.Draft draft) {return AjaxResult.success(service.create(actor(),draft));}
    @PostMapping("/documents/{id}/actions") public AjaxResult act(@PathVariable String id,@RequestBody OperationsService.Action action) {return AjaxResult.success(service.act(actor(),id,action));}
    @GetMapping("/policies") public AjaxResult policies() {return AjaxResult.success(service.policies(actor()));}
    @PutMapping("/policies") public AjaxResult policy(@RequestBody OperationsService.Policy body) {service.savePolicy(actor(),body);return AjaxResult.success();}
    @GetMapping("/grants") public AjaxResult grants() {return AjaxResult.success(service.grants(actor()));}
    @PutMapping("/grants") public AjaxResult grant(@RequestBody OperationsService.Grant body) {service.grant(actor(),body);return AjaxResult.success();}
    @GetMapping("/fields") public AjaxResult fields(@RequestParam String kind) {return AjaxResult.success(service.fields(actor(),kind));}
    @PutMapping("/fields") public AjaxResult field(@RequestBody OperationsService.Field body) {service.saveField(actor(),body);return AjaxResult.success();}
    @GetMapping("/replenishment") public AjaxResult replenishment(@RequestParam(defaultValue="1") int page) {return AjaxResult.success(service.replenishment(actor(),page));}
    @PutMapping("/replenishment") public AjaxResult reorder(@RequestBody OperationsService.Reorder body) {service.saveReorder(actor(),body);return AjaxResult.success();}
    @PostMapping("/replenishment/{id}") public AjaxResult replenish(@PathVariable long id,@RequestBody Map<String,String> body) {return AjaxResult.success(service.replenish(actor(),id,body.get("requestKey")));}
    @GetMapping("/audit") public AjaxResult audit(@RequestParam(defaultValue="1") int page) {return AjaxResult.success(service.configurationAudit(actor(),page));}
    @GetMapping("/master") public AjaxResult master(@RequestParam String kind,@RequestParam long recordId) {return AjaxResult.success(service.master(actor(),kind,recordId));}
    @PutMapping("/master") public AjaxResult master(@RequestBody OperationsService.Master body) {service.saveMaster(actor(),body);return AjaxResult.success();}
    @ExceptionHandler(IllegalArgumentException.class) public AjaxResult invalid(IllegalArgumentException ex) {return AjaxResult.error(ex.getMessage());}
    @ExceptionHandler(AccessDeniedException.class) public AjaxResult denied(AccessDeniedException ex) {return AjaxResult.error(ex.getMessage());}
}
